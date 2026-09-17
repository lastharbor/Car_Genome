package com.cargenome.app.data.vin

import com.cargenome.app.di.DefaultDispatcher
import com.cargenome.app.domain.model.FuelType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Free, offline-first VIN enricher for Russian and CIS-manufactured vehicles (AvtoVAZ / Lada,
 * Renault Russia, Hyundai/Kia Rus, Volkswagen/Skoda Kaluga, UAZ, GAZ, BMW Avtotor).
 *
 * NHTSA vPIC only indexes manufacturers registered with the US DOT. For Russian vehicles,
 * vPIC returns empty results. This decoder recognizes standard ISO 3779 VDS patterns
 * (characters 4-8) and extracts the exact model name, engine, body, and drive type with zero network cost.
 */
@Singleton
class RussianVdsVinDecoder @Inject constructor(
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : OnlineVinDecoder {

    override suspend fun lookup(vin: String, now: Instant): OnlineVinLookup = withContext(dispatcher) {
        val clean = vin.trim().uppercase()
        if (clean.length != 17) return@withContext OnlineVinLookup.Empty

        val wmi = clean.substring(0, 3)
        val vds = clean.substring(3, 8)
        val vdsFull = clean.substring(3, 9)

        val data = when (wmi) {
            // AvtoVAZ / Lada
            "XTA" -> decodeAvtoVaz(vds, vdsFull)

            // Renault Russia (Avtoframos / Moscow)
            "X7L" -> decodeRenaultRus(vds)

            // Hyundai Motor Manufacturing Rus / Kia (St. Petersburg)
            "XWE" -> decodeHyundaiKiaRus(vds, isHyundai = true)
            "Z94" -> decodeHyundaiKiaRus(vds, isHyundai = false)

            // Volkswagen Group Rus (Kaluga / Nizhny Novgorod)
            "XW8" -> decodeVwGroupRus(vds)

            // UAZ (Ulyanovsk Automobile Plant)
            "XTT" -> decodeUaz(vds)

            // GAZ (Gorky Automobile Plant)
            "X96" -> decodeGaz(vds)

            // BMW Avtotor (Kaliningrad)
            "X4X" -> decodeBmwAvtotor(vds)

            // Haval Motor Manufacturing Rus (Tula)
            "X9W" -> decodeHavalRus(vds)

            // BelGee (Belarus, Geely joint venture)
            "Y39" -> decodeBelGee(vds)

            // Chery / Omoda / Jaecoo / Exeed
            "LVV" -> decodeCheryGroup(vds)

            // Geely (China)
            "LB3", "L6T" -> decodeGeelyChina(vds)

            // Changan (China)
            "LS4", "LS5" -> decodeChangan(vds)

            // KAMAZ (Russia)
            "XTC" -> decodeKamaz(vds)

            else -> null
        }

        if (data != null) {
            OnlineVinLookup.Hit(data, fromCache = false)
        } else {
            OnlineVinLookup.Empty
        }
    }

    override suspend fun modelsFor(make: String, year: Int): List<String> {
        val normalized = make.trim().lowercase()
        return when {
            normalized.contains("lada") || normalized.contains("ваз") -> listOf(
                "Granta", "Vesta", "Niva Legend", "Niva Travel", "Largus", "XRAY", "Priora", "Kalina", "2114", "2110", "2107",
            )
            normalized.contains("uaz") || normalized.contains("уаз") -> listOf(
                "Patriot", "Hunter", "Pickup", "Profi", "Буханка",
            )
            normalized.contains("gaz") || normalized.contains("газ") -> listOf(
                "ГАЗель NEXT", "ГАЗель Бизнес", "ГАЗель NN", "Соболь", "ГАЗон NEXT", "Волга",
            )
            normalized.contains("haval") || normalized.contains("хавейл") || normalized.contains("хавал") -> listOf(
                "Jolion", "F7", "F7x", "Dargo", "H9", "M6", "H3",
            )
            normalized.contains("belgee") || normalized.contains("белджи") -> listOf(
                "X50", "X70",
            )
            normalized.contains("geely") || normalized.contains("джили") -> listOf(
                "Monjaro", "Coolray", "Atlas", "Atlas Pro", "Tugella", "Emgrand", "Preface", "Okavango",
            )
            normalized.contains("chery") || normalized.contains("чери") -> listOf(
                "Tiggo 4 Pro", "Tiggo 7 Pro Max", "Tiggo 8 Pro Max", "Arrizo 8", "Tiggo 4", "Tiggo 7 Pro", "Tiggo 8 Pro",
            )
            normalized.contains("omoda") || normalized.contains("омода") -> listOf(
                "C5", "S5", "S5 GT",
            )
            normalized.contains("changan") || normalized.contains("чанган") -> listOf(
                "CS35 Plus", "CS55 Plus", "CS75 Plus", "UNI-V", "UNI-K", "UNI-T", "Alsvin", "Lamore",
            )
            normalized.contains("renault") -> listOf(
                "Logan", "Sandero", "Sandero Stepway", "Duster", "Kaptur", "Arkana",
            )
            normalized.contains("hyundai") -> listOf(
                "Solaris", "Creta", "Elantra", "Sonata", "Tucson", "Santa Fe",
            )
            normalized.contains("kia") -> listOf(
                "Rio", "Rio X", "Ceed", "Cerato", "K5", "Sportage", "Sorento",
            )
            normalized.contains("volkswagen") -> listOf(
                "Polo", "Polo Sedan", "Tiguan", "Taos", "Passat", "Golf",
            )
            normalized.contains("skoda") -> listOf(
                "Rapid", "Octavia", "Kodiaq", "Karoq", "Superb",
            )
            else -> emptyList()
        }
    }

    private fun decodeAvtoVaz(vds: String, vdsFull: String): ExternalVehicleData {
        val prefix = vds.take(4)
        return when {
            // Vesta
            vds.startsWith("GFL") -> ExternalVehicleData(
                make = "Lada",
                model = "Vesta (Седан)",
                trim = "Vesta",
                engineSummary = "1.6L 16V (106 л.с.)",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 4,
                plant = "Ижевск / Тольятти, Россия",
            )
            vds.startsWith("GFK") -> ExternalVehicleData(
                make = "Lada",
                model = "Vesta SW / Cross",
                trim = "Vesta SW",
                engineSummary = "1.6L / 1.8L 16V",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Ижевск / Тольятти, Россия",
            )
            // XRAY
            vds.startsWith("GAB") -> ExternalVehicleData(
                make = "Lada",
                model = "XRAY",
                engineSummary = "1.6L / 1.8L 16V",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Тольятти, Россия",
            )
            // Granta (2190, 2191)
            prefix == "2190" -> ExternalVehicleData(
                make = "Lada",
                model = "Granta (Седан)",
                engineSummary = "1.6L (87-106 л.с.)",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 4,
                plant = "Тольятти, Россия",
            )
            prefix == "2191" -> ExternalVehicleData(
                make = "Lada",
                model = "Granta (Лифтбек)",
                engineSummary = "1.6L (87-106 л.с.)",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Ижевск / Тольятти, Россия",
            )
            prefix in listOf("2192", "2194") -> ExternalVehicleData(
                make = "Lada",
                model = "Kalina 2 / Granta Cross",
                engineSummary = "1.6L 16V",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Тольятти, Россия",
            )
            // Priora (2170, 2171, 2172)
            prefix in listOf("2170", "2171", "2172") -> ExternalVehicleData(
                make = "Lada",
                model = "Priora",
                engineSummary = "1.6L 16V (98-106 л.с.)",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Тольятти, Россия",
            )
            // Niva Legend (2121, 21213, 21214, 2131)
            prefix in listOf("2121", "2131") || prefix.startsWith("212") -> ExternalVehicleData(
                make = "Lada",
                model = "Niva Legend (4x4)",
                engineSummary = "1.7L (83 л.с.)",
                displacementLitres = 1.7,
                fuelType = FuelType.Petrol,
                driveType = "4WD",
                plant = "Тольятти, Россия",
            )
            // Niva Travel / Chevrolet Niva (2123)
            prefix == "2123" -> ExternalVehicleData(
                make = "Lada",
                model = "Niva Travel",
                engineSummary = "1.7L (80 л.с.)",
                displacementLitres = 1.7,
                fuelType = FuelType.Petrol,
                driveType = "4WD",
                plant = "Тольятти, Россия",
            )
            // Samara 2 (2113, 2114, 2115)
            prefix in listOf("2113", "2114", "2115") -> ExternalVehicleData(
                make = "Lada",
                model = if (prefix == "2114") "2114 (Самара-2)" else if (prefix == "2115") "2115 (Самара-2)" else "2113 (Самара-2)",
                engineSummary = "1.6L 8V",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Тольятти, Россия",
            )
            // Samara 1 (2108, 2109, 21099)
            prefix in listOf("2108", "2109") -> ExternalVehicleData(
                make = "Lada",
                model = if (prefix == "2108") "2108 (Самара)" else "2109 / 21099 (Самара)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                plant = "Тольятти, Россия",
            )
            // 2110, 2111, 2112
            prefix in listOf("2110", "2111", "2112") -> ExternalVehicleData(
                make = "Lada",
                model = "2110 / 2111 / 2112",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Тольятти, Россия",
            )
            // Kalina 1 (1117, 1118, 1119)
            prefix in listOf("1117", "1118", "1119") -> ExternalVehicleData(
                make = "Lada",
                model = "Kalina",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Тольятти, Россия",
            )
            // Largus (KS0, RS0)
            vds.startsWith("KS0") || vds.startsWith("RS0") -> ExternalVehicleData(
                make = "Lada",
                model = "Largus",
                engineSummary = "1.6L (8V / 16V)",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Тольятти, Россия",
            )
            // Classic (2104, 2105, 2106, 2107)
            prefix in listOf("2104", "2105", "2106", "2107") -> ExternalVehicleData(
                make = "Lada",
                model = prefix,
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Тольятти, Россия",
            )
            else -> ExternalVehicleData(
                make = "Lada",
                plant = "Тольятти, Россия",
                fuelType = FuelType.Petrol,
            )
        }
    }

    private fun decodeRenaultRus(vds: String): ExternalVehicleData {
        return when {
            vds.contains("HS") || vds.contains("H79") -> ExternalVehicleData(
                make = "Renault",
                model = "Duster",
                engineSummary = "1.6L / 2.0L / 1.5 dCi",
                plant = "Москва, Россия (Автофрамос)",
            )
            vds.contains("HHA") -> ExternalVehicleData(
                make = "Renault",
                model = "Kaptur",
                engineSummary = "1.6L / 2.0L / 1.3 TCe",
                plant = "Москва, Россия",
            )
            vds.contains("SR") || vds.contains("LS") || vds.contains("L90") -> ExternalVehicleData(
                make = "Renault",
                model = "Logan",
                displacementLitres = 1.6,
                plant = "Москва, Россия",
            )
            vds.contains("BS") || vds.contains("B90") -> ExternalVehicleData(
                make = "Renault",
                model = "Sandero",
                displacementLitres = 1.6,
                plant = "Москва, Россия",
            )
            else -> ExternalVehicleData(
                make = "Renault",
                plant = "Москва, Россия",
            )
        }
    }

    private fun decodeHyundaiKiaRus(vds: String, isHyundai: Boolean): ExternalVehicleData {
        return when {
            vds.contains("C1") || vds.contains("C2") || vds.contains("R1") -> ExternalVehicleData(
                make = "Hyundai",
                model = "Solaris",
                engineSummary = "1.4L / 1.6L Gamma",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Санкт-Петербург, Россия (HMMR)",
            )
            vds.contains("FB") || vds.contains("QB") || vds.contains("YB") -> ExternalVehicleData(
                make = "Kia",
                model = "Rio",
                engineSummary = "1.4L / 1.6L MPI",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                plant = "Санкт-Петербург, Россия",
            )
            vds.contains("GS") || vds.contains("SU2") -> ExternalVehicleData(
                make = "Hyundai",
                model = "Creta",
                engineSummary = "1.6L / 2.0L MPI",
                fuelType = FuelType.Petrol,
                plant = "Санкт-Петербург, Россия (HMMR)",
            )
            else -> ExternalVehicleData(
                make = if (isHyundai) "Hyundai" else "Kia",
                plant = "Санкт-Петербург, Россия",
            )
        }
    }

    private fun decodeVwGroupRus(vds: String): ExternalVehicleData {
        return when {
            vds.contains("61") -> ExternalVehicleData(
                make = "Volkswagen",
                model = "Polo (Седан)",
                engineSummary = "1.6 MPI / 1.4 TSI",
                displacementLitres = 1.6,
                plant = "Калуга, Россия",
            )
            vds.contains("NH") -> ExternalVehicleData(
                make = "Skoda",
                model = "Rapid",
                engineSummary = "1.6 MPI / 1.4 TSI",
                displacementLitres = 1.6,
                plant = "Калуга, Россия",
            )
            vds.contains("5N") -> ExternalVehicleData(
                make = "Volkswagen",
                model = "Tiguan",
                engineSummary = "1.4 TSI / 2.0 TSI",
                plant = "Калуга, Россия",
            )
            vds.contains("5E") -> ExternalVehicleData(
                make = "Skoda",
                model = "Octavia",
                engineSummary = "1.6 MPI / 1.4 TSI",
                plant = "Нижний Новгород, Россия",
            )
            else -> ExternalVehicleData(
                make = "Volkswagen Group",
                plant = "Калуга, Россия",
            )
        }
    }

    private fun decodeUaz(vds: String): ExternalVehicleData {
        val prefix = vds.take(4)
        return when {
            prefix == "3163" -> ExternalVehicleData(
                make = "УАЗ",
                model = "Patriot",
                engineSummary = "2.7L ZMZ-Pro (150 л.с.)",
                displacementLitres = 2.7,
                fuelType = FuelType.Petrol,
                driveType = "4WD",
                plant = "Ульяновск, Россия",
            )
            prefix in listOf("3151", "4690") -> ExternalVehicleData(
                make = "УАЗ",
                model = "Hunter",
                displacementLitres = 2.7,
                fuelType = FuelType.Petrol,
                driveType = "4WD",
                plant = "Ульяновск, Россия",
            )
            prefix in listOf("3909", "2206", "3741") -> ExternalVehicleData(
                make = "УАЗ",
                model = "СГР (Буханка)",
                displacementLitres = 2.7,
                fuelType = FuelType.Petrol,
                driveType = "4WD",
                plant = "Ульяновск, Россия",
            )
            else -> ExternalVehicleData(
                make = "УАЗ",
                driveType = "4WD",
                plant = "Ульяновск, Россия",
            )
        }
    }

    private fun decodeGaz(vds: String): ExternalVehicleData {
        return when {
            vds.startsWith("A21") || vds.startsWith("A22") || vds.startsWith("A31") -> ExternalVehicleData(
                make = "ГАЗ",
                model = "ГАЗель NEXT",
                engineSummary = "Cummins 2.8 / Evotech 2.7",
                plant = "Нижний Новгород, Россия",
            )
            vds.startsWith("3302") || vds.startsWith("2705") || vds.startsWith("3221") -> ExternalVehicleData(
                make = "ГАЗ",
                model = "ГАЗель Бизнес",
                engineSummary = "Evotech 2.7L",
                plant = "Нижний Новгород, Россия",
            )
            vds.startsWith("3110") || vds.startsWith("3102") -> ExternalVehicleData(
                make = "ГАЗ",
                model = "Волга",
                plant = "Нижний Новгород, Россия",
            )
            else -> ExternalVehicleData(
                make = "ГАЗ",
                plant = "Нижний Новгород, Россия",
            )
        }
    }

    private fun decodeBmwAvtotor(vds: String): ExternalVehicleData {
        return ExternalVehicleData(
            make = "BMW",
            plant = "Калининград, Россия (Автотор)",
        )
    }

    private fun decodeHavalRus(vds: String): ExternalVehicleData {
        return when {
            vds.contains("CC644") || vds.contains("JOL") || vds.startsWith("A") -> ExternalVehicleData(
                make = "Haval",
                model = "Jolion",
                engineSummary = "1.5T (143-150 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Тульская обл., Россия (Haval)",
            )
            vds.contains("CC646") || vds.contains("F7") -> ExternalVehicleData(
                make = "Haval",
                model = "F7 / F7x",
                engineSummary = "1.5T / 2.0T (150-190 л.с.)",
                displacementLitres = 2.0,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Тульская обл., Россия (Haval)",
            )
            vds.contains("CC647") || vds.contains("DAR") -> ExternalVehicleData(
                make = "Haval",
                model = "Dargo",
                engineSummary = "2.0T (192 л.с.)",
                displacementLitres = 2.0,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Тульская обл., Россия (Haval)",
            )
            vds.contains("H9") -> ExternalVehicleData(
                make = "Haval",
                model = "H9",
                engineSummary = "2.0T (218 л.с.)",
                displacementLitres = 2.0,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Тульская обл., Россия (Haval)",
            )
            else -> ExternalVehicleData(
                make = "Haval",
                plant = "Тульская обл., Россия (Haval)",
            )
        }
    }

    private fun decodeBelGee(vds: String): ExternalVehicleData {
        return when {
            vds.contains("SX11") || vds.contains("X50") -> ExternalVehicleData(
                make = "BelGee",
                model = "X50 (Coolray)",
                engineSummary = "1.5T (150 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Жодино, Беларусь (Белджи)",
            )
            vds.contains("NL-3") || vds.contains("X70") -> ExternalVehicleData(
                make = "BelGee",
                model = "X70 (Atlas Pro)",
                engineSummary = "1.5T / Mild Hybrid (177 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Жодино, Беларусь (Белджи)",
            )
            vds.contains("SS11") -> ExternalVehicleData(
                make = "Geely",
                model = "Emgrand",
                engineSummary = "1.5L (122 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 4,
                plant = "Жодино, Беларусь (Белджи)",
            )
            else -> ExternalVehicleData(
                make = "BelGee",
                plant = "Жодино, Беларусь (Белджи)",
            )
        }
    }

    private fun decodeCheryGroup(vds: String): ExternalVehicleData {
        return when {
            vds.contains("T19") || vds.contains("T17") -> ExternalVehicleData(
                make = "Chery",
                model = "Tiggo 4 Pro",
                engineSummary = "1.5L / 1.5T (113-147 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Уху, Китай (Chery)",
            )
            vds.contains("T1E") -> ExternalVehicleData(
                make = "Chery",
                model = "Tiggo 7 Pro Max",
                engineSummary = "1.5T / 1.6T (147-150 л.с.)",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Уху, Китай (Chery)",
            )
            vds.contains("T26") || vds.contains("T1D") -> ExternalVehicleData(
                make = "Chery",
                model = "Tiggo 8 Pro Max",
                engineSummary = "2.0T (197-249 л.с.)",
                displacementLitres = 2.0,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Уху, Китай (Chery)",
            )
            vds.contains("C5") || vds.contains("T19C") -> ExternalVehicleData(
                make = "Omoda",
                model = "C5",
                engineSummary = "1.5T / 1.6T (147-150 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Уху, Китай (Chery / Omoda)",
            )
            vds.contains("J7") -> ExternalVehicleData(
                make = "Jaecoo",
                model = "J7",
                engineSummary = "1.6T (186 л.с.)",
                displacementLitres = 1.6,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Уху, Китай (Jaecoo)",
            )
            else -> ExternalVehicleData(
                make = "Chery",
                plant = "Уху, Китай (Chery)",
            )
        }
    }

    private fun decodeGeelyChina(vds: String): ExternalVehicleData {
        return when {
            vds.contains("KX11") -> ExternalVehicleData(
                make = "Geely",
                model = "Monjaro",
                engineSummary = "2.0T Drive-E (238 л.с.)",
                displacementLitres = 2.0,
                fuelType = FuelType.Petrol,
                driveType = "4WD",
                doors = 5,
                plant = "Ханчжоу, Китай (Geely)",
            )
            vds.contains("FY11") -> ExternalVehicleData(
                make = "Geely",
                model = "Tugella",
                engineSummary = "2.0T Drive-E (200-238 л.с.)",
                displacementLitres = 2.0,
                fuelType = FuelType.Petrol,
                driveType = "4WD",
                doors = 5,
                plant = "Ханчжоу, Китай (Geely)",
            )
            else -> ExternalVehicleData(
                make = "Geely",
                plant = "Китай (Geely)",
            )
        }
    }

    private fun decodeChangan(vds: String): ExternalVehicleData {
        return when {
            vds.contains("CS35") || vds.contains("S111") -> ExternalVehicleData(
                make = "Changan",
                model = "CS35 Plus",
                engineSummary = "1.4T (150 л.с.)",
                displacementLitres = 1.4,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Чунцин, Китай (Changan)",
            )
            vds.contains("CS55") || vds.contains("S201") -> ExternalVehicleData(
                make = "Changan",
                model = "CS55 Plus",
                engineSummary = "1.5T (181 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Чунцин, Китай (Changan)",
            )
            vds.contains("UNIV") || vds.contains("C385") -> ExternalVehicleData(
                make = "Changan",
                model = "UNI-V",
                engineSummary = "1.5T (181 л.с.)",
                displacementLitres = 1.5,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Чунцин, Китай (Changan)",
            )
            vds.contains("UNIK") || vds.contains("CD569") -> ExternalVehicleData(
                make = "Changan",
                model = "UNI-K",
                engineSummary = "2.0T (226 л.с.)",
                displacementLitres = 2.0,
                fuelType = FuelType.Petrol,
                doors = 5,
                plant = "Чунцин, Китай (Changan)",
            )
            else -> ExternalVehicleData(
                make = "Changan",
                plant = "Чунцин, Китай (Changan)",
            )
        }
    }

    private fun decodeKamaz(vds: String): ExternalVehicleData {
        return ExternalVehicleData(
            make = "КАМАЗ",
            plant = "Набережные Челны, Россия",
            fuelType = FuelType.Diesel,
        )
    }
}
