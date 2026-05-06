package com.example.r2imagebed.data

import android.net.Uri

data class R2Config(
    val accountId: String = "",
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val bucketName: String = "",
    val publicBaseUrl: String = ""
) {
    fun normalized(): R2Config {
        val normalizedPublicBaseUrl = publicBaseUrl
            .trim()
            .trimEnd('/')
            .let { rawValue ->
                when {
                    rawValue.isBlank() -> ""
                    rawValue.startsWith("http://") || rawValue.startsWith("https://") -> rawValue
                    else -> "https://$rawValue"
                }
            }
            .let { rawUrl ->
                if (rawUrl.isBlank()) {
                    ""
                } else {
                    val parsed = Uri.parse(rawUrl)
                    val scheme = parsed.scheme ?: "https"
                    val authority = parsed.authority.orEmpty()
                    if (authority.isBlank()) "" else "$scheme://$authority"
                }
            }
        return copy(
            accountId = accountId.trim(),
            accessKeyId = accessKeyId.trim(),
            secretAccessKey = secretAccessKey.trim(),
            bucketName = bucketName.trim(),
            publicBaseUrl = normalizedPublicBaseUrl
        )
    }

    fun isComplete(): Boolean {
        val value = normalized()
        return value.accountId.isNotBlank() &&
            value.accessKeyId.isNotBlank() &&
            value.secretAccessKey.isNotBlank() &&
            value.bucketName.isNotBlank()
    }

    fun endpointHost(): String = "${normalized().accountId}.r2.cloudflarestorage.com"

    fun publicBaseUrl(): String {
        val value = normalized()
        return value.publicBaseUrl.ifBlank {
            "https://${value.bucketName}.${value.accountId}.r2.dev"
        }
    }
}

data class R2Folder(
    val prefix: String
) {
    val name: String
        get() {
            val trimmed = prefix.trimEnd('/')
            return trimmed.substringAfterLast('/', trimmed.ifBlank { "/" })
        }
}

data class R2Object(
    val key: String,
    val size: Long,
    val lastModified: String = ""
) {
    val name: String
        get() = key.substringAfterLast('/')
}

data class FolderListing(
    val prefix: String,
    val folders: List<R2Folder> = emptyList(),
    val objects: List<R2Object> = emptyList()
)