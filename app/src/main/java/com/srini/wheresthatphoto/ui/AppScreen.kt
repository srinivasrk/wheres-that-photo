package com.srini.wheresthatphoto.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.util.Log
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.srini.wheresthatphoto.indexing.IndexPhase
import com.srini.wheresthatphoto.indexing.IndexProgress
import com.srini.wheresthatphoto.search.IndexedPhotoSummary
import com.srini.wheresthatphoto.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "WTP/UI"

private const val PocMaxPhotos = 50

private sealed class DetailTarget {
    data class FromSearch(val result: SearchResult) : DetailTarget()
    data class FromLibrary(val photo: IndexedPhotoSummary) : DetailTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    hasPermission: Boolean,
    permissionBannerDismissed: Boolean,
    onDismissPermissionBanner: () -> Unit,
    selectedPhotoCount: Int,
    statusMessage: String,
    indexing: Boolean,
    indexProgress: IndexProgress?,
    onPickPhotos: () -> Unit,
    onIndexPoc: () -> Unit,
    onClearAll: () -> Unit,
    onSearch: suspend (String) -> List<SearchResult>,
    onGetCaption: suspend (String) -> String?,
    onRecaption: suspend (String) -> String,
    onLoadIndexedPhotos: suspend () -> List<IndexedPhotoSummary>
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<SearchResult>()) }
    var hasSearched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<DetailTarget?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var indexedPhotos by remember { mutableStateOf<List<IndexedPhotoSummary>>(emptyList()) }
    var libraryLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(selectedTab, indexing) {
        if (selectedTab == 1) {
            libraryLoading = true
            try {
                indexedPhotos = onLoadIndexedPhotos()
            } finally {
                libraryLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GradientHeader()

        if (!hasPermission && !permissionBannerDismissed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "READ_MEDIA_IMAGES not granted. Optional for the system picker on many devices.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onDismissPermissionBanner) {
                        Text("Dismiss")
                    }
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Search") },
                icon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Library") },
                icon = {
                    Icon(
                        Icons.Filled.PhotoLibrary,
                        contentDescription = null
                    )
                }
            )
        }

        if (indexing && indexProgress != null) {
            val p = indexProgress
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Indexing ${p.current} of ${p.total}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(
                        progress = { p.current.toFloat() / p.total.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        val ctx = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(p.currentUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            onError = { err ->
                                Log.e(TAG, "Progress thumb load failed uri=${p.currentUri}: ${err.result.throwable}")
                            },
                            modifier = Modifier
                                .width(72.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (p.phase) {
                                    IndexPhase.Embedding -> "Computing image embedding…"
                                    IndexPhase.Captioning ->
                                        if (p.captionText == null) {
                                            "Generating caption…"
                                        } else {
                                            "Caption ready"
                                        }
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = p.captionText ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else if (indexing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Starting indexing…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            transitionSpec = {
                val enter = fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it / 10 }
                val exit = fadeOut(animationSpec = tween(180)) +
                    slideOutHorizontally(animationSpec = tween(200)) { -it / 12 }
                enter togetherWith exit
            },
            label = "tabContent"
        ) { tab ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> SearchTabContent(
                        modifier = Modifier.fillMaxSize(),
                        selectedPhotoCount = selectedPhotoCount,
                        indexing = indexing,
                        searching = searching,
                        onPickPhotos = onPickPhotos,
                        onIndexPoc = onIndexPoc,
                        onClearAll = onClearAll,
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {
                            scope.launch {
                                searching = true
                                results = emptyList()
                                // Run the actual search and a 500 ms minimum delay in parallel,
                                // so the loading state is always visible long enough to register.
                                val searchDeferred = async { onSearch(query) }
                                delay(500)
                                results = searchDeferred.await()
                                hasSearched = true
                                searching = false
                            }
                        },
                        hasSearched = hasSearched,
                        results = results,
                        onResultClick = { detailTarget = DetailTarget.FromSearch(it) }
                    )
                    1 -> LibraryTabContent(
                        modifier = Modifier.fillMaxSize(),
                        photos = indexedPhotos,
                        loading = libraryLoading,
                        onPhotoClick = { detailTarget = DetailTarget.FromLibrary(it) }
                    )
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }

    detailTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { detailTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            when (target) {
                is DetailTarget.FromSearch ->
                    PhotoDetailSheetContent(
                        searchResult = target.result,
                        photoId = target.result.photoId,
                        uri = target.result.uri,
                        onDismiss = { detailTarget = null },
                        onGetCaption = onGetCaption,
                        onRecaption = onRecaption
                    )
                is DetailTarget.FromLibrary ->
                    PhotoDetailSheetContent(
                        searchResult = null,
                        photoId = target.photo.photoId,
                        uri = target.photo.uri,
                        onDismiss = { detailTarget = null },
                        onGetCaption = onGetCaption,
                        onRecaption = onRecaption
                    )
            }
        }
    }
}

@Composable
private fun GradientHeader() {
    val scheme = MaterialTheme.colorScheme
    val brush = Brush.horizontalGradient(
        colors = listOf(
            scheme.primaryContainer,
            scheme.tertiaryContainer,
            scheme.secondaryContainer
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "Where's That Photo",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "On-device search with MobileCLIP, Gemma captions, and MiniLM.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onPrimaryContainer.copy(alpha = 0.92f)
        )
    }
}

@Composable
private fun SearchTabContent(
    modifier: Modifier = Modifier,
    selectedPhotoCount: Int,
    indexing: Boolean,
    searching: Boolean,
    onPickPhotos: () -> Unit,
    onIndexPoc: () -> Unit,
    onClearAll: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    hasSearched: Boolean,
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),  // Shift content up when the soft keyboard appears
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pick up to $PocMaxPhotos photos. Each gets a CLIP embedding, a Gemma caption, and a caption embedding. Indexing adds or updates the selection only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPickPhotos,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Pick photos")
                    }
                    Button(
                        onClick = onIndexPoc,
                        modifier = Modifier.weight(1f),
                        enabled = selectedPhotoCount > 0 && !indexing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Index & caption")
                    }
                }
                if (selectedPhotoCount > 0) {
                    Text(
                        text = "$selectedPhotoCount selected (cap $PocMaxPhotos).",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                TextButton(
                    onClick = onClearAll,
                    enabled = !indexing,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear all indexed data")
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search indexed photos") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary
            )
        )
        Button(
            onClick = onSearch,
            enabled = query.isNotBlank() && !indexing && !searching,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text(if (searching) "Searching…" else "Search")
        }

        ResultsSection(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
            hasSearched = hasSearched,
            searching = searching,
            query = query,
            selectedPhotoCount = selectedPhotoCount,
            indexing = indexing,
            results = results,
            onResultClick = onResultClick
        )
    }
}

@Composable
private fun LibraryTabContent(
    modifier: Modifier = Modifier,
    photos: List<IndexedPhotoSummary>,
    loading: Boolean,
    onPhotoClick: (IndexedPhotoSummary) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Indexed in your library",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (photos.isEmpty() && !loading) {
                "No photos indexed yet. Use Search → pick photos → Index & caption."
            } else {
                "${photos.size} photo${if (photos.size == 1) "" else "s"} with embeddings stored on-device."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            photos.isEmpty() -> {
                EmptyStateMessage(
                    text = "Your indexed photos will appear here.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(photos, key = { it.photoId }) { photo ->
                        val ctx = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(photo.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = photo.photoId,
                            onError = { err ->
                                Log.e(TAG, "Library grid load failed photoId=${photo.photoId} uri=${photo.uri}: ${err.result.throwable}")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPhotoClick(photo) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsSection(
    modifier: Modifier = Modifier,
    hasSearched: Boolean,
    searching: Boolean,
    query: String,
    selectedPhotoCount: Int,
    indexing: Boolean,
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        when {
            searching -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                }
            }
            results.isNotEmpty() -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(results, key = { it.photoId }) { result ->
                        val ctx = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(result.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = result.photoId,
                            onError = { err ->
                                Log.e(TAG, "Search grid load failed photoId=${result.photoId} uri=${result.uri}: ${err.result.throwable}")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onResultClick(result) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            hasSearched && query.isNotBlank() -> {
                EmptyStateMessage(
                    text = "No photos matched your query.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            !hasSearched && selectedPhotoCount == 0 && !indexing -> {
                EmptyStateMessage(
                    text = "Pick photos, then index and search.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            !hasSearched -> {
                EmptyStateMessage(
                    text = "Enter a query and tap Search to find indexed photos.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotoDetailSheetContent(
    searchResult: SearchResult?,
    photoId: String,
    uri: String,
    onDismiss: () -> Unit,
    onGetCaption: suspend (String) -> String?,
    onRecaption: suspend (String) -> String
) {
    val scope = rememberCoroutineScope()
    var captionText by remember(photoId) { mutableStateOf<String?>(null) }
    var recaptioning by remember { mutableStateOf(false) }
    var recaptionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(photoId) {
        captionText = onGetCaption(photoId)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
        val ctx = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            onError = { err ->
                Log.e(TAG, "Detail modal load failed photoId=$photoId uri=$uri: ${err.result.throwable}")
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
        if (searchResult != null) {
            Text(
                "Match breakdown",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Combined score: ${"%.3f".format(searchResult.score)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "CLIP cosine: ${"%.3f".format(searchResult.clipSimilarity)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Caption embedding cosine: ${"%.3f".format(searchResult.captionEmbeddingSimilarity)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Caption text match: ${if (searchResult.lexicalMatch) "Yes" else "No"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        } else {
            Text(
                text = "Open this photo from Search to see query match scores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        }
        Text(
            "Gemma caption",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        if (recaptioning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Generating new caption with Gemma…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = captionText ?: "Loading…",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        recaptionError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(
            onClick = {
                scope.launch {
                    recaptioning = true
                    recaptionError = null
                    try {
                        captionText = onRecaption(photoId)
                    } catch (e: Exception) {
                        recaptionError = e.message ?: "Re-caption failed."
                    } finally {
                        recaptioning = false
                    }
                }
            },
            enabled = !recaptioning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(if (recaptioning) "Re-captioning…" else "Re-caption with Gemma")
        }
    }
}
