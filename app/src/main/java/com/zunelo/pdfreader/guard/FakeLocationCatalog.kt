package com.zunelo.pdfreader.guard

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object FakeLocationCatalog {
    private val nonceValues = intArrayOf(
        120, 21, 185, 201, 30, 252, 26, 151, 201, 202, 164, 10
    )

    private val encryptedValues = intArrayOf(
        234, 27, 153, 74, 67, 148, 137, 93, 178, 167, 117, 9, 62, 86, 13, 91,
        112, 63, 176, 141, 6, 169, 232, 226, 35, 228, 230, 47, 191, 217, 44, 144,
        116, 167, 78, 90, 229, 165, 97, 52, 113, 23, 125, 109, 200, 18, 23, 244,
        242, 163, 12, 129, 230, 5, 134, 29, 95, 188, 249, 106, 200, 211, 51, 241,
        18, 60, 99, 53, 103, 43, 186, 94, 209, 179, 120, 193, 42, 117, 250, 69,
        86, 12, 113, 200, 161, 57, 126, 52, 227, 187, 129, 247, 162, 221, 64, 60,
        65, 94, 99, 115, 49, 172, 116, 120, 6, 229, 220, 132, 44, 200, 105, 157,
        135, 53, 98, 86, 169, 4, 32, 180, 83, 83, 93, 82, 121, 169, 246, 138,
        99, 246, 69, 226, 189, 131, 226, 252, 0, 66, 67, 249, 182, 47, 13, 160,
        56, 85, 146, 152, 236, 143, 45, 158, 47, 148, 163, 95, 106, 225, 197,
        83, 136, 177, 250, 187, 188, 141, 40, 169, 54, 221, 217, 21, 30, 171,
        87, 198, 209, 52, 151, 195, 124, 65, 195, 71, 74, 47, 130, 27, 233, 73,
        87, 9, 204, 202, 76, 140, 184, 211, 65
    )

    @Volatile
    private var decodedPackages: Set<String>? = null

    fun contains(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName in packages()
    }

    private fun packages(): Set<String> {
        decodedPackages?.let { return it }
        return synchronized(this) {
            decodedPackages ?: decryptPackages().also { decodedPackages = it }
        }
    }

    private fun decryptPackages(): Set<String> = runCatching {
        val encrypted = encryptedValues.toByteArray()
        val nonce = nonceValues.toByteArray()
        val key = catalogKey()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce)
            )
            val plain = cipher.doFinal(encrypted)
            try {
                plain.toString(Charsets.UTF_8)
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .toSet()
            } finally {
                plain.fill(0)
            }
        } finally {
            encrypted.fill(0)
            nonce.fill(0)
            key.fill(0)
        }
    }.getOrDefault(emptySet())

    private fun catalogKey(): ByteArray {
        val seed = buildString {
            append("zun")
            append("elo")
            append(':')
            append("pdf")
            append(':')
            append("geo")
            append(':')
            append("v1")
        }.toByteArray(Charsets.UTF_8)
        return try {
            MessageDigest.getInstance("SHA-256").digest(seed).copyOf(16)
        } finally {
            seed.fill(0)
        }
    }

    private fun IntArray.toByteArray(): ByteArray = ByteArray(size) { index ->
        this[index].toByte()
    }
}
