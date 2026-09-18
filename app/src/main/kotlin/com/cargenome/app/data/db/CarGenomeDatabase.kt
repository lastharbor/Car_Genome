package com.cargenome.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cargenome.app.data.db.dao.AttachmentDao
import com.cargenome.app.data.db.dao.ExpenseDao
import com.cargenome.app.data.db.dao.FuelRecordDao
import com.cargenome.app.data.db.dao.MaintenanceEventDao
import com.cargenome.app.data.db.dao.MaintenanceScheduleDao
import com.cargenome.app.data.db.dao.OdometerReadingDao
import com.cargenome.app.data.db.dao.ServiceRecordDao
import com.cargenome.app.data.db.dao.VehicleDao
import com.cargenome.app.data.db.dao.VinCacheDao
import com.cargenome.app.data.db.entity.AttachmentEntity
import com.cargenome.app.data.db.entity.ExpenseEntity
import com.cargenome.app.data.db.entity.FuelRecordEntity
import com.cargenome.app.data.db.entity.MaintenanceEventEntity
import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import com.cargenome.app.data.db.entity.OdometerReadingEntity
import com.cargenome.app.data.db.entity.ServiceRecordEntity
import com.cargenome.app.data.db.entity.VehicleEntity
import com.cargenome.app.data.db.entity.VinCacheEntryEntity

@Database(
    entities = [
        VehicleEntity::class,
        FuelRecordEntity::class,
        OdometerReadingEntity::class,
        ServiceRecordEntity::class,
        MaintenanceScheduleEntity::class,
        ExpenseEntity::class,
        AttachmentEntity::class,
        VinCacheEntryEntity::class,
        MaintenanceEventEntity::class,
    ],
    version = CarGenomeDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(CarGenomeConverters::class)
abstract class CarGenomeDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    abstract fun fuelRecordDao(): FuelRecordDao

    abstract fun odometerReadingDao(): OdometerReadingDao

    abstract fun serviceRecordDao(): ServiceRecordDao

    abstract fun maintenanceScheduleDao(): MaintenanceScheduleDao

    abstract fun maintenanceEventDao(): MaintenanceEventDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun vinCacheDao(): VinCacheDao

    companion object {
        const val VERSION = 5
        const val NAME = "cargenome.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN insuranceProvider TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN insurancePolicyNumber TEXT")
                db.execSQL("ALTER TABLE vehicles ADD COLUMN insuranceExpiresOn INTEGER")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN insurancePdfUri TEXT")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `maintenance_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `vehicleId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `scheduledDate` INTEGER NOT NULL,
                        `scheduledTimeMinutes` INTEGER,
                        `targetOdometerKm` REAL,
                        `estimatedCostMinor` INTEGER,
                        `shop` TEXT,
                        `notes` TEXT,
                        `remindAdvanceDays` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `serviceRecordId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`serviceRecordId`) REFERENCES `service_records`(`id`) ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_events_vehicleId` ON `maintenance_events` (`vehicleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_events_scheduledDate` ON `maintenance_events` (`scheduledDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_events_serviceRecordId` ON `maintenance_events` (`serviceRecordId`)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `maintenance_events` ADD COLUMN `scheduleId` INTEGER REFERENCES `maintenance_schedules`(`id`) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_events_scheduleId` ON `maintenance_events` (`scheduleId`)")
            }
        }
    }
}
