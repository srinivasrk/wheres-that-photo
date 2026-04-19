package com.srini.wheresthatphoto

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.srini.wheresthatphoto.indexing.IndexProgress
import com.srini.wheresthatphoto.ui.AppScreen
import com.srini.wheresthatphoto.ui.theme.WheresThatPhotoTheme
import kotlinx.coroutines.launch

private const val TAG = "WTP/Main"

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { (application as WheresThatPhotoApp).container }
    private var hasPermission by mutableStateOf(false)
    private var selectedUris by mutableStateOf<List<Uri>>(emptyList())
    private var statusMessage by mutableStateOf("")
    private var indexing by mutableStateOf(false)
    private var indexProgress by mutableStateOf<IndexProgress?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    private val pickPhotosLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(PocMaxPhotos)
    ) { uris ->
        Log.d(TAG, "Picker returned ${uris.size} URI(s)")
        // Persist read permission for each URI so they remain accessible after
        // the photo-picker session ends (e.g., when the app is restarted).
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Persisted URI permission: $uri")
            } catch (e: SecurityException) {
                // URI doesn't support persistable grants (e.g., file:// URIs) — ignore.
                Log.w(TAG, "Could not persist URI permission for $uri: ${e.message}")
            }
        }
        selectedUris = uris.take(PocMaxPhotos)
        statusMessage = if (selectedUris.isEmpty()) {
            Log.d(TAG, "No photos selected")
            "No photos selected."
        } else {
            Log.d(TAG, "${selectedUris.size} photo(s) selected (capped at $PocMaxPhotos)")
            "${selectedUris.size} photo(s) selected. Tap Index & caption."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)

        setContent {
            var permissionBannerDismissed by rememberSaveable { mutableStateOf(false) }
            WheresThatPhotoTheme {
                AppScreen(
                    hasPermission = hasPermission,
                    permissionBannerDismissed = permissionBannerDismissed,
                    onDismissPermissionBanner = { permissionBannerDismissed = true },
                    selectedPhotoCount = selectedUris.size,
                    statusMessage = statusMessage,
                    indexing = indexing,
                    indexProgress = indexProgress,
                    onPickPhotos = {
                        pickPhotosLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onIndexPoc = {
                        if (selectedUris.isNotEmpty()) {
                            lifecycleScope.launch {
                                Log.i(TAG, "Starting indexing for ${selectedUris.size} photo(s)")
                                indexing = true
                                indexProgress = null
                                statusMessage = "Indexing ${selectedUris.size} photo(s)…"
                                try {
                                    appContainer.indexer
                                        .indexPickedPhotosFlow(contentResolver, selectedUris)
                                        .collect { progress -> indexProgress = progress }
                                    val readyCount = appContainer.searchRepository
                                        .getIndexedPhotoSummaries().size
                                    Log.i(TAG, "Indexing complete — $readyCount photo(s) fully ready in DB")
                                    statusMessage =
                                        "Done: $readyCount photo(s) fully indexed with MobileCLIP + captions. You can search now."
                                } catch (e: Exception) {
                                    Log.e(TAG, "Indexing failed", e)
                                    statusMessage = e.message ?: "Indexing failed."
                                } finally {
                                    indexing = false
                                    indexProgress = null
                                }
                            }
                        }
                    },
                    onClearAll = {
                        lifecycleScope.launch {
                            Log.i(TAG, "User triggered DB clear")
                            appContainer.indexer.clearAllData()
                            selectedUris = emptyList()
                            statusMessage = "All indexed data cleared."
                            Log.i(TAG, "DB clear complete")
                        }
                    },
                    onSearch = { query -> appContainer.searchRepository.search(query) },
                    onGetCaption = { photoId -> appContainer.searchRepository.getCaption(photoId) },
                    onRecaption = { photoId ->
                        appContainer.indexer.recaptionPhoto(contentResolver, photoId)
                    },
                    onLoadIndexedPhotos = {
                        appContainer.searchRepository.getIndexedPhotoSummaries()
                    }
                )
            }
        }
    }

    companion object {
        private const val PocMaxPhotos = 50
    }
}
