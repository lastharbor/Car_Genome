package com.cargenome.app.domain.service

import com.cargenome.app.data.db.entity.MaintenanceScheduleEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ScheduleDueStatus {
    Ok,
    DueSoon,
    Overdue,
}

data class ScheduleStatus(
    val schedule: MaintenanceScheduleEntity,
    val overallStatus: ScheduleDueStatus,
    val dueKm: Double? = null,
    val remainingKm: Double? = null,
    val dueDate: LocalDate? = null,
    val remainingDays: Long? = null,
) {
    val isOverdue: Boolean get() = overallStatus == ScheduleDueStatus.Overdue
    val isDueSoon: Boolean get() = overallStatus == ScheduleDueStatus.DueSoon
}

object MaintenanceScheduleCalculator {

    fun calculate(
        schedule: MaintenanceScheduleEntity,
        currentOdometerKm: Double?,
        initialOdometerKm: Double? = null,
        purchasedOn: LocalDate? = null,
        vehicleCreatedAt: LocalDate? = null,
        currentDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ScheduleStatus {
        if (!schedule.isEnabled) {
            return ScheduleStatus(
                schedule = schedule,
                overallStatus = ScheduleDueStatus.Ok,
            )
        }

        var kmStatus: ScheduleDueStatus? = null
        var dueKm: Double? = null
        var remainingKm: Double? = null

        if (schedule.intervalKm != null && schedule.intervalKm > 0.0) {
            val baseKm = schedule.lastPerformedOdometerKm ?: initialOdometerKm ?: 0.0
            val targetKm = baseKm + schedule.intervalKm
            dueKm = targetKm

            if (currentOdometerKm != null) {
                val diff = targetKm - currentOdometerKm
                remainingKm = diff
                kmStatus = when {
                    diff <= 0.0 -> ScheduleDueStatus.Overdue
                    diff <= schedule.warnBeforeKm -> ScheduleDueStatus.DueSoon
                    else -> ScheduleDueStatus.Ok
                }
            }
        }

        var timeStatus: ScheduleDueStatus? = null
        var dueDate: LocalDate? = null
        var remainingDays: Long? = null

        if (schedule.intervalMonths != null && schedule.intervalMonths > 0) {
            val baseDate = schedule.lastPerformedAt?.atZone(zoneId)?.toLocalDate()
                ?: purchasedOn
                ?: vehicleCreatedAt
            if (baseDate != null) {
                val targetDate = baseDate.plusMonths(schedule.intervalMonths.toLong())
                dueDate = targetDate
                val days = ChronoUnit.DAYS.between(currentDate, targetDate)
                remainingDays = days
                timeStatus = when {
                    days < 0 -> ScheduleDueStatus.Overdue
                    days <= schedule.warnBeforeDays -> ScheduleDueStatus.DueSoon
                    else -> ScheduleDueStatus.Ok
                }
            }
        }

        val overall = when {
            kmStatus == ScheduleDueStatus.Overdue || timeStatus == ScheduleDueStatus.Overdue ->
                ScheduleDueStatus.Overdue
            kmStatus == ScheduleDueStatus.DueSoon || timeStatus == ScheduleDueStatus.DueSoon ->
                ScheduleDueStatus.DueSoon
            else ->
                ScheduleDueStatus.Ok
        }

        return ScheduleStatus(
            schedule = schedule,
            overallStatus = overall,
            dueKm = dueKm,
            remainingKm = remainingKm,
            dueDate = dueDate,
            remainingDays = remainingDays,
        )
    }

    fun calculateAll(
        schedules: List<MaintenanceScheduleEntity>,
        currentOdometerKm: Double?,
        initialOdometerKm: Double? = null,
        purchasedOn: LocalDate? = null,
        vehicleCreatedAt: LocalDate? = null,
        currentDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<ScheduleStatus> = schedules.map {
        calculate(it, currentOdometerKm, initialOdometerKm, purchasedOn, vehicleCreatedAt, currentDate, zoneId)
    }.sortedWith(
        compareBy<ScheduleStatus> {
            when (it.overallStatus) {
                ScheduleDueStatus.Overdue -> 0
                ScheduleDueStatus.DueSoon -> 1
                ScheduleDueStatus.Ok -> 2
            }
        }.thenBy { it.schedule.title },
    )
}
