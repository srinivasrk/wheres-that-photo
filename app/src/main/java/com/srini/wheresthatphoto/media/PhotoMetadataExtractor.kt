package com.srini.wheresthatphoto.media

import android.content.ContentResolver
import android.content.Context
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "WTP/Metadata"

/**
 * Metadata extracted from a photo's EXIF headers.
 * All fields are nullable — a photo may have none, some, or all of them.
 */
data class PhotoMetadata(
    /** Human-readable date, e.g. "January 15, 2023". Null if EXIF is absent. */
    val dateTaken: String?,
    /** Human-readable time, e.g. "2:30 PM". Null if EXIF is absent. */
    val timeTaken: String?,
    /** Reverse-geocoded place name, e.g. "Paris, France". Null if GPS is absent or geocoding fails. */
    val location: String?
) {
    /** True only when there is at least one piece of useful metadata. */
    val hasAny: Boolean get() = dateTaken != null || timeTaken != null || location != null
}

/**
 * Reads EXIF date/time and GPS coordinates from a photo URI, then reverse-geocodes
 * the GPS to a human-readable place name using Android's on-device [Geocoder].
 *
 * All failures are caught and logged; [extract] always returns a [PhotoMetadata] object
 * (possibly with all-null fields) so the caller never needs to handle exceptions.
 */
class PhotoMetadataExtractor(private val context: Context) {

    private val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    private val displayTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun extract(contentResolver: ContentResolver, uri: Uri): PhotoMetadata {
        var dateTaken: String? = null
        var timeTaken: String? = null
        var lat: Double? = null
        var lng: Double? = null

        // ── EXIF ──────────────────────────────────────────────────────────────
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)

                // Date / time — prefer ORIGINAL (set by camera), fall back to write timestamp
                val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (rawDate != null) {
                    runCatching {
                        val parsed = exifDateFormat.parse(rawDate)
                        if (parsed != null) {
                            dateTaken = displayDateFormat.format(parsed)
                            timeTaken = displayTimeFormat.format(parsed)
                        }
                    }.onFailure {
                        Log.w(TAG, "Could not parse EXIF date \"$rawDate\": ${it.message}")
                    }
                }

                // GPS coordinates
                val latLng = FloatArray(2)
                if (exif.getLatLong(latLng)) {
                    lat = latLng[0].toDouble()
                    lng = latLng[1].toDouble()
                    Log.d(TAG, "extract: GPS lat=$lat lng=$lng")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extract: failed to read EXIF from $uri — ${e.message}")
        }

        // ── Reverse geocoding ─────────────────────────────────────────────────
        var location: String? = null
        if (lat != null && lng != null) {
            if (!Geocoder.isPresent()) {
                Log.d(TAG, "extract: Geocoder not available on this device")
            } else {
                try {
                    @Suppress("DEPRECATION") // Blocking form is fine on IO dispatcher (minSdk 31)
                    val addresses = Geocoder(context, Locale.getDefault())
                        .getFromLocation(lat!!, lng!!, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        // Build "City, State/Region, Country" dropping nulls and duplicates
                        location = listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                            .distinct()
                            .joinToString(", ")
                            .ifBlank { null }
                        Log.d(TAG, "extract: geocoded → $location")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "extract: reverse geocoding failed — ${e.message}")
                }
            }
        }

        val meta = PhotoMetadata(dateTaken, timeTaken, location)
        Log.i(TAG, "extract: date=${meta.dateTaken} time=${meta.timeTaken} location=${meta.location}")
        return meta
    }
}
