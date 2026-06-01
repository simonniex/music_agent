package com.example.mediaagent.shared

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object PlatformCrypto {
    actual fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    actual fun sha256Hex(data: String): String {
        return hex(MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8)))
    }

    actual fun hex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    actual fun base64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    actual fun epochSeconds(): Long = System.currentTimeMillis() / 1000

    actual fun utcDate(epochSeconds: Long): String {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate().toString()
    }
}
