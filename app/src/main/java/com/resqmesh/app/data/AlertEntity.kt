package com.resqmesh.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val incidentType: Int,
    val battery: Int,
    val urgencyLevel: Int
)