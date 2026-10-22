package com.spendly.app.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.spendly.app.core.model.LocationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val source: LocationSource = LocationSource.CONFIRMATION_TIME,
    val label: String? = null
)

class LocationHelper(
    private val context: Context
) {
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    suspend fun getLastKnownLocation(): CapturedLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        val lm = locationManager ?: return@withContext null

        try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                if (lm.isProviderEnabled(provider)) {
                    val loc = lm.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            }

            val location = bestLocation ?: return@withContext null

            // Reverse geocode to get a simple place label if available
            val label = getPlaceLabel(location.latitude, location.longitude)

            val source = LocationSource.CONFIRMATION_TIME

            CapturedLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                capturedAt = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                source = source,
                label = label
            )
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun getPlaceLabel(lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val subLocality = addr.subLocality ?: addr.locality ?: addr.subAdminArea
                val feature = addr.featureName
                if (!feature.isNullOrBlank() && feature != subLocality) {
                    "$feature, $subLocality"
                } else {
                    subLocality
                }
            } else {
                null
            }
        } catch (_: Exception) {
            // Geocoder service might be unavailable offline, which is completely expected
            null
        }
    }
}
