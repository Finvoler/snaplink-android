package com.example.r2imagebed.data

import android.net.Uri
import android.webkit.MimeTypeMap
import java.util.Locale

fun normalizeFolderPath(raw: String): String {
    val cleaned = raw
        .replace('\\', '/')
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("/")
    return if (cleaned.isBlank()) "" else "$cleaned/"
}

fun buildObjectKey(folder: String, fileName: String): String {
    val normalizedFolder = normalizeFolderPath(folder)
    val cleanName = sanitizeFileName(fileName)
    return "$normalizedFolder$cleanName"
}

fun parentFolder(folder: String): String {
    val normalized = normalizeFolderPath(folder).trimEnd('/')
    if (normalized.isBlank()) {
        return ""
    }
    val parent = normalized.substringBeforeLast('/', "")
    return if (parent.isBlank()) "" else "$parent/"
}

fun buildPublicUrl(config: R2Config, key: String): String {
    val encodedKey = key
        .split('/')
        .filter { it.isNotEmpty() }
        .joinToString("/") { Uri.encode(it) }
    return if (encodedKey.isBlank()) config.publicBaseUrl() else "${config.publicBaseUrl()}/$encodedKey"
}

fun isLikelyImage(key: String): Boolean {
    val extension = key.substringAfterLast('.', "").lowercase(Locale.US)
    return extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
}

fun guessMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return mapped ?: "application/octet-stream"
}

fun sanitizeFileName(fileName: String): String {
    val cleaned = fileName
        .trim()
        .replace('\\', '_')
        .replace('/', '_')
    return if (cleaned.isBlank()) "image_${System.currentTimeMillis()}.jpg" else cleaned
}

fun resolveChildFolder(baseFolder: String, childInput: String): String {
    val raw = childInput.trim()
    if (raw.isBlank()) {
        return normalizeFolderPath(baseFolder)
    }
    return if (baseFolder.isBlank()) {
        normalizeFolderPath(raw)
    } else {
        normalizeFolderPath("${normalizeFolderPath(baseFolder).trimEnd('/')}/$raw")
    }
}