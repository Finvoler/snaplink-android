package com.example.r2imagebed.data

class R2Repository {
    suspend fun testConnection(config: R2Config) {
        R2S3Client(config.normalized()).listFolder("")
    }

    suspend fun listFolder(config: R2Config, prefix: String): FolderListing {
        return R2S3Client(config.normalized()).listFolder(prefix)
    }

    suspend fun listAllFolders(config: R2Config): List<String> {
        return R2S3Client(config.normalized()).listAllFolders()
    }

    suspend fun totalObjectBytes(config: R2Config): Long {
        return R2S3Client(config.normalized()).totalObjectBytes()
    }

    suspend fun createFolder(config: R2Config, folderPath: String) {
        R2S3Client(config.normalized()).createFolder(folderPath)
    }

    suspend fun uploadObject(
        config: R2Config,
        folderPath: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): String {
        return R2S3Client(config.normalized()).uploadObject(
            folderPath, fileName, bytes, contentType, onProgress
        )
    }

    fun presignGetUrl(config: R2Config, key: String, expiresInSeconds: Int = 3600): String {
        return R2S3Client(config.normalized()).presignGetUrl(key, expiresInSeconds)
    }

    suspend fun moveObject(config: R2Config, sourceKey: String, targetKey: String) {
        R2S3Client(config.normalized()).moveObject(sourceKey, targetKey)
    }

    suspend fun deleteObject(config: R2Config, key: String) {
        R2S3Client(config.normalized()).deleteObject(key)
    }

    suspend fun deleteFolder(config: R2Config, folderPath: String) {
        R2S3Client(config.normalized()).deleteFolder(folderPath)
    }
}