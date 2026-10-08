package com.cargenome.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.data.db.dao.LoyaltyCardDao
import com.cargenome.app.data.db.dao.VehicleDao
import com.cargenome.app.data.db.entity.BarcodeType
import com.cargenome.app.data.db.entity.LoyaltyCardEntity
import com.cargenome.app.data.db.entity.LoyaltyCategory
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VolumeUnit
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoyaltyCardDaoTest {

    private lateinit var database: CarGenomeDatabase
    private lateinit var loyaltyDao: LoyaltyCardDao
    private lateinit var vehicleDao: VehicleDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CarGenomeDatabase::class.java,
        ).allowMainThreadQueries().build()
        loyaltyDao = database.loyaltyCardDao()
        vehicleDao = database.vehicleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveLoyaltyCard() = runTest {
        val card = LoyaltyCardEntity(
            title = "Лукойл Заправься выгодой",
            cardNumber = "7005123456789012",
            barcodeType = BarcodeType.Code128,
            category = LoyaltyCategory.Fuel,
            colorHex = 0xFFD32F2F,
            note = "Скидка 2%",
        )

        val id = loyaltyDao.insert(card)
        val loaded = loyaltyDao.findById(id)

        assertNotNull(loaded)
        assertEquals("Лукойл Заправься выгодой", loaded!!.title)
        assertEquals("7005123456789012", loaded.cardNumber)
        assertEquals(BarcodeType.Code128, loaded.barcodeType)
        assertEquals(LoyaltyCategory.Fuel, loaded.category)
        assertEquals(0xFFD32F2F, loaded.colorHex)
        assertEquals("Скидка 2%", loaded.note)
    }

    @Test
    fun observeByCategoryFiltersCorrectly() = runTest {
        loyaltyDao.insert(
            LoyaltyCardEntity(
                title = "Газпромнефть",
                cardNumber = "111",
                category = LoyaltyCategory.Fuel,
            ),
        )
        loyaltyDao.insert(
            LoyaltyCardEntity(
                title = "Мойка 24",
                cardNumber = "222",
                category = LoyaltyCategory.Wash,
            ),
        )
        loyaltyDao.insert(
            LoyaltyCardEntity(
                title = "Автодок",
                cardNumber = "333",
                category = LoyaltyCategory.Parts,
            ),
        )

        val fuelCards = loyaltyDao.observeByCategory(LoyaltyCategory.Fuel).first()
        assertEquals(1, fuelCards.size)
        assertEquals("Газпромнефть", fuelCards[0].title)

        val allCards = loyaltyDao.observeAll().first()
        assertEquals(3, allCards.size)
    }

    @Test
    fun deleteCardRemovesIt() = runTest {
        val id = loyaltyDao.insert(
            LoyaltyCardEntity(
                title = "Delete Me",
                cardNumber = "999",
            ),
        )
        assertNotNull(loyaltyDao.findById(id))

        loyaltyDao.deleteById(id)
        assertNull(loyaltyDao.findById(id))
    }

    @Test
    fun vehicleDeletionSetsVehicleIdToNull() = runTest {
        val vehicleId = vehicleDao.insert(
            VehicleEntity(
                make = "Mitsubishi",
                model = "Outlander",
                fuelType = FuelType.Petrol,
                distanceUnit = DistanceUnit.Kilometres,
                volumeUnit = VolumeUnit.Litres,
                currencyCode = "RUB",
                createdAt = Instant.now(),
            ),
        )

        val cardId = loyaltyDao.insert(
            LoyaltyCardEntity(
                title = "Сервис Mitsubishi",
                cardNumber = "MITSU-01",
                vehicleId = vehicleId,
            ),
        )

        val before = loyaltyDao.findById(cardId)
        assertEquals(vehicleId, before?.vehicleId)

        vehicleDao.deleteById(vehicleId)

        val after = loyaltyDao.findById(cardId)
        assertNotNull(after)
        assertNull(after!!.vehicleId)
    }
}
