package com.cargenome.app.data.export

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.AttachmentOwner
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreFolderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val restoreDir: File
        get() = File(context.getExternalFilesDir(null), "restore").apply { mkdirs() }
    private val ownAuthority: String
        get() = "${context.packageName}.fileprovider"
    private val otherAuthority: String
        get() = if (context.packageName.endsWith(".debug")) {
            "com.cargenome.app.fileprovider"
        } else {
            "com.cargenome.app.debug.fileprovider"
        }

    @After
    fun tearDown() {
        context.deleteDatabase(CarGenomeDatabase.NAME)
        context.deleteDatabase(SOURCE_NAME)
        restoreDir.deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
    }

    @Test
    fun leavesAnInstallThatHasDataUntouched() {
        createDatabase(CarGenomeDatabase.NAME) { it.vehicleDao().insert(car(1L)) }
        stageRestore { it.vehicleDao().insert(car(2L)) }

        assertFalse(RestoreFolder.applyIfFreshInstall(context))

        assertEquals(listOf(1L), readTarget { it.vehicleDao().listAll().map { v -> v.id } })
        assertTrue(File(restoreDir, "cargenome.db").exists())
    }

    @Test
    fun seedsAnEmptyInstallAndPointsUrisAtThisBuild() {
        // Opened once, nothing entered yet: still counts as fresh.
        createDatabase(CarGenomeDatabase.NAME) {}
        stageRestore { db ->
            db.vehicleDao().insert(
                car(2L).copy(
                    photoUri = "content://$otherAuthority/attachments/photo.jpg",
                    insurancePdfUri = "content://$otherAuthority/attachments/policy.pdf",
                ),
            )
            db.attachmentDao().insert(
                AttachmentEntity(
                    ownerType = AttachmentOwner.Vehicle,
                    ownerId = 2L,
                    uri = "content://$otherAuthority/attachments/receipt.jpg",
                    addedAt = Instant.parse("2025-03-01T12:00:00Z"),
                ),
            )
        }
        File(restoreDir, "attachments").apply { mkdirs() }.resolve("receipt.jpg").writeText("receipt")

        assertTrue(RestoreFolder.applyIfFreshInstall(context))

        val vehicle = readTarget { it.vehicleDao().findById(2L)!! }
        assertEquals("content://$ownAuthority/attachments/photo.jpg", vehicle.photoUri)
        assertEquals("content://$ownAuthority/attachments/policy.pdf", vehicle.insurancePdfUri)
        val attachment = readTarget { it.attachmentDao().listForOwner(AttachmentOwner.Vehicle, 2L).single() }
        assertEquals("content://$ownAuthority/attachments/receipt.jpg", attachment.uri)
        assertTrue(File(context.filesDir, "attachments/receipt.jpg").exists())
        assertFalse(File(restoreDir, "cargenome.db").exists())
        assertTrue(File(restoreDir, "cargenome.db.restored").exists())
    }

    @Test
    fun aCorruptRestoreFileLeavesTheInstallAsItWasAndIsRetried() {
        File(restoreDir, "cargenome.db").writeText("not a database")

        assertFalse(RestoreFolder.applyIfFreshInstall(context))

        assertFalse(context.getDatabasePath(CarGenomeDatabase.NAME).exists())
        assertTrue(File(restoreDir, "cargenome.db").exists())
    }

    @Test
    fun aDamagedRestoreFileIsRejected() {
        stageRestore { db ->
            db.vehicleDao().insert(car(2L))
            db.fuelRecordDao().insert(
                FuelRecordEntity(
                    vehicleId = 2L,
                    filledAt = Instant.parse("2025-03-01T12:00:00Z"),
                    odometerKm = 1_000.0,
                    volumeLitres = 40.0,
                    totalCostMinor = 200_000L,
                    isFullTank = true,
                    missedPreviousFillUp = false,
                ),
            )
        }
        val staged = File(restoreDir, "cargenome.db")
        // Wreck the fuel log's page only. Opening the file and rewriting URIs never
        // touch it, so the damage is caught by the integrity check or not at all.
        val (pageSize, rootPage) = SQLiteDatabase.openDatabase(staged.path, null, SQLiteDatabase.OPEN_READONLY)
            .use { db ->
                DatabaseUtils.longForQuery(db, "PRAGMA page_size", null).toInt() to
                    DatabaseUtils.longForQuery(db, "SELECT rootpage FROM sqlite_master WHERE name = 'fuel_records'", null)
                        .toInt()
            }
        val bytes = staged.readBytes()
        for (i in (rootPage - 1) * pageSize until rootPage * pageSize) bytes[i] = 0x5A
        staged.writeBytes(bytes)

        assertFalse(RestoreFolder.applyIfFreshInstall(context))

        assertFalse(context.getDatabasePath(CarGenomeDatabase.NAME).exists())
        assertTrue(staged.exists())
    }

    private fun stageRestore(fill: suspend (CarGenomeDatabase) -> Unit) {
        createDatabase(SOURCE_NAME, fill)
        context.getDatabasePath(SOURCE_NAME).copyTo(File(restoreDir, "cargenome.db"), overwrite = true)
    }

    private fun createDatabase(name: String, fill: suspend (CarGenomeDatabase) -> Unit) {
        // TRUNCATE keeps every write in the main file, which is what gets copied.
        val db = Room.databaseBuilder(context, CarGenomeDatabase::class.java, name)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        db.openHelper.writableDatabase // Room creates the file lazily.
        runBlocking { fill(db) }
        db.close()
    }

    private fun <T> readTarget(query: suspend (CarGenomeDatabase) -> T): T {
        val db = Room.databaseBuilder(context, CarGenomeDatabase::class.java, CarGenomeDatabase.NAME).build()
        return try {
            runBlocking { query(db) }
        } finally {
            db.close()
        }
    }

    private fun car(id: Long) = VehicleEntity(id = id, make = "Lada", model = "Vesta", currencyCode = "RUB")

    private companion object {
        const val SOURCE_NAME = "restore-source.db"
    }
}
