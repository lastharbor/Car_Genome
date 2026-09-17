package com.cargenome.vin

import java.time.Year

/**
 * Reads everything a VIN carries on its own, without a network call.
 *
 * This is the base layer of VIN handling: it works for every car ever built,
 * including the ones no online decoder knows about, and online data is only
 * ever layered on top of it.
 */
class OfflineVinDecoder(
    private val wmiRegistry: WmiRegistry = WmiRegistry.Empty,
    private val currentYear: () -> Int = { Year.now().value },
) {

    fun decode(raw: String): VinDecodeResult {
        val vin = VinFormat.normalize(raw)

        val illegalPositions = vin.mapIndexedNotNull { index, c ->
            (index + 1).takeUnless { VinFormat.isAllowedChar(c) }
        }

        val structuralProblems = buildList {
            if (vin.length != VinFormat.LENGTH) add(VinProblem.WrongLength(vin.length))
            if (illegalPositions.isNotEmpty()) add(VinProblem.IllegalCharacters(illegalPositions))
        }
        if (structuralProblems.isNotEmpty()) {
            return VinDecodeResult.Malformed(vin, structuralProblems)
        }

        val manufacturer = wmiRegistry.lookup(vin)
        val country = resolveCountry(vin, manufacturer)
        val checkDigit = VinCheckDigit.verify(vin, mandatory = isCheckDigitMandatory(country))

        val problems = buildList {
            if (!checkDigit.matches && checkDigit.expected != null && checkDigit.actual != null) {
                add(
                    VinProblem.CheckDigitMismatch(
                        expected = checkDigit.expected,
                        actual = checkDigit.actual,
                        mandatory = checkDigit.isMandatory,
                    ),
                )
            }
        }

        return VinDecodeResult.Decoded(
            DecodedVin(
                vin = vin,
                wmi = vin.substring(0, 3),
                vds = vin.substring(3, 9),
                vis = vin.substring(9),
                country = country,
                manufacturer = manufacturer,
                modelYear = VinModelYear.decode(vin, currentYear()),
                plantCode = vin[VinFormat.PLANT_INDEX],
                serialNumber = vin.substring(11),
                checkDigit = checkDigit,
                problems = problems,
            ),
        )
    }

    /**
     * The ISO chart leaves gaps: X2 through X0 carry no allocation even though
     * X7L (Renault Russia) and X96 (GAZ) are in daily use. Where the chart is
     * silent, the country recorded against the WMI fills in.
     */
    private fun resolveCountry(vin: String, manufacturer: WmiEntry?): VinCountry? {
        VinCountries.resolve(vin)?.let { return it }
        val fallback = manufacturer?.country ?: return null
        val region = VinCountries.regionOf(vin[0]) ?: return null
        return VinCountry(code = fallback, region = region)
    }

    /** The check digit is compulsory in North America and China only. */
    private fun isCheckDigitMandatory(country: VinCountry?): Boolean =
        country != null && (country.region == VinRegion.NorthAmerica || country.code == "CN")
}
