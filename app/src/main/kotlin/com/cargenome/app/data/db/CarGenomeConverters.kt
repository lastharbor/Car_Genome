package com.cargenome.app.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/**
 * Room stores instants as epoch milliseconds and dates as epoch days. Both are
 * integers, so they sort and compare correctly in SQL, which matters because
 * nearly every query here is ordered by time.
 */
internal class CarGenomeConverters {

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}
