package io.github.rubayet123.tvlive.scraper

import android.util.Base64
import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PlayZTVCryptoUtils {

    private const val TAG = "PlayZTVCrypto"

    /**
     * OLD FORMAT
     */
    private const val PLAYZ_AES_KEY =
        "bTVLbDVuazR4SzFrTjdwTg=="

    private const val PLAYZ_AES_IV =
        "azVLNG5NOG1LbE5MN2wxNQ=="

    /**
     * PRIMARY FORMAT
     */
    private const val PLAYZ_PRIMARY_AES_KEY =
        "Yi8xam1sNW5rNHg1azdwTg=="

    private const val PLAYZ_PRIMARY_AES_IV =
        "MTRuTWs4bU41S2w1S0w3bA=="

    /**
     * Substitution maps from plugin.js
     */
    private const val SUB_FROM =
        "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ"

    private const val SUB_TO =
        "fFgGjJkKaApPbBmMoOzZeEnNcCdDrRqQtTvVuUxXhHiIwWyYlLsS"

    private val SUB_REVERSE = HashMap<Char, Char>()

    init {
        for (i in SUB_TO.indices) {
            SUB_REVERSE[SUB_TO[i]] = SUB_FROM[i]
        }
    }

    private data class KeyInfo(
        val key: ByteArray,
        val iv: ByteArray
    )

    private fun decodeKey(base64: String): ByteArray {
        return safeBase64Decode(base64)
    }

    private fun safeBase64Decode(data: String): ByteArray {
        val norm = normalizeBase64(data)
        return try {
            Base64.decode(norm, Base64.DEFAULT)
        } catch (_: Throwable) {
            try {
                java.util.Base64.getDecoder().decode(norm)
            } catch (_: Throwable) {
                ByteArray(0)
            }
        }
    }

    private val PRIMARY_KEY by lazy {
        KeyInfo(
            decodeKey(PLAYZ_PRIMARY_AES_KEY),
            decodeKey(PLAYZ_PRIMARY_AES_IV)
        )
    }

    private val FALLBACK_KEY by lazy {
        KeyInfo(
            decodeKey(PLAYZ_AES_KEY),
            decodeKey(PLAYZ_AES_IV)
        )
    }

    private val NATIVE_KEY = byteArrayOf(99, 122, 49, 52, 82, 83, 116, 107, 78, 48, 49, 80, 86, 69, 53, 119)
    private val NATIVE_IV = byteArrayOf(87, 84, 108, 69, 118, 99, 107, 100, 50, 85, 82, 52, 49, 115, 100, 107)

    private fun swapAdjacentPairs(data: ByteArray): ByteArray {
        val out = data.clone()
        for (i in 0 until out.size - 1 step 2) {
            val tmp = out[i]
            out[i] = out[i + 1]
            out[i + 1] = tmp
        }
        return out
    }

    private fun decryptNative(raw: String): String? {
        return try {
            val stripped = raw.replace("\\s".toRegex(), "")
            val b1 = safeBase64Decode(stripped)
            if (b1.isEmpty()) return null
            val reversed = b1.reversedArray()
            val b2 = swapAdjacentPairs(reversed)
            val b2Sb = StringBuilder()
            for (b in b2) {
                val u = b.toInt() and 0xFF
                if (u < 128) {
                    val c = u.toChar()
                    if (!Character.isWhitespace(c)) {
                        b2Sb.append(c)
                    }
                } else {
                    b2Sb.append('?')
                }
            }
            val b3 = safeBase64Decode(b2Sb.toString())
            if (b3.isEmpty()) return null
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(NATIVE_KEY, "AES"), IvParameterSpec(NATIVE_IV))
            val decrypted = cipher.doFinal(b3)
            val text = String(decrypted, Charsets.UTF_8).trim()
            if (text.startsWith("{") || text.startsWith("[")) {
                text
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "Native decrypt notice: ${e.message}")
            null
        }
    }

    /**
     * Reverse substitution cipher
     */
    private fun decodeSubstitutionPayload(
        value: String
    ): String {

        val restored = buildString {
            for (char in value) {
                append(
                    SUB_REVERSE[char] ?: char
                )
            }
        }

        return String(
            safeBase64Decode(restored),
            Charsets.UTF_8
        )
    }

    /**
     * Normalize malformed base64
     */
    private fun normalizeBase64(
        value: String
    ): String {

        var normalized = value
            .replace("-", "+")
            .replace("_", "/")
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")
            .replace("\t", "")

        while (normalized.length % 4 != 0) {
            normalized += "="
        }

        return normalized
    }

    /**
     * AES CBC PKCS5 decrypt
     */
    private fun decryptAes(
        dataB64: String,
        keyInfo: KeyInfo
    ): String? {

        return try {

            val cipherBytes = safeBase64Decode(dataB64)

            val cipher = Cipher.getInstance(
                "AES/CBC/PKCS5Padding"
            )

            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(
                    keyInfo.key,
                    "AES"
                ),
                IvParameterSpec(
                    keyInfo.iv
                )
            )

            val decrypted =
                cipher.doFinal(cipherBytes)

            String(
                decrypted,
                Charsets.UTF_8
            ).trim()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "AES failed: ${e.message}"
            )

            null
        }
    }

    /**
     * MAIN DECRYPT
     */
    fun decryptPlayZTV(
        body: String?
    ): String? {

        return try {

            val raw = body?.trim().orEmpty()

            if (raw.isEmpty()) {
                return null
            }

            Log.d(TAG, "Raw Payload: $raw")

            /**
             * Already plain JSON/HTML/M3U
             */
            if (
                raw.startsWith("{") ||
                raw.startsWith("[") ||
                raw.startsWith("<") ||
                raw.startsWith("#EXTM3U")
            ) {
                return raw
            }

            /**
             * NATIVE FORMAT (Current server format)
             */
            try {
                val nativeResult = decryptNative(raw)
                if (!nativeResult.isNullOrBlank()) {
                    Log.d(TAG, "Native decrypt success")
                    return nativeResult
                }
            } catch (e: Exception) {
                Log.d(TAG, "Native decrypt failed: ${e.message}")
            }

            /**
             * PRIMARY FORMAT
             *
             * substitution ->
             * base64 text ->
             * AES decrypt
             */
            try {

                val primaryPayload =
                    decodeSubstitutionPayload(
                        raw.replace("\\s".toRegex(), "")
                    )

                Log.d(
                    TAG,
                    "Primary payload decoded"
                )

                val primary =
                    decryptAes(
                        primaryPayload,
                        PRIMARY_KEY
                    )

                if (!primary.isNullOrBlank()) {

                    Log.d(
                        TAG,
                        "Primary decrypt success"
                    )

                    return primary
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Primary decrypt failed: ${e.message}"
                )
            }

            /**
             * FALLBACK FORMAT
             */
            try {

                val fallback =
                    decryptAes(
                        raw.replace("\\s".toRegex(), ""),
                        FALLBACK_KEY
                    )

                if (!fallback.isNullOrBlank()) {

                    Log.d(
                        TAG,
                        "Fallback decrypt success"
                    )

                    return fallback
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Fallback decrypt failed: ${e.message}"
                )
            }

            /**
             * FALLBACK FORMAT 2: Embedded Slice Decryption (from LIVE2.m3u format)
             */
            try {
                val sliceDecrypted = decryptContentSlice(raw)
                if (!sliceDecrypted.isNullOrBlank() && sliceDecrypted != raw) {
                    Log.d(TAG, "Slice decrypt success")
                    return sliceDecrypted
                }
            } catch (e: Exception) {
                Log.e(TAG, "Slice decrypt failed: ${e.message}")
            }

            Log.e(
                TAG,
                "All decryption strategies failed"
            )

            null

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Decrypt error: ${e.message}",
                e
            )

            null
        }
    }

    private fun decryptContentSlice(content: String): String? {
        val trimmed = content.trim()
        if (trimmed.length < 79) return null

        return try {
            val part1 = trimmed.substring(0, 10)
            val part2 = trimmed.substring(34, trimmed.length - 54)
            val part3 = trimmed.substring(trimmed.length - 10)
            val encryptedData = part1 + part2 + part3

            val ivBase64 = trimmed.substring(10, 34)
            val keyBase64 = trimmed.substring(trimmed.length - 54, trimmed.length - 10)

            val iv = safeBase64Decode(ivBase64)
            val key = safeBase64Decode(keyBase64)
            val encrypted = safeBase64Decode(encryptedData)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decrypted = cipher.doFinal(encrypted)

            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
