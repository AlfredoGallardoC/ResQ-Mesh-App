package com.resqmesh.app

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.resqmesh.app.data.AlertEntity
import com.resqmesh.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "ResQ_SQLite"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val db = AppDatabase.getDatabase(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val existingAlerts = db.alertDao().getAllAlerts()
            Log.d(TAG, "Registros previos almacenados en la BD: ${existingAlerts.size}")

            val testAlert = AlertEntity(
                userId = "RESCUER_TEST_01",
                timestamp = System.currentTimeMillis(),
                latitude = 19.432608,
                longitude = -99.133209,
                incidentType = 1,
                battery = 95,
                urgencyLevel = 3
            )

            val startTime = System.currentTimeMillis()
            val insertedId = db.alertDao().insertAlert(testAlert)
            val elapsedTime = System.currentTimeMillis() - startTime

            Log.d(TAG, "--------------------------------------------------")
            Log.d(TAG, "ALERTA GUARDADA EXITOSAMENTE EN SQLITE")
            Log.d(TAG, "ID Generado: $insertedId")
            Log.d(TAG, "Tiempo de Inserción: $elapsedTime ms")
            Log.d(TAG, "Criterio HU-03 (< 50 ms): ${if (elapsedTime < 50) "CUMPLE (ÉXITO)" else "NO CUMPLE"}")
            Log.d(TAG, "--------------------------------------------------")
        }
    }
}