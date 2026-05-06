package com.example.r2imagebed

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.r2imagebed.data.ConfigStorage
import com.example.r2imagebed.data.FolderListing
import com.example.r2imagebed.data.R2Config
import com.example.r2imagebed.data.R2Folder
import com.example.r2imagebed.data.R2Object
import com.example.r2imagebed.data.R2Repository
import com.example.r2imagebed.data.buildPublicUrl
import com.example.r2imagebed.data.guessMimeType
import com.example.r2imagebed.data.normalizeFolderPath
import com.example.r2imagebed.data.parentFolder
import com.example.r2imagebed.data.resolveChildFolder
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = ConfigStorage(application)
    private val repository = R2Repository()
    private val thumbnailUrlCache = linkedMapOf<String, CachedThumbnailUrl>()
    private var globalPreloadJob: Job? = null

    private companion object {
        const val BUCKET_CAPACITY_BYTES = 10L * 1024 * 1024 * 1024
        const val THUMBNAIL_URL_EXPIRES_IN_SECONDS = 3600
        const val THUMBNAIL_URL_REFRESH_SKEW_MILLIS = 2 * 60 * 1000L
    }

    var config by mutableStateOf(storage.load())
        private set

    var currentFolder by mutableStateOf("")
        private set

    var folderSuggestions by mutableStateOf(emptyList<String>())
        private set

    var listing by mutableStateOf(FolderListing(prefix = ""))
        private set

    var uploadFolderInput by mutableStateOf("")
        private set

    var bannerMessage by mutableStateOf<String?>(null)
        private set

    var isLoadingListing by mutableStateOf(false)
        private set

    var isRefreshingFolders by mutableStateOf(false)
        private set

    var isSavingConfig by mutableStateOf(false)
        private set

    var isUploading by mutableStateOf(false)
        private set

    var uploadProgressLabel by mutableStateOf<String?>(null)
        private set

    var uploadFileProgress by mutableStateOf(0f)
        private set

    var uploadCurrentFileName by mutableStateOf<String?>(null)
        private set

    var lastUploadedUrl by mutableStateOf<String?>(null)
        private set

    var bucketUsedBytes by mutableStateOf(0L)
        private set

    var isLoadingBucketUsage by mutableStateOf(false)
        private set

    var isPhotoPickerOpen by mutableStateOf(false)
        private set

    var pendingPhotoPickerUris by mutableStateOf<List<Uri>>(emptyList())
        private set

    init {
        if (config.isComplete()) {
            refreshAll()
        }
    }

    fun updateAccountId(value: String) {
        config = config.copy(accountId = value)
    }

    fun updateAccessKeyId(value: String) {
        config = config.copy(accessKeyId = value)
    }

    fun updateSecretAccessKey(value: String) {
        config = config.copy(secretAccessKey = value)
    }

    fun updateBucketName(value: String) {
        config = config.copy(bucketName = value)
    }

    fun updatePublicBaseUrl(value: String) {
        config = config.copy(publicBaseUrl = value)
    }

    fun updateUploadFolderInput(value: String) {
        uploadFolderInput = value.replace('\\', '/')
    }

    fun useCurrentFolderForUpload() {
        uploadFolderInput = currentFolder
    }

    fun dismissBanner() {
        bannerMessage = null
    }

    fun announce(message: String) {
        bannerMessage = message
    }

    fun clearLastUploadedUrl() {
        lastUploadedUrl = null
    }

    fun saveConfig() {
        val normalized = config.normalized()
        if (!normalized.isComplete()) {
            bannerMessage = "请先填写完整的 R2 配置。"
            return
        }
        isSavingConfig = true
        viewModelScope.launch {
            storage.save(normalized)
            config = normalized
            clearThumbnailUrlCache()
            isSavingConfig = false
            bannerMessage = "配置已保存。"
            refreshAll()
        }
    }

    fun testConnection() {
        val normalized = validatedConfig() ?: return
        isSavingConfig = true
        viewModelScope.launch {
            runCatching {
                repository.testConnection(normalized)
            }.onSuccess {
                bannerMessage = "连接成功，Bucket 可访问。"
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
            }
            isSavingConfig = false
        }
    }

    fun refreshAll() {
        refreshFolderSuggestions()
        refreshBucketUsage()
        browseFolder(currentFolder)
    }

    fun refreshFolderSuggestions() {
        val normalized = validatedConfig() ?: return
        isRefreshingFolders = true
        viewModelScope.launch {
            runCatching {
                repository.listAllFolders(normalized)
            }.onSuccess { folders ->
                folderSuggestions = folders
                reconcileUploadFolder(folders)
                startGlobalPreload(folders)
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
            }
            isRefreshingFolders = false
        }
    }

    fun refreshBucketUsage() {
        val normalized = validatedConfig() ?: return
        isLoadingBucketUsage = true
        viewModelScope.launch {
            runCatching {
                repository.totalObjectBytes(normalized)
            }.onSuccess { totalBytes ->
                bucketUsedBytes = totalBytes
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
            }
            isLoadingBucketUsage = false
        }
    }

    fun openPhotoPicker() {
        isPhotoPickerOpen = true
    }

    fun closePhotoPicker() {
        isPhotoPickerOpen = false
        pendingPhotoPickerUris = emptyList()
    }

    fun updatePhotoPickerSelection(uris: List<Uri>) {
        pendingPhotoPickerUris = uris
    }

    fun submitPhotoPickerSelection(context: Context) {
        val selectedUris = pendingPhotoPickerUris
        if (selectedUris.isEmpty()) {
            bannerMessage = "请先选择至少一张图片。"
            return
        }
        isPhotoPickerOpen = false
        pendingPhotoPickerUris = emptyList()
        uploadFromUris(context, selectedUris)
    }

    fun browseFolder(prefix: String) {
        val normalized = validatedConfig() ?: return
        val target = normalizeFolderPath(prefix)
        isLoadingListing = true
        viewModelScope.launch {
            runCatching {
                repository.listFolder(normalized, target)
            }.onSuccess { result ->
                currentFolder = result.prefix
                listing = result
                preloadObjects(result.objects)
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
            }
            isLoadingListing = false
        }
    }

    fun openParentFolder() {
        browseFolder(parentFolder(currentFolder))
    }

    fun createFolder(folderPath: String, baseFolder: String? = null) {
        val normalized = validatedConfig() ?: return
        val resolvedFolder = if (baseFolder == null) {
            normalizeFolderPath(folderPath)
        } else {
            resolveChildFolder(baseFolder, folderPath)
        }
        if (resolvedFolder.isBlank()) {
            bannerMessage = "请输入要创建的文件夹路径。"
            return
        }
        isLoadingListing = true
        viewModelScope.launch {
            runCatching {
                repository.createFolder(normalized, resolvedFolder)
            }.onSuccess {
                bannerMessage = "文件夹已创建：$resolvedFolder"
                refreshAll()
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
                isLoadingListing = false
            }
        }
    }

    fun uploadFromUri(context: Context, sourceUri: Uri) {
        uploadFromUris(context, listOf(sourceUri))
    }

    fun uploadFromUris(context: Context, sourceUris: List<Uri>) {
        val normalized = validatedConfig() ?: return
        if (sourceUris.isEmpty()) {
            bannerMessage = "未选择任何图片。"
            return
        }
        isUploading = true
        uploadFileProgress = 0f
        uploadCurrentFileName = null
        uploadProgressLabel = if (sourceUris.size == 1) "准备上传..." else "准备批量上传 0/${sourceUris.size}"
        viewModelScope.launch {
            runCatching {
                var latestUrl: String? = null
                sourceUris.forEachIndexed { index, sourceUri ->
                    val payload = readUploadPayload(context, sourceUri)
                    uploadCurrentFileName = payload.fileName
                    uploadFileProgress = 0f
                    uploadProgressLabel = if (sourceUris.size == 1) {
                        "上传中: ${payload.fileName}"
                    } else {
                        "${index + 1}/${sourceUris.size}: ${payload.fileName}"
                    }
                    latestUrl = repository.uploadObject(
                        config = normalized,
                        folderPath = uploadFolderInput,
                        fileName = payload.fileName,
                        bytes = payload.bytes,
                        contentType = payload.contentType,
                        onProgress = { written, total ->
                            uploadFileProgress = if (total > 0) written.toFloat() / total else 0f
                        }
                    )
                    uploadFileProgress = 1f
                }
                latestUrl
            }.onSuccess { publicUrl ->
                lastUploadedUrl = publicUrl
                refreshAll()
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
            }
            isUploading = false
            uploadProgressLabel = null
            uploadCurrentFileName = null
            uploadFileProgress = 0f
        }
    }

    fun deleteObject(item: R2Object) {
        val normalized = validatedConfig() ?: return
        isLoadingListing = true
        viewModelScope.launch {
            runCatching {
                repository.deleteObject(normalized, item.key)
            }.onSuccess {
                bannerMessage = "已删除文件：${item.name}"
                refreshFolderSuggestions()
                refreshBucketUsage()
                browseFolder(currentFolder)
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
                isLoadingListing = false
            }
        }
    }

    fun deleteObjects(items: List<R2Object>) {
        if (items.isEmpty()) return
        val normalized = validatedConfig() ?: return
        isLoadingListing = true
        viewModelScope.launch {
            var failCount = 0
            withContext(Dispatchers.IO) {
                items.forEach { item ->
                    runCatching { repository.deleteObject(normalized, item.key) }
                        .onFailure { failCount++ }
                }
            }
            bannerMessage = if (failCount == 0) "已删除 ${items.size} 个文件。"
                            else "删除完成，${failCount} 个文件失败。"
            refreshFolderSuggestions()
            refreshBucketUsage()
            browseFolder(currentFolder)
        }
    }

    fun deleteFolder(folder: R2Folder) {
        val normalized = validatedConfig() ?: return
        isLoadingListing = true
        viewModelScope.launch {
            runCatching {
                repository.deleteFolder(normalized, folder.prefix)
            }.onSuccess {
                bannerMessage = "已删除文件夹：${folder.prefix}"
                handleDeletedFolderTarget(folder.prefix)
                refreshFolderSuggestions()
                refreshBucketUsage()
                if (currentFolder.startsWith(folder.prefix)) {
                    browseFolder(parentFolder(folder.prefix))
                } else {
                    browseFolder(currentFolder)
                }
            }.onFailure { throwable ->
                bannerMessage = throwable.readableMessage()
                isLoadingListing = false
            }
        }
    }

    fun directLink(item: R2Object): String {
        return buildPublicUrl(config.normalized(), item.key)
    }

    fun thumbnailUrl(item: R2Object): String {
        val normalizedConfig = config.normalized()
        val cacheKey = listOf(normalizedConfig.accountId, normalizedConfig.bucketName, item.key)
            .joinToString("|")
        val now = System.currentTimeMillis()

        thumbnailUrlCache[cacheKey]
            ?.takeIf { it.expiresAtMillis > now }
            ?.let { return it.url }

        return try {
            val url = repository.presignGetUrl(
                normalizedConfig,
                item.key,
                expiresInSeconds = THUMBNAIL_URL_EXPIRES_IN_SECONDS
            )
            thumbnailUrlCache[cacheKey] = CachedThumbnailUrl(
                url = url,
                expiresAtMillis = now + THUMBNAIL_URL_EXPIRES_IN_SECONDS * 1000L - THUMBNAIL_URL_REFRESH_SKEW_MILLIS
            )
            url
        } catch (e: Exception) {
            buildPublicUrl(normalizedConfig, item.key)
        }
    }

    private fun clearThumbnailUrlCache() {
        thumbnailUrlCache.clear()
        globalPreloadJob?.cancel()
        globalPreloadJob = null
    }

    private fun isImageKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") ||
            lower.endsWith(".heic") || lower.endsWith(".heif") || lower.endsWith(".avif")
    }

    // Enqueue Coil preload for image objects not yet cached. Must be called on Main thread.
    private fun preloadObjects(objects: List<R2Object>) {
        val normalizedConfig = config.normalized()
        if (!normalizedConfig.isComplete()) return
        val context = getApplication<Application>()
        val imageLoader = context.imageLoader
        objects.forEach { obj ->
            if (!isImageKey(obj.key)) return@forEach
            val url = try { thumbnailUrl(obj) } catch (e: Exception) { return@forEach }
            val cacheKey = "${obj.key}|${obj.lastModified}"
            // Use the same size as FileRow's ImageRequest so memory cache key matches exactly.
            // Without matching size, Coil's memory cache key differs and every display request
            // falls back to disk/network even though preload already ran.
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(128, 128)
                    .diskCacheKey(cacheKey)
                    .memoryCacheKey(cacheKey)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }

    // Background job: list every known folder and preload all image thumbnails.
    private fun startGlobalPreload(folders: List<String>) {
        val normalizedConfig = config.normalized()
        if (!normalizedConfig.isComplete()) return
        globalPreloadJob?.cancel()
        globalPreloadJob = viewModelScope.launch {
            folders.forEach { folder ->
                if (!isActive) return@launch
                try {
                    val objects = withContext(Dispatchers.IO) {
                        repository.listFolder(normalizedConfig, folder).objects
                    }
                    preloadObjects(objects)
                } catch (_: Exception) { /* ignore individual folder errors */ }
            }
        }
    }

    private fun handleDeletedFolderTarget(deletedFolder: String) {
        val normalizedDeleted = normalizeFolderPath(deletedFolder)
        if (normalizedDeleted.isBlank()) {
            return
        }
        if (uploadFolderInput.startsWith(normalizedDeleted)) {
            uploadFolderInput = parentFolder(normalizedDeleted)
        }
    }

    private fun reconcileUploadFolder(availableFolders: List<String>) {
        var normalizedTarget = normalizeFolderPath(uploadFolderInput)
        if (normalizedTarget.isBlank()) {
            uploadFolderInput = ""
            return
        }
        if (normalizedTarget in availableFolders) {
            uploadFolderInput = normalizedTarget
            return
        }
        while (normalizedTarget.isNotBlank() && normalizedTarget !in availableFolders) {
            normalizedTarget = parentFolder(normalizedTarget)
        }
        uploadFolderInput = normalizedTarget
    }

    private fun validatedConfig(): R2Config? {
        val normalized = config.normalized()
        if (!normalized.isComplete()) {
            bannerMessage = "请先保存完整的 Account ID / Access Key / Secret / Bucket。"
            return null
        }
        return normalized
    }

    private suspend fun readUploadPayload(context: Context, uri: Uri): UploadPayload {
        return withContext(Dispatchers.IO) {
            val fileName = queryDisplayName(context, uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: throw IOException("无法读取选择的图片。")
            val contentType = context.contentResolver.getType(uri) ?: guessMimeType(fileName)
            UploadPayload(fileName = fileName, bytes = bytes, contentType = contentType)
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0).orEmpty().ifBlank { "image_${System.currentTimeMillis()}.jpg" }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "image_${System.currentTimeMillis()}.jpg"
    }

    private fun Throwable.readableMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "请求失败，请检查网络或 R2 配置。"
    }

    private data class UploadPayload(
        val fileName: String,
        val bytes: ByteArray,
        val contentType: String
    )

    private data class CachedThumbnailUrl(
        val url: String,
        val expiresAtMillis: Long
    )

    val bucketUsageProgress: Float
        get() = (bucketUsedBytes.toFloat() / BUCKET_CAPACITY_BYTES.toFloat()).coerceIn(0f, 1f)
}