package com.cargenome.app.di

import android.content.Context
import androidx.room.Room
import com.cargenome.app.data.db.CarGenomeDatabase
import com.cargenome.app.data.db.dao.AttachmentDao
import com.cargenome.app.data.db.dao.ExpenseDao
import com.cargenome.app.data.db.dao.FuelRecordDao
import com.cargenome.app.data.db.dao.MaintenanceEventDao
import com.cargenome.app.data.db.dao.MaintenanceScheduleDao
import com.cargenome.app.data.db.dao.OdometerReadingDao
import com.cargenome.app.data.db.dao.ServiceRecordDao
import com.cargenome.app.data.db.dao.VehicleDao
import com.cargenome.app.data.db.dao.VinCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CarGenomeDatabase {
        checkAndPerformRestore(context)
        return Room.databaseBuilder(context, CarGenomeDatabase::class.java, CarGenomeDatabase.NAME)
            .addMigrations(
                CarGenomeDatabase.MIGRATION_1_2,
                CarGenomeDatabase.MIGRATION_2_3,
                CarGenomeDatabase.MIGRATION_3_4,
                CarGenomeDatabase.MIGRATION_4_5,
            )
            .build()
    }

    private fun checkAndPerformRestore(context: Context) {
        try {
            val candidateDirs = listOfNotNull(
                File(context.getExternalFilesDir(null), "restore"),
                File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "CarGenome_Restore"),
            )
            val restoreDir = candidateDirs.firstOrNull { dir ->
                val f = File(dir, "cargenome.db")
                f.exists() && f.canRead() && f.length() > 0
            } ?: return
            val restoreDb = File(restoreDir, "cargenome.db")

            val targetDb = context.getDatabasePath(CarGenomeDatabase.NAME)
            val parentDir = targetDb.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            // Copy to temp file first to prevent truncating targetDb if source read fails
            val tempDb = File(parentDir, "${CarGenomeDatabase.NAME}.tmp")
            restoreDb.copyTo(tempDb, overwrite = true)
            if (tempDb.exists() && tempDb.length() > 0) {
                tempDb.copyTo(targetDb, overwrite = true)
                tempDb.delete()
            } else {
                tempDb.delete()
                return
            }
            File(parentDir, "${CarGenomeDatabase.NAME}-wal").delete()
            File(parentDir, "${CarGenomeDatabase.NAME}-shm").delete()

            // Fix FileProvider authority if moving between debug and release package names
            try {
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    targetDb.path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
                ).use { db ->
                    db.execSQL(
                        "UPDATE vehicles SET insurancePdfUri = replace(insurancePdfUri, 'com.cargenome.app.debug.fileprovider', '${context.packageName}.fileprovider') WHERE insurancePdfUri IS NOT NULL",
                    )
                }
            } catch (t: Throwable) {
                android.util.Log.w("DatabaseModule", "Failed to rewrite insurance URI", t)
            }

            // Copy attachments
            val restoreAttachmentsDir = File(restoreDir, "attachments")
            if (restoreAttachmentsDir.exists() && restoreAttachmentsDir.isDirectory) {
                val targetAttachmentsDir = File(context.filesDir, "attachments")
                if (!targetAttachmentsDir.exists()) {
                    targetAttachmentsDir.mkdirs()
                }
                restoreAttachmentsDir.listFiles()?.forEach { file ->
                    file.copyTo(File(targetAttachmentsDir, file.name), overwrite = true)
                }
            }

            // Copy datastore preferences if present
            val restoreDatastoreDir = File(restoreDir, "datastore")
            if (restoreDatastoreDir.exists() && restoreDatastoreDir.isDirectory) {
                val targetDatastoreDir = File(context.filesDir, "datastore")
                if (!targetDatastoreDir.exists()) {
                    targetDatastoreDir.mkdirs()
                }
                restoreDatastoreDir.listFiles()?.forEach { file ->
                    file.copyTo(File(targetDatastoreDir, file.name), overwrite = true)
                }
            }

            // Rename restore.db so it only runs once
            val doneFile = File(restoreDir, "cargenome.db.restored")
            restoreDb.renameTo(doneFile)
            android.util.Log.i("DatabaseModule", "Auto-restored user database, attachments, and settings successfully")
        } catch (e: Throwable) {
            android.util.Log.e("DatabaseModule", "Failed during auto-restore", e)
        }
    }

    @Provides
    fun provideVehicleDao(database: CarGenomeDatabase): VehicleDao = database.vehicleDao()

    @Provides
    fun provideFuelRecordDao(database: CarGenomeDatabase): FuelRecordDao = database.fuelRecordDao()

    @Provides
    fun provideOdometerReadingDao(database: CarGenomeDatabase): OdometerReadingDao =
        database.odometerReadingDao()

    @Provides
    fun provideServiceRecordDao(database: CarGenomeDatabase): ServiceRecordDao =
        database.serviceRecordDao()

    @Provides
    fun provideMaintenanceScheduleDao(database: CarGenomeDatabase): MaintenanceScheduleDao =
        database.maintenanceScheduleDao()

    @Provides
    fun provideMaintenanceEventDao(database: CarGenomeDatabase): MaintenanceEventDao =
        database.maintenanceEventDao()

    @Provides
    fun provideExpenseDao(database: CarGenomeDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun provideAttachmentDao(database: CarGenomeDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideVinCacheDao(database: CarGenomeDatabase): VinCacheDao = database.vinCacheDao()
}
