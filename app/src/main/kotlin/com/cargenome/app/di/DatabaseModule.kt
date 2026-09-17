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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CarGenomeDatabase =
        Room.databaseBuilder(context, CarGenomeDatabase::class.java, CarGenomeDatabase.NAME)
            .addMigrations(
                CarGenomeDatabase.MIGRATION_1_2,
                CarGenomeDatabase.MIGRATION_2_3,
                CarGenomeDatabase.MIGRATION_3_4,
            )
            .build()

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
