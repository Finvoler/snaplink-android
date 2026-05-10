package com.example.r2imagebed.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TreeMap

class R2S3Client(
    private val config: R2Config,
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    suspend fun listFolder(prefix: String): FolderListing = withContext(Dispatchers.IO) {
        val normalizedPrefix = normalizeFolderPath(prefix)
        val folders = linkedSetOf<R2Folder>()
        val objects = mutableListOf<R2Object>()
        var continuationToken: String? = null

        do {
            val page = listObjectsPage(
                prefix = normalizedPrefix,
                delimiter = "/",
                continuationToken = continuationToken
            )
            folders += page.prefixes.map { R2Folder(it) }
            objects += page.objects.filterNot { item ->
                item.key == normalizedPrefix || item.key.endsWith("/")
            }
            continuationToken = page.nextContinuationToken
        } while (continuationToken != null)

        FolderListing(
            prefix = normalizedPrefix,
            folders = folders.sortedBy { it.prefix },
            objects = objects.sortedByDescending { it.lastModified }
        )
    }

    suspend fun listAllFolders(): List<String> = withContext(Dispatchers.IO) {
        val folders = linkedSetOf<String>()
        for (key in listAllKeys(prefix = "")) {
            val segments = key.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) {
                continue
            }
            val maxDepth = if (key.endsWith('/')) segments.size else segments.size - 1
            for (index in 1..maxDepth) {
                folders += segments.take(index).joinToString("/") + "/"
            }
        }
        folders.sorted()
    }

    suspend fun totalObjectBytes(): Long = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        var continuationToken: String? = null

        do {
            val page = listObjectsPage(
                prefix = "",
                delimiter = null,
                continuationToken = continuationToken
            )
            totalBytes += page.objects
                .filterNot { it.key.endsWith("/") }
                .sumOf { it.size }
            continuationToken = page.nextContinuationToken
        } while (continuationToken != null)

        totalBytes
    }

    suspend fun createFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val normalized = normalizeFolderPath(folderPath)
        require(normalized.isNotBlank()) { "Folder path cannot be empty." }
        putObject(normalized, ByteArray(0), "application/x-directory")
    }

    suspend fun uploadObject(
        folderPath: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val key = buildObjectKey(folderPath, fileName)
        putObjectWithProgress(key, bytes, contentType, onProgress)
        buildPublicUrl(config, key)
    }

    suspend fun moveObject(sourceKey: String, targetKey: String) = withContext(Dispatchers.IO) {
        require(sourceKey.isNotBlank()) { "Source key cannot be empty." }
        require(targetKey.isNotBlank()) { "Target key cannot be empty." }
        if (sourceKey == targetKey) {
            return@withContext
        }

        copyObject(sourceKey, targetKey)
        executeRequest(
            method = "DELETE",
            canonicalPath = bucketObjectPath(sourceKey)
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException(response.body?.string().orEmpty())
            }
        }
    }

    fun presignGetUrl(key: String, expiresInSeconds: Int = 3600): String {
        val normalizedConfig = config.normalized()
        val timestamp = ZonedDateTime.now(ZoneOffset.UTC)
        val amzDate = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val shortDate = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val canonicalPath = bucketObjectPath(key)
        val credentialScope = "$shortDate/auto/s3/aws4_request"
        val credential = "${normalizedConfig.accessKeyId}/$credentialScope"

        val queryParams = listOf(
            "X-Amz-Algorithm" to "AWS4-HMAC-SHA256",
            "X-Amz-Credential" to credential,
            "X-Amz-Date" to amzDate,
            "X-Amz-Expires" to expiresInSeconds.toString(),
            "X-Amz-SignedHeaders" to "host"
        )

        val canonicalQuery = queryParams
            .map { (k, v) -> percentEncode(k) to percentEncode(v) }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { "${it.first}=${it.second}" }

        val canonicalHeaders = "host:${normalizedConfig.endpointHost()}\n"
        val signedHeaders = "host"
        val payloadHash = "UNSIGNED-PAYLOAD"

        val canonicalRequest = listOf(
            "GET",
            canonicalPath,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")

        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        val signingKey = hmacSha256(
            hmacSha256(
                hmacSha256(
                    hmacSha256(
                        ("AWS4${normalizedConfig.secretAccessKey}").toByteArray(Charsets.UTF_8),
                        shortDate
                    ),
                    "auto"
                ),
                "s3"
            ),
            "aws4_request"
        )

        val signature = hmacSha256(signingKey, stringToSign)
            .joinToString("") { "%02x".format(it) }

        val finalQuery = "$canonicalQuery&${percentEncode("X-Amz-Signature")}=${percentEncode(signature)}"

        return buildString {
            append("https://")
            append(normalizedConfig.endpointHost())
            append(canonicalPath)
            append('?')
            append(finalQuery)
        }
    }

    suspend fun deleteObject(key: String) = withContext(Dispatchers.IO) {
        executeRequest(
            method = "DELETE",
            canonicalPath = bucketObjectPath(key)
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException(response.body?.string().orEmpty())
            }
        }
    }

    suspend fun deleteFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val normalized = normalizeFolderPath(folderPath)
        require(normalized.isNotBlank()) { "Folder path cannot be empty." }

        val allKeys = listAllKeys(normalized).toMutableSet()
        allKeys += normalized
        allKeys.chunked(1000).forEach { chunk ->
            deleteObjects(chunk)
        }
    }

    private fun listAllKeys(prefix: String): List<String> {
        val keys = mutableListOf<String>()
        var continuationToken: String? = null

        do {
            val page = listObjectsPage(
                prefix = prefix,
                delimiter = null,
                continuationToken = continuationToken
            )
            keys += page.objects.map { it.key }
            continuationToken = page.nextContinuationToken
        } while (continuationToken != null)

        return keys
    }

    private fun listObjectsPage(
        prefix: String,
        delimiter: String?,
        continuationToken: String?
    ): ListPage {
        val queryParameters = mutableListOf(
            "list-type" to "2"
        )
        if (prefix.isNotBlank()) {
            queryParameters += "prefix" to prefix
        }
        if (!delimiter.isNullOrBlank()) {
            queryParameters += "delimiter" to delimiter
        }
        if (!continuationToken.isNullOrBlank()) {
            queryParameters += "continuation-token" to continuationToken
        }

        val xml = executeRequest(
            method = "GET",
            canonicalPath = bucketPath(),
            queryParameters = queryParameters
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException(response.body?.string().orEmpty())
            }
            response.body?.string().orEmpty()
        }

        return parseListResult(xml)
    }

    private fun putObject(key: String, bytes: ByteArray, contentType: String) {
        putObjectWithProgress(key, bytes, contentType) { _, _ -> }
    }

    private fun copyObject(sourceKey: String, targetKey: String) {
        executeRequest(
            method = "PUT",
            canonicalPath = bucketObjectPath(targetKey),
            extraHeaders = mapOf("x-amz-copy-source" to bucketObjectPath(sourceKey))
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException(response.body?.string().orEmpty())
            }
        }
    }

    private fun putObjectWithProgress(
        key: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: (Long, Long) -> Unit
    ) {
        val totalBytes = bytes.size.toLong()
        val baseBody = bytes.toRequestBody(contentType.toMediaTypeOrNull())
        val progressBody = object : RequestBody() {
            override fun contentType() = baseBody.contentType()
            override fun contentLength() = totalBytes
            override fun writeTo(sink: BufferedSink) {
                var written = 0L
                val countingSink = object : ForwardingSink(sink) {
                    override fun write(source: Buffer, byteCount: Long) {
                        super.write(source, byteCount)
                        written += byteCount
                        onProgress(written, totalBytes)
                    }
                }
                val buffered = countingSink.buffer()
                baseBody.writeTo(buffered)
                buffered.flush()
            }
        }
        executeRequest(
            method = "PUT",
            canonicalPath = bucketObjectPath(key),
            body = bytes,
            extraHeaders = mapOf("content-type" to contentType),
            overrideBody = progressBody
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException(response.body?.string().orEmpty())
            }
        }
    }

    private fun deleteObjects(keys: List<String>) {
        if (keys.isEmpty()) {
            return
        }
        val bodyString = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<Delete>")
            append("<Quiet>true</Quiet>")
            keys.forEach { key ->
                append("<Object><Key>")
                append(escapeXml(key))
                append("</Key></Object>")
            }
            append("</Delete>")
        }
        val bodyBytes = bodyString.toByteArray(Charsets.UTF_8)
        executeRequest(
            method = "POST",
            canonicalPath = bucketPath(),
            queryParameters = listOf("delete" to ""),
            body = bodyBytes,
            extraHeaders = mapOf(
                "content-md5" to md5Base64(bodyBytes),
                "content-type" to "application/xml; charset=utf-8"
            )
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException(response.body?.string().orEmpty())
            }
        }
    }

    private fun executeRequest(
        method: String,
        canonicalPath: String,
        queryParameters: List<Pair<String, String>> = emptyList(),
        body: ByteArray = ByteArray(0),
        extraHeaders: Map<String, String> = emptyMap(),
        overrideBody: RequestBody? = null
    ): okhttp3.Response {
        val payloadHash = sha256Hex(body)
        val timestamp = ZonedDateTime.now(ZoneOffset.UTC)
        val amzDate = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val shortDate = timestamp.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val headersToSign = TreeMap<String, String>()
        headersToSign["host"] = config.endpointHost()
        headersToSign["x-amz-content-sha256"] = payloadHash
        headersToSign["x-amz-date"] = amzDate
        extraHeaders.forEach { (key, value) ->
            headersToSign[key.lowercase()] = value.trim()
        }

        val canonicalQuery = queryParameters
            .map { (key, value) -> percentEncode(key) to percentEncode(value) }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { "${it.first}=${it.second}" }

        val canonicalHeaders = headersToSign.entries.joinToString(separator = "") {
            "${it.key}:${it.value}\n"
        }
        val signedHeaders = headersToSign.keys.joinToString(";")
        val canonicalRequest = listOf(
            method,
            canonicalPath,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")
        val credentialScope = "$shortDate/auto/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))
        ).joinToString("\n")

        val signingKey = hmacSha256(
            hmacSha256(
                hmacSha256(
                    hmacSha256(
                        ("AWS4${config.normalized().secretAccessKey}").toByteArray(Charsets.UTF_8),
                        shortDate
                    ),
                    "auto"
                ),
                "s3"
            ),
            "aws4_request"
        )
        val signature = hmacSha256(signingKey, stringToSign)
            .joinToString("") { "%02x".format(it) }
        val authorization = "AWS4-HMAC-SHA256 Credential=${config.normalized().accessKeyId}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val url = buildString {
            append("https://")
            append(config.endpointHost())
            append(canonicalPath)
            if (canonicalQuery.isNotBlank()) {
                append('?')
                append(canonicalQuery)
            }
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .method(
                method,
                when {
                    overrideBody != null -> overrideBody
                    method == "GET" || method == "DELETE" -> null
                    else -> body.toRequestBody(extraHeaders["content-type"]?.toMediaTypeOrNull())
                }
            )
            .header("Authorization", authorization)

        headersToSign.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        return httpClient.newCall(requestBuilder.build()).execute()
    }

    private fun parseListResult(xml: String): ListPage {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())

        val prefixes = mutableListOf<String>()
        val objects = mutableListOf<R2Object>()
        var nextToken: String? = null
        var currentKey = ""
        var currentSize = 0L
        var currentLastModified = ""
        var currentPrefix = ""
        var currentTag: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (parser.name == "Contents") {
                        currentKey = ""
                        currentSize = 0L
                        currentLastModified = ""
                    }
                }

                XmlPullParser.TEXT -> {
                    when (currentTag) {
                        "Key" -> currentKey = parser.text.orEmpty()
                        "Size" -> currentSize = parser.text.orEmpty().toLongOrNull() ?: 0L
                        "LastModified" -> currentLastModified = parser.text.orEmpty()
                        "Prefix" -> currentPrefix = parser.text.orEmpty()
                        "NextContinuationToken" -> nextToken = parser.text.orEmpty().ifBlank { null }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "Contents" -> {
                            if (currentKey.isNotBlank()) {
                                objects += R2Object(
                                    key = currentKey,
                                    size = currentSize,
                                    lastModified = currentLastModified
                                )
                            }
                        }

                        "CommonPrefixes" -> {
                            if (currentPrefix.isNotBlank()) {
                                prefixes += currentPrefix
                                currentPrefix = ""
                            }
                        }
                    }
                    currentTag = null
                }
            }
            parser.next()
        }

        return ListPage(prefixes = prefixes, objects = objects, nextContinuationToken = nextToken)
    }

    private fun bucketPath(): String {
        return "/${percentEncode(config.normalized().bucketName)}"
    }

    private fun bucketObjectPath(key: String): String {
        val normalizedKey = key.split('/').filter { it.isNotEmpty() }
            .joinToString("/") { percentEncode(it) }
        return if (key.endsWith('/')) {
            "${bucketPath()}/$normalizedKey/"
        } else {
            "${bucketPath()}/$normalizedKey"
        }
    }

    private fun percentEncode(value: String): String {
        val unreserved = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~"
        val builder = StringBuilder()
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val char = byte.toInt().toChar()
            if (unreserved.indexOf(char) >= 0) {
                builder.append(char)
            } else {
                builder.append("%%%02X".format(byte.toInt() and 0xFF))
            }
        }
        return builder.toString()
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private data class ListPage(
        val prefixes: List<String>,
        val objects: List<R2Object>,
        val nextContinuationToken: String?
    )
}