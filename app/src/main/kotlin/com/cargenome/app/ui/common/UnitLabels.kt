package com.cargenome.app.ui.common

import androidx.annotation.StringRes
import com.cargenome.app.R
import com.cargenome.app.domain.model.ConsumptionUnit
import com.cargenome.app.domain.model.DistanceUnit
import com.cargenome.app.domain.model.FuelType
import com.cargenome.app.domain.model.VolumeUnit
import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.data.db.entity.ServiceCategory

/** Value with its unit attached, for example "120 450 km". */
@StringRes
fun DistanceUnit.shortRes(): Int = when (this) {
    DistanceUnit.Kilometres -> R.string.unit_km
    DistanceUnit.Miles -> R.string.unit_mi
}

@StringRes
fun DistanceUnit.nameRes(): Int = when (this) {
    DistanceUnit.Kilometres -> R.string.unit_km_name
    DistanceUnit.Miles -> R.string.unit_mi_name
}

/** Just the unit, for a text field that already shows the number. */
@StringRes
fun DistanceUnit.suffixRes(): Int = when (this) {
    DistanceUnit.Kilometres -> R.string.unit_km_suffix
    DistanceUnit.Miles -> R.string.unit_mi_suffix
}

@StringRes
fun VolumeUnit.suffixRes(): Int = when (this) {
    VolumeUnit.Litres -> R.string.unit_litre_suffix
    VolumeUnit.UsGallons -> R.string.unit_gallon_suffix
    VolumeUnit.ImperialGallons -> R.string.unit_gallon_suffix
}

@StringRes
fun VolumeUnit.shortRes(): Int = when (this) {
    VolumeUnit.Litres -> R.string.unit_litre
    VolumeUnit.UsGallons -> R.string.unit_gallon_us
    VolumeUnit.ImperialGallons -> R.string.unit_gallon_imp
}

@StringRes
fun VolumeUnit.nameRes(): Int = when (this) {
    VolumeUnit.Litres -> R.string.unit_litre_name
    VolumeUnit.UsGallons -> R.string.unit_gallon_us_name
    VolumeUnit.ImperialGallons -> R.string.unit_gallon_imp_name
}

@StringRes
fun ConsumptionUnit.shortRes(): Int = when (this) {
    ConsumptionUnit.LitresPer100Km -> R.string.unit_l_100km
    ConsumptionUnit.KilometresPerLitre -> R.string.unit_km_l
    ConsumptionUnit.MilesPerUsGallon -> R.string.unit_mpg_us
    ConsumptionUnit.MilesPerImperialGallon -> R.string.unit_mpg_imp
}

@StringRes
fun FuelType.labelRes(): Int = when (this) {
    FuelType.Petrol -> R.string.fuel_petrol
    FuelType.Diesel -> R.string.fuel_diesel
    FuelType.Lpg -> R.string.fuel_lpg
    FuelType.Cng -> R.string.fuel_cng
    FuelType.Hybrid -> R.string.fuel_hybrid
    FuelType.PlugInHybrid -> R.string.fuel_plugin_hybrid
    FuelType.Electric -> R.string.fuel_electric
    FuelType.Other -> R.string.fuel_other
}

@StringRes
fun ServiceCategory.labelRes(): Int = when (this) {
    ServiceCategory.RoutineService -> R.string.service_category_routine
    ServiceCategory.Engine -> R.string.service_category_engine
    ServiceCategory.Transmission -> R.string.service_category_transmission
    ServiceCategory.Brakes -> R.string.service_category_brakes
    ServiceCategory.Suspension -> R.string.service_category_suspension
    ServiceCategory.Electrical -> R.string.service_category_electrical
    ServiceCategory.Tyres -> R.string.service_category_tyres
    ServiceCategory.Body -> R.string.service_category_body
    ServiceCategory.Diagnostics -> R.string.service_category_diagnostics
    ServiceCategory.Other -> R.string.service_category_other
}

@StringRes
fun ExpenseCategory.labelRes(): Int = when (this) {
    ExpenseCategory.Insurance -> R.string.expense_category_insurance
    ExpenseCategory.Tax -> R.string.expense_category_tax
    ExpenseCategory.Tyres -> R.string.expense_category_tyres
    ExpenseCategory.Fine -> R.string.expense_category_fine
    ExpenseCategory.Wash -> R.string.expense_category_wash
    ExpenseCategory.Parking -> R.string.expense_category_parking
    ExpenseCategory.Toll -> R.string.expense_category_toll
    ExpenseCategory.Registration -> R.string.expense_category_registration
    ExpenseCategory.Accessories -> R.string.expense_category_accessories
    ExpenseCategory.Credit -> R.string.expense_category_credit
    ExpenseCategory.Other -> R.string.expense_category_other
}

@StringRes
fun com.cargenome.app.data.db.entity.OdometerSource.labelRes(): Int = when (this) {
    com.cargenome.app.data.db.entity.OdometerSource.Manual -> R.string.odometer_source_manual
    com.cargenome.app.data.db.entity.OdometerSource.FuelRecord -> R.string.odometer_source_fuel
    com.cargenome.app.data.db.entity.OdometerSource.ServiceRecord -> R.string.odometer_source_service
    com.cargenome.app.data.db.entity.OdometerSource.Expense -> R.string.odometer_source_expense
}

@StringRes
fun com.cargenome.app.domain.service.ScheduleDueStatus.labelRes(): Int = when (this) {
    com.cargenome.app.domain.service.ScheduleDueStatus.Ok -> R.string.schedule_status_ok
    com.cargenome.app.domain.service.ScheduleDueStatus.DueSoon -> R.string.schedule_status_due_soon
    com.cargenome.app.domain.service.ScheduleDueStatus.Overdue -> R.string.schedule_status_overdue
}

