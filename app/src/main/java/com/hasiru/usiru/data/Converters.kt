package com.hasiru.usiru.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun toAlertType(value: String): AlertType = AlertType.valueOf(value)
    @TypeConverter fun fromAlertType(value: AlertType): String = value.name

    @TypeConverter fun toAlertStatus(value: String): AlertStatus = AlertStatus.valueOf(value)
    @TypeConverter fun fromAlertStatus(value: AlertStatus): String = value.name
}
