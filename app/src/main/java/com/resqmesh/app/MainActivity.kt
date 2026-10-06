package com.resqmesh.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.resqmesh.app.data.AlertEntity
import com.resqmesh.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private val TAG = "ResQ_SQLite"

    // Componentes para la HU-04 (Cartografía Offline)
    private lateinit var mapView: MapView
    private lateinit var tvDistance: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Coordenadas y Marcadores
    private var victimLocation: GeoPoint = GeoPoint(31.8625, -116.6264) // Valor por defecto
    private var victimMarker: Marker? = null
    private var rescuerMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración requerida por OSMdroid para manejo de tiles locales
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_main)

        // Vincular componentes de la UI (HU-04)
        mapView = findViewById(R.id.mapView)
        tvDistance = findViewById(R.id.tvDistance)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializar la base de datos (HU-03)
        val db = AppDatabase.getDatabase(this)

        // 1. Ejecutar prueba de inserción SQLite (HU-03) y obtener la ubicación
        lifecycleScope.launch(Dispatchers.IO) {
            val existingAlerts = db.alertDao().getAllAlerts()
            Log.d(TAG, "Registros previos almacenados en la BD: ${existingAlerts.size}")

            val testAlert = AlertEntity(
                userId = "RESCUER_TEST_01",
                timestamp = System.currentTimeMillis(),
                latitude = 31.8625,
                longitude = -116.6264,
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

            // Actualizar la coordenada de la víctima desde los datos insertados en Room
            victimLocation = GeoPoint(testAlert.latitude, testAlert.longitude)

            // Pasar al hilo principal para renderizar el mapa con la alerta guardada
            withContext(Dispatchers.Main) {
                setupMap()
                checkLocationPermissions()
            }
        }
    }

    // Configurar el mapa offline y el pin de la víctima (HU-04)
    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(17.5)
        mapView.controller.setCenter(victimLocation)

        // Limpiar overlays anteriores si los hay
        mapView.overlays.clear()

        // Inyectar Marcador de la Víctima guardada en la BD
        victimMarker = Marker(mapView).apply {
            position = victimLocation
            title = "Víctima Registrada (Room DB)"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(victimMarker)
        mapView.invalidate()
    }

    // Solicitar permisos de ubicación para rastrear al rescatista
    private fun checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        } else {
            startLocationUpdates()
        }
    }

    // Escuchar actualizaciones de ubicación del sensor GPS
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    for (location in locationResult.locations) {
                        updateRescuerPosition(location)
                    }
                }
            }, mainLooper)
        }
    }

    // Dibujar posición del rescatista y calcular la distancia relativa hacia la víctima
    private fun updateRescuerPosition(location: Location) {
        val rescuerGeoPoint = GeoPoint(location.latitude, location.longitude)

        // 1. Dibujar o actualizar el Pin del Rescatista
        if (rescuerMarker == null) {
            rescuerMarker = Marker(mapView).apply {
                title = "Ubicación del Rescatista"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(rescuerMarker)
        }
        rescuerMarker?.position = rescuerGeoPoint

        // 2. Calcular la Distancia Relativa en Metros hacia la Víctima
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            victimLocation.latitude, victimLocation.longitude,
            results
        )
        val distanceInMeters = results[0]

        // 3. Actualizar la UI
        tvDistance.text = String.format("Distancia a la víctima: %.1f metros", distanceInMeters)
        mapView.invalidate()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}