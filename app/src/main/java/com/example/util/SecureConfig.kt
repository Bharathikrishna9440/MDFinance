package com.example.util

object SecureConfig {
    private const val SECRET_XOR_KEY = 0x43

    // Obfuscated representation of authorization username
    private val CRYPTO_USERNAME = intArrayOf(46, 54, 45, 38, 38, 48, 52, 34, 49, 34, 45)

    // Obfuscated representation of authorization password
    private val CRYPTO_PASSWORD = intArrayOf(14, 7, 33, 3, 113, 119, 115, 123, 115, 116)

    // Obfuscated representation of database URL
    private val CRYPTO_FIREBASE_URL = intArrayOf(
        43, 55, 55, 51, 48, 121, 108, 108, 51, 47, 34, 45, 45, 38, 45, 36,
        110, 52, 38, 55, 43, 110, 34, 38, 110, 117, 115, 112, 32, 33, 110, 39,
        38, 41, 34, 54, 47, 55, 110, 49, 55, 39, 33, 109, 34, 48, 38, 34,
        110, 48, 44, 54, 55, 43, 38, 34, 48, 55, 114, 109, 41, 38, 49, 38,
        33, 34, 48, 38, 39, 34, 55, 34, 33, 34, 48, 38, 109, 34, 51, 51,
        108
    )

    // Obfuscated representation of synchronization Google Script Web App URL
    private val CRYPTO_SCRIPT_URL = intArrayOf(
        43, 55, 55, 51, 48, 121, 108, 108, 48, 32, 49, 42, 51, 55, 109, 36, 44, 44,
        36, 47, 38, 109, 32, 44, 46, 108, 46, 34, 32, 49, 44, 48, 108, 48, 108, 2,
        8, 37, 58, 32, 33, 52, 116, 41, 5, 34, 14, 22, 18, 13, 114, 38, 51, 123, 0,
        40, 6, 59, 117, 51, 27, 0, 8, 112, 22, 12, 53, 6, 11, 46, 12, 119, 38, 26,
        14, 37, 117, 22, 122, 18, 118, 47, 42, 8, 43, 0, 112, 38, 115, 27, 32, 116,
        27, 48, 112, 122, 113, 58, 51, 117, 4, 110, 6, 53, 16, 17, 20, 108, 38, 59,
        38, 32
    )

    private fun decrypt(data: IntArray): String {
        val chars = CharArray(data.size) { i ->
            (data[i] xor SECRET_XOR_KEY).toChar()
        }
        return String(chars)
    }

    val adminUsername: String by lazy { decrypt(CRYPTO_USERNAME) }
    val adminPassword: String by lazy { decrypt(CRYPTO_PASSWORD) }
    val firebaseDatabaseUrl: String = "https://collection-app-2007-default-rtdb.asia-southeast1.firebasedatabase.app/"
    val googleScriptUrl: String by lazy { decrypt(CRYPTO_SCRIPT_URL) }
}
