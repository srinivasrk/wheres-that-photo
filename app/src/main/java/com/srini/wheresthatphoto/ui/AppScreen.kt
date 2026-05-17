package com.srini.wheresthatphoto.ui

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.srini.wheresthatphoto.data.IdentityKind
import com.srini.wheresthatphoto.indexing.IndexPhase
import com.srini.wheresthatphoto.indexing.IndexProgress
import com.srini.wheresthatphoto.people.IdentitySummary
import com.srini.wheresthatphoto.search.IndexedPhotoSummary
import java.io.File
import com.srini.wheresthatphoto.search.SearchResult
import com.srini.wheresthatphoto.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

private const val TAG = "WTP/UI"

private enum class HomeTab { Gallery, People, Search, Settings }

private data class GalleryBucket(
    val title: String,
    val photos: List<IndexedPhotoSummary>
)

private sealed class DetailTarget {
    data class FromSearch(val result: SearchResult) : DetailTarget()
    data class FromLibrary(val photo: IndexedPhotoSummary) : DetailTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    themeMode: ThemeMode,
    onToggleTheme: (systemIsDark: Boolean) -> Unit,
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
    onLoadIndexedPhotos: suspend () -> List<IndexedPhotoSummary>,
    onLoadIdentities: suspend () -> Pair<List<IdentitySummary>, List<IdentitySummary>>,
    onNameIdentity: suspend (identityId: String, displayName: String, kind: IdentityKind) -> Unit
) {
    var activeTab by rememberSaveable { mutableStateOf(HomeTab.Gallery) }
    var indexedPhotos by remember { mutableStateOf<List<IndexedPhotoSummary>>(emptyList()) }
    var loadingPhotos by remember { mutableStateOf(false) }
    var detailTarget by remember { mutableStateOf<DetailTarget?>(null) }
    var displayStatus by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<SearchResult>()) }
    var peopleIdentities by remember { mutableStateOf(emptyList<IdentitySummary>()) }
    var petsIdentities by remember { mutableStateOf(emptyList<IdentitySummary>()) }
    var namingIdentity by remember { mutableStateOf<IdentitySummary?>(null) }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var nameKindDraft by rememberSaveable { mutableStateOf(IdentityKind.PERSON.name) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nameSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    suspend fun refreshLibraryAndIdentities() {
        loadingPhotos = true
        try {
            indexedPhotos = onLoadIndexedPhotos()
            val (people, pets) = onLoadIdentities()
            peopleIdentities = people
            petsIdentities = pets
        } finally {
            loadingPhotos = false
        }
    }

    LaunchedEffect(Unit, indexing) {
        refreshLibraryAndIdentities()
    }
    LaunchedEffect(statusMessage) {
        if (statusMessage.isBlank()) {
            displayStatus = ""
        } else {
            displayStatus = statusMessage
            delay(6000)
            displayStatus = ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 430.dp)
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TopAppHeader(themeMode = themeMode, onToggleTheme = onToggleTheme)
            Spacer(Modifier.height(12.dp))

            if (!hasPermission && !permissionBannerDismissed) {
                PermissionBanner(onDismissPermissionBanner)
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.weight(1f, fill = true)) {
                when (activeTab) {
                    HomeTab.Gallery -> GalleryScreen(
                        photos = indexedPhotos,
                        loading = loadingPhotos,
                        indexing = indexing,
                        indexProgress = indexProgress,
                        onPhotoClick = { detailTarget = DetailTarget.FromLibrary(it) },
                        onPickPhotos = onPickPhotos
                    )
                    HomeTab.People -> PeoplePetsScreen(
                        people = peopleIdentities,
                        pets = petsIdentities,
                        onIdentityClick = { identity ->
                            namingIdentity = identity
                            nameDraft = identity.displayName.orEmpty()
                            nameKindDraft = identity.kind.name
                        }
                    )
                    HomeTab.Search -> SearchScreen(
                        query = query,
                        onQueryChange = { query = it },
                        searching = searching,
                        hasSearched = hasSearched,
                        results = searchResults,
                        onResultClick = { detailTarget = DetailTarget.FromSearch(it) },
                        onSearch = {
                            if (query.isNotBlank()) {
                                scope.launch {
                                    searching = true
                                    searchResults = onSearch(query)
                                    hasSearched = true
                                    searching = false
                                }
                            }
                        }
                    )
                    HomeTab.Settings -> PlaceholderSettings()
                }
            }

            Text(
                text = displayStatus,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp)
                    .padding(top = 6.dp, bottom = 4.dp)
            )

            BottomNav(activeTab = activeTab, onSelect = { activeTab = it })
        }
    }

    detailTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { detailTarget = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            when (target) {
                is DetailTarget.FromSearch -> PhotoDetailSheetContent(
                    searchResult = target.result,
                    photoId = target.result.photoId,
                    uri = target.result.uri,
                    onDismiss = { detailTarget = null },
                    onGetCaption = onGetCaption,
                    onRecaption = onRecaption
                )
                is DetailTarget.FromLibrary -> PhotoDetailSheetContent(
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

    namingIdentity?.let { identity ->
        ModalBottomSheet(
            onDismissRequest = { namingIdentity = null },
            sheetState = nameSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (identity.displayName == null) "Who is this?" else "Edit name",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = nameKindDraft == IdentityKind.PERSON.name,
                        onClick = { nameKindDraft = IdentityKind.PERSON.name },
                        label = { Text("Person") }
                    )
                    FilterChip(
                        selected = nameKindDraft == IdentityKind.PET.name,
                        onClick = { nameKindDraft = IdentityKind.PET.name },
                        label = { Text("Pet") }
                    )
                }
                Button(
                    onClick = {
                        val trimmed = nameDraft.trim()
                        if (trimmed.isEmpty()) return@Button
                        scope.launch {
                            onNameIdentity(
                                identity.identityId,
                                trimmed,
                                IdentityKind.valueOf(nameKindDraft)
                            )
                            namingIdentity = null
                            refreshLibraryAndIdentities()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank()
                ) {
                    Text("Save")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TopAppHeader(themeMode: ThemeMode, onToggleTheme: (Boolean) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIcon(Icons.Filled.Tune, null)
            Text(
                "Where's That Photo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            ThemeToggleButton(themeMode = themeMode, onToggle = onToggleTheme)
        }
    }
}

@Composable
private fun PermissionBanner(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp))
            Text(
                "READ_MEDIA_IMAGES not granted. Optional for many devices.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun GalleryScreen(
    photos: List<IndexedPhotoSummary>,
    loading: Boolean,
    indexing: Boolean,
    indexProgress: IndexProgress?,
    onPhotoClick: (IndexedPhotoSummary) -> Unit,
    onPickPhotos: () -> Unit
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchPill(
                    placeholder = "Try \"sunset at the beach\"",
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
                UploadButton(onPickPhotos = onPickPhotos)
            }
        }
        if (indexing) {
            item { IndexingBanner(indexProgress) }
        }

        val buckets = groupPhotosByRecency(photos)
        if (buckets.isEmpty()) {
            item {
                Text(
                    "No indexed photos yet. Tap Upload to add photos.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(buckets) { bucket ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = bucket.title,
                        count = "${bucket.photos.size} photo${if (bucket.photos.size == 1) "" else "s"}"
                    )
                    bucket.photos.chunked(4).forEach { chunk ->
                        MosaicRow(chunk, onPhotoClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPill(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun UploadButton(onPickPhotos: () -> Unit) {
    Button(
        onClick = onPickPhotos,
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Upload", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String, count: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(count, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MosaicRow(photos: List<IndexedPhotoSummary>, onPhotoClick: (IndexedPhotoSummary) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        val left = photos.getOrNull(0)
        val rightTop = photos.getOrNull(1)
        val rightBottom = photos.getOrNull(2)
        val extra = photos.getOrNull(3)

        if (left != null) MosaicImage(left, Modifier.weight(1.6f).aspectRatio(1.25f), onPhotoClick)
        else PlaceholderTile(Modifier.weight(1.6f).aspectRatio(1.25f))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rightTop != null) MosaicImage(rightTop, Modifier.aspectRatio(1.25f), onPhotoClick)
            else PlaceholderTile(Modifier.aspectRatio(1.25f))
            if (rightBottom != null) MosaicImage(rightBottom, Modifier.aspectRatio(1.25f), onPhotoClick)
            else PlaceholderTile(Modifier.aspectRatio(1.25f))
        }
    }
    Spacer(Modifier.height(8.dp))
    val bottomPhoto = photos.getOrNull(3)
    if (bottomPhoto != null) {
        MosaicImage(bottomPhoto, Modifier.fillMaxWidth(0.34f).aspectRatio(1f), onPhotoClick)
    }
}

@Composable
private fun MosaicImage(photo: IndexedPhotoSummary, modifier: Modifier, onClick: (IndexedPhotoSummary) -> Unit) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(photo.uri).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick(photo) }
    )
}

@Composable
private fun PlaceholderTile(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun IndexingBanner(progress: IndexProgress?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val ctx = LocalContext.current
                if (progress?.currentUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(progress.currentUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "INDEXING ON-DEVICE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = progress?.currentUri
                            ?.substringAfterLast('/')
                            ?.substringBefore('?')
                            ?.ifBlank { "Preparing photos..." }
                            ?: "Preparing photos...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (progress != null) {
                        val phaseText = when (progress.phase) {
                            IndexPhase.Embedding -> "Computing visual embedding..."
                            IndexPhase.FaceDetection -> progress.captionText
                                ?: "Detecting faces and pets…"
                            IndexPhase.Captioning -> "Generating Gemma caption..."
                        }
                        Text(
                            text = phaseText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = {
                    if (progress == null || progress.total <= 0) 0f
                    else progress.current.toFloat() / progress.total.toFloat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun PeoplePetsScreen(
    people: List<IdentitySummary>,
    pets: List<IdentitySummary>,
    onIdentityClick: (IdentitySummary) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("People\n& Pets", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, lineHeight = MaterialTheme.typography.displaySmall.lineHeight)
            Text(
                "Faces and pets detected on-device. Tap a circle to add a name.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { PeopleSection("People", people, onIdentityClick) }
        item { PeopleSection("Pets", pets, onIdentityClick) }
    }
}

@Composable
private fun PeopleSection(
    label: String,
    identities: List<IdentitySummary>,
    onIdentityClick: (IdentitySummary) -> Unit
) {
    val display = identities.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label · ${identities.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (identities.isNotEmpty()) {
                Text("View all", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (display.isEmpty()) {
            Text(
                "Index photos with faces to see $label here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val rows = max(1, display.chunked(3).size)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                userScrollEnabled = false,
                modifier = Modifier.height((rows * 160).dp)
            ) {
                items(display, key = { it.identityId }) { identity ->
                    CircleIdentityCard(identity = identity, onClick = { onIdentityClick(identity) })
                }
            }
        }
    }
}

@Composable
private fun CircleIdentityCard(identity: IdentitySummary, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val named = identity.displayName != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .clickable(onClick = onClick)
    ) {
        if (named && identity.thumbnailPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(File(identity.thumbnailPath)).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
            )
        } else if (identity.thumbnailPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(File(identity.thumbnailPath)).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier.size(110.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) { CircleIcon(Icons.Outlined.PersonAdd, null) }
        }
        if (named) {
            Text(
                identity.displayName!!,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "${identity.photoCount} photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    "Who is this?",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    hasSearched: Boolean,
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    onSearch: () -> Unit
) {
    val chips = listOf(
        Triple(Icons.Filled.History, "All time", true),
        Triple(Icons.Outlined.LocationOn, "Location", false),
        Triple(Icons.Filled.Groups, "People", false),
        Triple(Icons.Filled.CameraAlt, "Camera", false)
    )
    val recents = listOf("sunset at the beach", "mom in the garden", "golden retriever in the snow", "birthday cake", "hiking in autumn")

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null)
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        placeholder = { Text("Describe a moment: \"Rex in the snow\"") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() })
                    )
                    IconButton(
                        onClick = onSearch,
                        enabled = query.isNotBlank() && !searching,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { (icon, label, active) ->
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, modifier = Modifier.size(16.dp))
                            Text(label, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            Text("RECENT SEARCHES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(recents) { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Icon(Icons.Outlined.NorthWest, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WbTwilight, null, tint = MaterialTheme.colorScheme.primary)
                        Text("TRY ASKING", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                    listOf(
                        "\"Photos from my trip to Big Sur\"",
                        "\"Everyone smiling at the camera\"",
                        "\"Where did I photograph that blue door?\""
                    ).forEach {
                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface) {
                            Text(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp))
                        }
                    }
                }
            }
        }
        if (searching) {
            item {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (hasSearched && results.isNotEmpty()) {
            item { Text("Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(results, key = { it.photoId }) { result ->
                        SearchResultItem(result = result, onClick = { onResultClick(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(result.uri).crossfade(true).build(),
        contentDescription = result.photoId,
        onError = { err ->
            Log.e(TAG, "Search thumb failed photoId=${result.photoId}: ${err.result.throwable}")
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun BottomNav(activeTab: HomeTab, onSelect: (HomeTab) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavItem(Icons.Filled.PhotoLibrary, "Gallery", activeTab == HomeTab.Gallery) { onSelect(HomeTab.Gallery) }
            NavItem(Icons.Filled.Groups, "People", activeTab == HomeTab.People) { onSelect(HomeTab.People) }
            NavItem(Icons.Filled.Search, "Search", activeTab == HomeTab.Search) { onSelect(HomeTab.Search) }
            NavItem(Icons.Filled.Settings, "Settings", activeTab == HomeTab.Settings) { onSelect(HomeTab.Settings) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, description: String?) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlaceholderSettings() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Settings screen is intentionally skipped for this pass.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun groupPhotosByRecency(photos: List<IndexedPhotoSummary>): List<GalleryBucket> {
    if (photos.isEmpty()) return emptyList()

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    fun bucketFor(photo: IndexedPhotoSummary): String {
        val ts = photo.captionedAt ?: photo.indexedAt ?: return "Earlier"
        val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(date, today)
        return when {
            daysAgo == 0L -> "Today"
            daysAgo == 1L -> "Yesterday"
            daysAgo in 2L..6L -> "This Week"
            date.year == today.year && date.monthValue == today.monthValue -> "This Month"
            else -> "Earlier"
        }
    }

    val grouped = linkedMapOf(
        "Today" to mutableListOf<IndexedPhotoSummary>(),
        "Yesterday" to mutableListOf<IndexedPhotoSummary>(),
        "This Week" to mutableListOf<IndexedPhotoSummary>(),
        "This Month" to mutableListOf<IndexedPhotoSummary>(),
        "Earlier" to mutableListOf<IndexedPhotoSummary>()
    )

    photos.forEach { photo ->
        grouped.getValue(bucketFor(photo)).add(photo)
    }

    return grouped
        .filterValues { it.isNotEmpty() }
        .map { (title, items) -> GalleryBucket(title, items) }
}


// ─────────────────────────────────────────────────────────────────────────────
// Photo detail bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

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
            TextButton(onClick = onDismiss) { Text("Close") }
        }

        // Full photo
        val ctx = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            onError = { err ->
                Log.e(TAG, "Detail modal failed photoId=$photoId: ${err.result.throwable}")
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )

        // ── Match breakdown ───────────────────────────────────────────────
        if (searchResult != null) {
            Text(
                "Match breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Combined score as large text + bar
            ScoreRow(
                label = "Combined score",
                value = searchResult.score,
                barColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurface,
                bold = true
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 2.dp)
            )

            // Sub-scores
            ScoreRow(
                label = "CLIP visual",
                value = searchResult.clipSimilarity,
                barColor = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ScoreRow(
                label = "Caption embedding",
                value = searchResult.captionEmbeddingSimilarity,
                barColor = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                textColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Lexical chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Caption text match",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                val chipColor = if (searchResult.lexicalMatch)
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
                val chipTextColor = if (searchResult.lexicalMatch)
                    MaterialTheme.colorScheme.onSecondaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(chipColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (searchResult.lexicalMatch) "✓ Match" else "No match",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = chipTextColor
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        } else {
            Text(
                text = "Open this photo from Search to see query match scores.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        }

        // ── Gemma caption ─────────────────────────────────────────────────
        Text(
            "Gemma caption",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary
        )
        if (recaptioning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
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
            shape = RoundedCornerShape(100),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(if (recaptioning) "Re-captioning…" else "Re-caption with Gemma")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Score row helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScoreRow(
    label: String,
    value: Float,
    barColor: Color,
    trackColor: Color,
    textColor: Color,
    bold: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "scoreAnim"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = if (bold) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor
            )
            Text(
                text = "%.3f".format(value),
                style = if (bold) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
                color = barColor
            )
        }
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (bold) 6.dp else 4.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = trackColor
        )
    }
}
