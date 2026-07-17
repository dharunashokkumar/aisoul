package com.aisoul.app.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * IMPLEMENTATION §8 — client-side encryption, always. Google never sees
 * plaintext. Key = Argon2id(passphrase, per-archive random salt);
 * body = AES-256-GCM over the zip. The KDF is injected so the format
 * round-trips in JVM tests without the native Argon2 binding.
 *
 * Archive layout (format v1):
 * ```
 * 0   8  magic "AISOULBK"
 * 8   1  format version (1)
 * 9   1  kdf id (1 = argon2id)
 * 10  4  kdf memory KiB
 * 14  4  kdf iterations
 * 18  4  kdf parallelism
 * 22 16  salt
 * 38 12  GCM nonce
 * 50  …  AES-256-GCM ciphertext of the zip
 * ```
 */
interface Kdf {
    fun derive(
        passphrase: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): ByteArray
}

class BackupCrypto(private val kdf: Kdf) {

    class WrongPassphraseException : Exception("wrong passphrase")
    class BadArchiveException(message: String) : Exception(message)

    fun encrypt(zipBytes: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val key = kdf.derive(passphrase.toByteArray(Charsets.UTF_8), salt, KDF_MEMORY_KIB, KDF_ITERATIONS, KDF_PARALLELISM)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(zipBytes)

        return ByteBuffer.allocate(HEADER_LEN + ciphertext.size).apply {
            put(MAGIC)
            put(FORMAT_VERSION)
            put(KDF_ARGON2ID)
            putInt(KDF_MEMORY_KIB)
            putInt(KDF_ITERATIONS)
            putInt(KDF_PARALLELISM)
            put(salt)
            put(nonce)
            put(ciphertext)
        }.array()
    }

    fun decrypt(blob: ByteArray, passphrase: String): ByteArray {
        if (blob.size < HEADER_LEN + GCM_TAG_BITS / 8) throw BadArchiveException("not an aisoul backup (too small)")
        val buffer = ByteBuffer.wrap(blob)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) throw BadArchiveException("not an aisoul backup")
        val version = buffer.get()
        if (version != FORMAT_VERSION) throw BadArchiveException("backup format $version is newer than this app understands")
        val kdfId = buffer.get()
        if (kdfId != KDF_ARGON2ID) throw BadArchiveException("unknown kdf $kdfId")
        val memoryKiB = buffer.int
        val iterations = buffer.int
        val parallelism = buffer.int
        val salt = ByteArray(SALT_LEN).also { buffer.get(it) }
        val nonce = ByteArray(NONCE_LEN).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val key = kdf.derive(passphrase.toByteArray(Charsets.UTF_8), salt, memoryKiB, iterations, parallelism)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }

    companion object {
        val MAGIC = "AISOULBK".toByteArray(Charsets.US_ASCII)
        const val FORMAT_VERSION: Byte = 1
        const val KDF_ARGON2ID: Byte = 1
        const val SALT_LEN = 16
        const val NONCE_LEN = 12
        const val GCM_TAG_BITS = 128
        const val HEADER_LEN = 8 + 1 + 1 + 4 + 4 + 4 + SALT_LEN + NONCE_LEN

        // Argon2id cost written into every archive header, so these can be
        // raised later without breaking old backups
        const val KDF_MEMORY_KIB = 65536 // 64 MiB
        const val KDF_ITERATIONS = 3
        const val KDF_PARALLELISM = 2

        private val random = SecureRandom()
    }
}
