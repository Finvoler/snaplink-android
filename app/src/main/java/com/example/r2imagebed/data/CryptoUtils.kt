package com.example.r2imagebed.data

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun sha256Hex(input: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(input)
        .joinToString("") { "%02x".format(it) }
}

fun md5Base64(input: ByteArray): String {
    return java.util.Base64.getEncoder().encodeToString(
        MessageDigest.getInstance("MD5").digest(input)
    )
}

fun hmacSha256(key: ByteArray, data: String): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8))
}