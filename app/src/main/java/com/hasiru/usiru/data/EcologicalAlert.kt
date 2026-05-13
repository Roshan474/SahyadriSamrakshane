package com.hasiru.usiru.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ecological_alerts")
data class EcologicalAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AlertType,
    val status: AlertStatus = AlertStatus.REPORTED,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val photoPath: String?,
    val notes: String,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
