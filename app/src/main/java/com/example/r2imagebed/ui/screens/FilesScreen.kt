package com.example.r2imagebed.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.DataSource
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.r2imagebed.AppViewModel
import com.example.r2imagebed.data.R2Folder
import com.example.r2imagebed.data.R2Object
import com.example.r2imagebed.data.isLikelyImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    var newFolderName by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<R2Folder?>(null) }
    var fileToDelete by remember { mutableStateOf<R2Object?>(null) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedKeys = remember { mutableStateListOf<String>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveFolderPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedKeys.clear()
    }

    if (showMoveFolderPicker) {
        FolderPickerDialog(
            initialFolder = viewModel.currentFolder,
            allFolders = viewModel.folderSuggestions,
            onSelect = { selectedFolder ->
                val toMove = viewModel.listing.objects.filter { it.key in selectedKeys }
                viewModel.moveObjects(toMove, selectedFolder)
                selectedKeys.clear()
                isMultiSelectMode = false
                showMoveFolderPicker = false
            },
            onDismiss = { showMoveFolderPicker = false },
            onCreateFolder = { path -> viewModel.createFolder(path) }
        )
    }

    // Batch delete confirmation dialog
    if (showBatchDeleteConfirm) {
        val count = selectedKeys.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
            title = { Text("批量删除", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("确定要删除已选的 $count 个文件吗？")
                    Text("此操作不可撤销。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = viewModel.listing.objects.filter { it.key in selectedKeys }
                        viewModel.deleteObjects(toDelete)
                        selectedKeys.clear()
                        isMultiSelectMode = false
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // Folder delete confirmation dialog
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
            title = { Text("删除文件夹", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("确定要删除以下文件夹及其所有内容吗？")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = folder.prefix,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "此操作不可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteFolder(folder); folderToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("取消") }
            }
        )
    }

    // File delete confirmation dialog
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp)) },
            title = { Text("删除文件", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("确定要删除以下文件吗？")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 2
                            )
                            Text(
                                text = formatFileSizeStatic(file.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Text(
                        text = "此操作不可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteObject(file); fileToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("取消") }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            shape = RoundedCornerShape(20.dp),
            confirmButton = {
                Button(onClick = {
                    viewModel.createFolder(newFolderName, baseFolder = viewModel.currentFolder)
                    newFolderName = ""
                    showCreateDialog = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("取消") }
            },
            title = { Text("新建子目录", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("目录名称") },
                    placeholder = { Text("例如 tokyo/day-1") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "文件管理",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (isMultiSelectMode) {
                IconButton(onClick = { isMultiSelectMode = false; selectedKeys.clear() }) {
                    Icon(Icons.Default.Close, contentDescription = "退出多选", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Breadcrumb + actions bar
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Current path
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (viewModel.currentFolder.isBlank()) "根目录" else viewModel.currentFolder,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = viewModel::openParentFolder, enabled = viewModel.currentFolder.isNotBlank(), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.ArrowUpward, "上一级", modifier = Modifier.size(20.dp))
                    }
                    FilledTonalIconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.CreateNewFolder, "新建目录", modifier = Modifier.size(20.dp))
                    }
                    FilledTonalIconButton(onClick = { viewModel.browseFolder(viewModel.currentFolder) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Refresh, "刷新", modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (isMultiSelectMode) "长按选择，单击切换" else "${viewModel.listing.folders.size} 目录 · ${viewModel.listing.objects.size} 文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                if (viewModel.isLoadingListing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)))
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.listing.folders, key = { it.prefix }) { folder ->
                FolderRow(
                    folder = folder,
                    onOpen = { if (!isMultiSelectMode) viewModel.browseFolder(folder.prefix) },
                    onDelete = { if (!isMultiSelectMode) folderToDelete = folder }
                )
            }
            items(viewModel.listing.objects, key = { it.key }) { item ->
                val thumbnailUrl = remember(item.key, item.lastModified) { viewModel.thumbnailUrl(item) }
                val publicUrl = remember(item.key, viewModel.config.publicBaseUrl, viewModel.config.bucketName, viewModel.config.accountId) {
                    viewModel.directLink(item)
                }
                val isSelected = item.key in selectedKeys
                FileRow(
                    item = item,
                    thumbnailUrl = thumbnailUrl,
                    publicUrl = publicUrl,
                    isMultiSelectMode = isMultiSelectMode,
                    isSelected = isSelected,
                    onLongPress = {
                        isMultiSelectMode = true
                        if (item.key !in selectedKeys) selectedKeys.add(item.key)
                    },
                    onSelect = {
                        if (item.key in selectedKeys) selectedKeys.remove(item.key)
                        else selectedKeys.add(item.key)
                    },
                    onCopy = { copyToClipboard(context, publicUrl); viewModel.announce("直链已复制。") },
                    onDelete = { fileToDelete = item }
                )
            }
        }

        // Multi-select bottom action bar
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp)
            ) {
                val allObjects = viewModel.listing.objects
                val allSelected = allObjects.isNotEmpty() && allObjects.all { it.key in selectedKeys }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "已选 ${selectedKeys.size} 项",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                if (allSelected) selectedKeys.clear()
                                else { selectedKeys.clear(); selectedKeys.addAll(allObjects.map { it.key }) }
                            }
                        ) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedKeys.isNotEmpty()) {
                                    viewModel.refreshFolderSuggestions()
                                    showMoveFolderPicker = true
                                }
                            },
                            enabled = selectedKeys.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("移动")
                        }
                        Button(
                            onClick = { if (selectedKeys.isNotEmpty()) showBatchDeleteConfirm = true },
                            enabled = selectedKeys.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("删除 (${selectedKeys.size})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: R2Folder, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(folder.prefix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: R2Object,
    thumbnailUrl: String,
    publicUrl: String,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onSelect: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val previewCacheKey = remember(item.key, item.lastModified) {
        "${item.key}|${item.lastModified}"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isMultiSelectMode) onSelect() else onCopy() },
                onLongClick = { if (!isMultiSelectMode) onLongPress() }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail wrapper — outer Box not clipped so indicator can overflow corner
            Box(modifier = Modifier.size(64.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLikelyImage(item.key)) {
                        val request = remember(thumbnailUrl, previewCacheKey) {
                            ImageRequest.Builder(context)
                                .data(thumbnailUrl)
                                .crossfade(true)
                                .size(128, 128)
                                .diskCacheKey(previewCacheKey)
                                .memoryCacheKey(previewCacheKey)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .build()
                        }
                        val painter = rememberAsyncImagePainter(model = request)
                        when (val state = painter.state) {
                            is AsyncImagePainter.State.Success -> {
                                val revealProgress by animateFloatAsState(
                                    targetValue = 1f,
                                    animationSpec = if (state.result.dataSource == DataSource.MEMORY_CACHE) snap() else tween(420),
                                    label = "thumbnailReveal"
                                )
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Image(
                                        painter = painter,
                                        contentDescription = item.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (revealProgress < 1f) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp * (1f - revealProgress))
                                                .align(Alignment.BottomCenter)
                                        ) {}
                                    }
                                }
                            }
                            is AsyncImagePainter.State.Loading,
                            is AsyncImagePainter.State.Empty -> ThumbnailLoadingState()
                            else -> {
                                Icon(
                                    Icons.Default.Image,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    } else {
                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(28.dp))
                    }
                    // Dim overlay in multi-select when not selected
                    if (isMultiSelectMode && !isSelected) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
                    }
                }
                // Red selection badge at top-start corner
                if (isMultiSelectMode) {
                    Box(
                        modifier = Modifier
                            .padding(3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFFE53935)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                            .border(
                                width = 2.dp,
                                color = Color(0xFFE53935),
                                shape = CircleShape
                            )
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = formatFileSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = publicUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Action buttons only in normal mode
            if (!isMultiSelectMode) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, "复制直链", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailLoadingState() {
    Box(modifier = Modifier.fillMaxSize()) {
        Icon(
            Icons.Default.Image,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.Center)
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

// Alias used in top-level dialog scope (outside composable) — same implementation
private fun formatFileSizeStatic(bytes: Long) = formatFileSize(bytes)

private fun copyToClipboard(context: Context, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("snaplink-url", content))
}