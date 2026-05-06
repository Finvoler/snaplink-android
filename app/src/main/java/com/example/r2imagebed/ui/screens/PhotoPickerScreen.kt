package com.example.r2imagebed.ui.screens

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun PhotoPickerScreen(
    selectedUris: List<Uri>,
    onDismiss: () -> Unit,
    onSelectionChange: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var activeTab by remember { mutableStateOf(PhotoPickerTab.Photos) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var photos by remember { mutableStateOf<List<LocalPhoto>>(emptyList()) }
    val selectedPhotoIds = remember { mutableStateListOf<Long>() }

    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) {
        isLoading = true
        photos = kotlinx.coroutines.withContext(Dispatchers.IO) {
            queryLocalPhotos(context)
        }
        isLoading = false
    }

    val albums = remember(photos) { buildAlbums(photos) }
    val normalizedQuery = searchQuery.trim()
    val visibleAlbums = remember(albums, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            albums
        } else {
            albums.filter { album ->
                album.name.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val filteredPhotos = remember(photos, selectedAlbumId, normalizedQuery) {
        photos.filter { photo ->
            val matchesAlbum = selectedAlbumId == null || photo.bucketId == selectedAlbumId
            val matchesSearch = normalizedQuery.isBlank() || buildSearchText(photo).contains(normalizedQuery, ignoreCase = true)
            matchesAlbum && matchesSearch
        }
    }
    val feed = remember(filteredPhotos) { buildPhotoFeed(filteredPhotos) }
    val photoById = remember(photos) { photos.associateBy { it.id } }
    val selectedAlbumName = remember(selectedAlbumId, albums) {
        albums.firstOrNull { it.id == selectedAlbumId }?.name
    }

    LaunchedEffect(selectedUris, photoById) {
        val selectedIds = selectedUris.mapNotNull { uri ->
            photoById.values.firstOrNull { it.uri == uri }?.id
        }
        selectedPhotoIds.clear()
        selectedPhotoIds.addAll(selectedIds)
    }

    LaunchedEffect(selectedPhotoIds.toList(), photoById) {
        onSelectionChange(selectedPhotoIds.mapNotNull { photoById[it]?.uri })
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "选择图片",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "按日期浏览，按相册筛选，支持批量上传",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("搜索日期或图片名") },
                placeholder = { Text("例如 2026-05 或 IMG_1024") }
            )

            if (selectedAlbumName != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = selectedAlbumName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "当前相册",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                selectedAlbumId = null
                                activeTab = PhotoPickerTab.Albums
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出相册", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("退出")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已选 ${selectedPhotoIds.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TabRow(selectedTabIndex = activeTab.ordinal) {
                PhotoPickerTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tab.label) },
                        icon = {
                            Icon(
                                imageVector = if (tab == PhotoPickerTab.Photos) Icons.Default.PhotoLibrary else Icons.Default.Collections,
                                contentDescription = tab.label
                            )
                        }
                    )
                }
            }

            Crossfade(targetState = activeTab, modifier = Modifier.weight(1f), label = "pickerTab") { tab ->
                when (tab) {
                    PhotoPickerTab.Photos -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isLoading) {
                                PickerLoadingState()
                            } else if (feed.items.isEmpty()) {
                                PickerEmptyState(
                                    title = "没有找到匹配图片",
                                    subtitle = if (photos.isEmpty()) "设备里暂时没有可读取的图片。" else "可以换个日期、名称或相册再试。"
                                )
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(feed.items, key = { item -> item.key }) { item ->
                                        when (item) {
                                            is PhotoFeedItem.Header -> {
                                                Text(
                                                    text = item.label,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                                )
                                            }

                                            is PhotoFeedItem.Row -> {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    item.photos.forEach { photo ->
                                                        PhotoTile(
                                                            photo = photo,
                                                            selected = selectedPhotoIds.contains(photo.id),
                                                            onToggle = {
                                                                if (selectedPhotoIds.contains(photo.id)) {
                                                                    selectedPhotoIds.remove(photo.id)
                                                                } else {
                                                                    selectedPhotoIds.add(photo.id)
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    repeat(3 - item.photos.size) {
                                                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (feed.anchors.size > 1) {
                                    FastDateScroller(
                                        anchors = feed.anchors,
                                        listState = listState,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .offset(x = 20.dp)
                                    )
                                }
                            }
                        }
                    }

                    PhotoPickerTab.Albums -> {
                        if (isLoading) {
                            PickerLoadingState()
                        } else if (visibleAlbums.isEmpty()) {
                            PickerEmptyState(
                                title = "没有找到匹配相册",
                                subtitle = "可以尝试搜索其他相册名称。"
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    AlbumRow(
                                        album = PhotoAlbum(
                                            id = "__all__",
                                            name = "全部照片",
                                            count = photos.size,
                                            coverUri = photos.firstOrNull()?.uri,
                                            latestTakenAt = photos.firstOrNull()?.dateTakenMillis ?: 0L
                                        ),
                                        selected = selectedAlbumId == null,
                                        onClick = {
                                            selectedAlbumId = null
                                            activeTab = PhotoPickerTab.Photos
                                        }
                                    )
                                }
                                items(visibleAlbums, key = { it.id }) { album ->
                                    AlbumRow(
                                        album = album,
                                        selected = selectedAlbumId == album.id,
                                        onClick = {
                                            selectedAlbumId = album.id
                                            activeTab = PhotoPickerTab.Photos
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(
    photo: LocalPhoto,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.96f else 1f,
        animationSpec = spring(stiffness = 450f),
        label = "photoTileScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "photoTileBorder"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
        )
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.28f),
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp)
                .align(Alignment.TopEnd)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(topStart = 12.dp),
            color = Color.Black.copy(alpha = 0.42f),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text(
                text = shortDate(photo.dateTakenMillis),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AlbumRow(
    album: PhotoAlbum,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverUri != null) {
                    AsyncImage(
                        model = album.coverUri,
                        contentDescription = album.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.count} 张图片 · ${fullDate(album.latestTakenAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FastDateScroller(
    anchors: List<DateAnchor>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var trackHeight by remember { mutableIntStateOf(1) }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    fun selectAnchor(y: Float) {
        if (anchors.isEmpty() || trackHeight <= 0) return
        val fraction = (y / trackHeight.toFloat()).coerceIn(0f, 1f)
        val index = (fraction * anchors.lastIndex).toInt().coerceIn(0, anchors.lastIndex)
        val anchor = anchors[index]
        activeLabel = anchor.label
        coroutineScope.launch {
            listState.scrollToItem(anchor.listIndex)
        }
    }

    LaunchedEffect(activeLabel, isDragging) {
        if (activeLabel != null && !isDragging) {
            delay(700)
            activeLabel = null
        }
    }

    Box(
        modifier = modifier
            .width(34.dp)
            .fillMaxHeight()
            .navigationBarsPadding()
    ) {
        if (activeLabel != null) {
            val labelLines = remember(activeLabel) { activeLabel.orEmpty().toScrollerLabelLines() }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-44).dp)
                    .requiredWidth(112.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    labelLines.forEachIndexed { index, line ->
                        Text(
                            text = line,
                            style = if (index == 0 && labelLines.size > 1) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            fontWeight = if (index == 0 && labelLines.size > 1) FontWeight.Medium else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            textAlign = TextAlign.Center,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.74f)
                .width(14.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                .onSizeChanged { trackHeight = it.height }
                .pointerInput(anchors, trackHeight) {
                    detectTapGestures { offset -> selectAnchor(offset.y) }
                }
                .pointerInput(anchors, trackHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> isDragging = true; selectAnchor(offset.y) },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            selectAnchor(change.position.y)
                            change.consume()
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                List(minOf(anchors.size, 11)) { it }.forEach { _ ->
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在读取本地图片…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PickerEmptyState(
    title: String,
    subtitle: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun queryLocalPhotos(context: Context): List<LocalPhoto> {
    val resolver = context.contentResolver
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    )
    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

    return buildList {
        resolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex).orEmpty().ifBlank { "IMG_$id" }
                val dateTaken = cursor.getLong(dateTakenIndex)
                val dateAddedSeconds = cursor.getLong(dateAddedIndex)
                val bucketId = cursor.getString(bucketIdIndex).orEmpty().ifBlank { "unknown" }
                val bucketName = cursor.getString(bucketNameIndex).orEmpty().ifBlank { "未命名相册" }
                val takenAt = when {
                    dateTaken > 0L -> dateTaken
                    dateAddedSeconds > 0L -> dateAddedSeconds * 1000L
                    else -> 0L
                }
                add(
                    LocalPhoto(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name,
                        dateTakenMillis = takenAt,
                        bucketId = bucketId,
                        bucketName = bucketName
                    )
                )
            }
        }
    }
}

private fun buildAlbums(photos: List<LocalPhoto>): List<PhotoAlbum> {
    return photos
        .groupBy { it.bucketId }
        .mapNotNull { (bucketId, bucketPhotos) ->
            val cover = bucketPhotos.firstOrNull() ?: return@mapNotNull null
            PhotoAlbum(
                id = bucketId,
                name = cover.bucketName,
                count = bucketPhotos.size,
                coverUri = cover.uri,
                latestTakenAt = bucketPhotos.maxOfOrNull { it.dateTakenMillis } ?: 0L
            )
        }
        .sortedByDescending { it.latestTakenAt }
}

private fun buildPhotoFeed(photos: List<LocalPhoto>): PhotoFeed {
    if (photos.isEmpty()) return PhotoFeed(emptyList(), emptyList())

    val grouped = linkedMapOf<String, MutableList<LocalPhoto>>()
    photos.forEach { photo ->
        val key = fullDate(photo.dateTakenMillis)
        grouped.getOrPut(key) { mutableListOf() }.add(photo)
    }

    val items = mutableListOf<PhotoFeedItem>()
    val anchors = mutableListOf<DateAnchor>()
    var listIndex = 0
    grouped.forEach { (label, groupedPhotos) ->
        anchors += DateAnchor(label = label, listIndex = listIndex)
        items += PhotoFeedItem.Header(label)
        listIndex += 1
        groupedPhotos.chunked(3).forEach { rowPhotos ->
            items += PhotoFeedItem.Row(rowPhotos)
            listIndex += 1
        }
    }
    return PhotoFeed(items = items, anchors = anchors)
}

private fun buildSearchText(photo: LocalPhoto): String {
    val date = photoLocalDate(photo.dateTakenMillis)
    return listOf(
        photo.name,
        photo.bucketName,
        date.format(DateTimeFormatter.ISO_LOCAL_DATE),
        date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.CHINA)),
        date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
        date.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)),
        date.format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.CHINA))
    ).joinToString(" ")
}

private fun photoLocalDate(timestampMillis: Long): LocalDate {
    val safeTimestamp = if (timestampMillis > 0L) timestampMillis else System.currentTimeMillis()
    return Instant.ofEpochMilli(safeTimestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

private fun shortDate(timestampMillis: Long): String {
    return photoLocalDate(timestampMillis).format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA))
}

private fun fullDate(timestampMillis: Long): String {
    val date = photoLocalDate(timestampMillis)
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    }
}

private fun String.toScrollerLabelLines(): List<String> {
    if (!contains('年') || !contains('月') || !contains('日')) {
        return listOf(this)
    }
    val yearPart = substringBefore('年') + "年"
    val monthDayPart = substringAfter('年')
    return listOf(yearPart, monthDayPart)
}

private enum class PhotoPickerTab(val label: String) {
    Photos("照片"),
    Albums("相册")
}

private data class LocalPhoto(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTakenMillis: Long,
    val bucketId: String,
    val bucketName: String
)

private data class PhotoAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: Uri?,
    val latestTakenAt: Long
)

private data class PhotoFeed(
    val items: List<PhotoFeedItem>,
    val anchors: List<DateAnchor>
)

private sealed interface PhotoFeedItem {
    val key: String

    data class Header(val label: String) : PhotoFeedItem {
        override val key: String = "header:$label"
    }

    data class Row(val photos: List<LocalPhoto>) : PhotoFeedItem {
        override val key: String = photos.joinToString(prefix = "row:", separator = "|") { it.id.toString() }
    }
}

private data class DateAnchor(
    val label: String,
    val listIndex: Int
)