package com.example.mediaagent.shared

expect object PlatformCrypto {
    fun hmacSha256(key: ByteArray, data: String): ByteArray
    fun sha256Hex(data: String): String
    fun hex(bytes: ByteArray): String
    fun base64(bytes: ByteArray): String
    fun epochSeconds(): Long
    fun utcDate(epochSeconds: Long): String
}
