package com.srini.wheresthatphoto.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
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
    /** Raw latitude from EXIF GPS. Null when missing/invalid/placeholder. */
    val lat: Double?,
    /** Raw longitude from EXIF GPS. Null when missing/invalid/placeholder. */
    val lng: Double?,
    /** Reverse-geocoded place name, e.g. "Paris, France". Null if GPS is absent or geocoding fails. */
    val location: String?
) {
    /** True only when there is at least one piece of useful metadata. */
    val hasAny: Boolean get() = dateTaken != null || timeTaken != null || location != null || (lat != null && lng != null)
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

        // Photo Picker URIs and bare MediaStore URIs both have GPS tags scrubbed
        // from their streams unless we explicitly opt in with setRequireOriginal
        // (and hold ACCESS_MEDIA_LOCATION). Resolve to an "original" URI first.
        val originalUri = resolveOriginalUri(uri)

        // ── EXIF ──────────────────────────────────────────────────────────────
        try {
            contentResolver.openInputStream(originalUri)?.use { stream ->
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

                // GPS coordinates. AndroidX ExifInterface returns a DoubleArray
                // or null (in contrast to the framework class's FloatArray).
                val latLng = exif.latLong
                if (latLng != null) {
                    val rawLat = latLng[0]
                    val rawLng = latLng[1]
                    if (isUsableCoordinate(rawLat, rawLng)) {
                        lat = rawLat
                        lng = rawLng
                        Log.d(TAG, "extract: GPS lat=$lat lng=$lng")
                    } else {
                        Log.d(TAG, "extract: ignoring unusable GPS lat=$rawLat lng=$rawLng")
                    }
                } else {
                    Log.d(TAG, "extract: no GPS tags on $originalUri (stream may be redacted)")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extract: failed to read EXIF from $originalUri — ${e.message}")
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

        val meta = PhotoMetadata(dateTaken, timeTaken, lat, lng, location)
        Log.i(TAG, "extract: date=${meta.dateTaken} time=${meta.timeTaken} location=${meta.location}")
        return meta
    }

    /**
     * Returns a URI whose stream contains the un-redacted EXIF (including GPS),
     * or the original URI if we can't produce a better one.
     *
     * Two cases matter:
     *  1. Photo Picker URIs — `content://media/picker/<user>/<authority>/media/<id>`.
     *     These are served by the picker provider which strips GPS. The trailing
     *     path segment is the underlying MediaStore `_ID`, so we can rebuild a
     *     MediaStore URI and call [MediaStore.setRequireOriginal] on that.
     *  2. Plain MediaStore URIs — `content://media/external/images/media/<id>`.
     *     Streams from these are also GPS-redacted by default on API 29+; the
     *     same [MediaStore.setRequireOriginal] wrap lifts the redaction.
     *
     * Both require the app to hold ACCESS_MEDIA_LOCATION at runtime. If the
     * permission is missing the call silently falls back to the redacted stream.
     */
    private fun resolveOriginalUri(uri: Uri): Uri {
        return try {
            val mediaStoreUri: Uri? = when {
                uri.authority == MediaStore.AUTHORITY &&
                    uri.pathSegments.firstOrNull() == "picker" -> {
                    val id = uri.lastPathSegment?.toLongOrNull()
                    if (id == null) {
                        Log.d(TAG, "resolveOriginalUri: picker URI without numeric id: $uri")
                        null
                    } else {
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                    }
                }
                uri.authority == MediaStore.AUTHORITY -> uri
                else -> null
            }
            if (mediaStoreUri == null) uri else MediaStore.setRequireOriginal(mediaStoreUri)
        } catch (e: SecurityException) {
            // ACCESS_MEDIA_LOCATION not granted, or caller isn't allowed to
            // request originals for this URI. Fall back to the redacted stream
            // so the rest of the metadata (date/time) still gets extracted.
            Log.w(TAG, "resolveOriginalUri: setRequireOriginal denied for $uri — ${e.message}")
            uri
        } catch (e: Exception) {
            Log.w(TAG, "resolveOriginalUri: failed for $uri — ${e.message}")
            uri
        }
    }

    private fun isUsableCoordinate(lat: Double, lng: Double): Boolean {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
        // Many images carry placeholder GPS (0,0). Treat that as missing.
        if (lat == 0.0 && lng == 0.0) return false
        return true
    }
}
