package com.example.r2imagebed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FolderPickerDialog(
    initialFolder: String,
    allFolders: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateFolder: (path: String) -> Unit
) {
    var currentPath by remember { mutableStateOf(initialFolder) }
    var showCreateInput by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val directChildren = remember(currentPath, allFolders) {
        val prefix = currentPath
        allFolders
            .filter { folder ->
                folder.startsWith(prefix) && folder != prefix
            }
            .map { folder ->
                val relative = folder.removePrefix(prefix).trimEnd('/')
                if (relative.contains('/')) {
                    // Only direct children (one level deep)
                    prefix + relative.substringBefore('/') + "/"
                } else {
                    folder
                }
            }
            .distinct()
            .sorted()
    }

    // Breadcrumb segments
    val segments = remember(currentPath) {
        if (currentPath.isBlank()) emptyList()
        else currentPath.trimEnd('/').split('/')
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    text = "选择目标目录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Breadcrumb bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = { currentPath = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "根目录",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    segments.forEachIndexed { idx, seg ->
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val pathUpTo = segments.take(idx + 1).joinToString("/") + "/"
                        Text(
                            text = seg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (idx == segments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (idx == segments.lastIndex)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { currentPath = pathUpTo },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Back button (if not root)
                if (currentPath.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val parent = currentPath.trimEnd('/').substringBeforeLast('/', "")
                                currentPath = if (parent.isBlank()) "" else "$parent/"
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回上级",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "返回上级",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                if (directChildren.isEmpty() && currentPath.isBlank()) {
                    Text(
                        text = "暂无子目录，可在此处创建新目录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(directChildren) { folder ->
                        val folderName = folder.trimEnd('/').substringAfterLast('/')
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentPath = folder }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Create new folder inline
                if (showCreateInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("新目录名") },
                            singleLine = true,
                            placeholder = { Text("例如 photos") }
                        )
                        IconButton(
                            onClick = {
                                if (newFolderName.isNotBlank()) {
                                    val fullPath = if (currentPath.isBlank()) {
                                        "${newFolderName.trim()}/"
                                    } else {
                                        "$currentPath${newFolderName.trim()}/"
                                    }
                                    onCreateFolder(fullPath)
                                    currentPath = fullPath
                                    newFolderName = ""
                                    showCreateInput = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "确认创建")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showCreateInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("在此创建新目录")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelect(currentPath) }) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (currentPath.isBlank()) "选择根目录" else "选择 「${currentPath.trimEnd('/').substringAfterLast('/')}」")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
