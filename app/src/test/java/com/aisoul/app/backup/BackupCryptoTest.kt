package com.aisoul.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest

/** deterministic stand-in for Argon2id — JVM tests exercise the format, not the KDF */
class FakeKdf : Kdf {
    override fun derive(
        passphrase: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(passphrase + salt + byteArrayOf(memoryKiB.toByte(), iterations.toByte(), parallelism.toByte()))
}

class BackupCryptoTest {

    private val crypto = BackupCrypto(FakeKdf())

    @Test
    fun `round trip returns original bytes`() {
        val payload = "the whole harness, zipped".toByteArray()
        val blob = crypto.encrypt(payload, "correct horse battery staple")
        assertArrayEquals(payload, crypto.decrypt(blob, "correct horse battery staple"))
    }

    @Test
    fun `wrong passphrase throws WrongPassphraseException`() {
        val blob = crypto.encrypt("secret".toByteArray(), "right")
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            crypto.decrypt(blob, "wrong")
        }
    }

    @Test
    fun `tampered ciphertext throws WrongPassphraseException`() {
        val blob = crypto.encrypt("secret".toByteArray(), "pass")
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            crypto.decrypt(blob, "pass")
        }
    }

    @Test
    fun `garbage is rejected as not a backup`() {
        assertThrows(BackupCrypto.BadArchiveException::class.java) {
            crypto.decrypt("PK garbage that is not ours at all, but long enough".toByteArray(), "pass")
        }
    }

    @Test
    fun `truncated blob is rejected`() {
        assertThrows(BackupCrypto.BadArchiveException::class.java) {
            crypto.decrypt(ByteArray(10), "pass")
        }
    }

    @Test
    fun `header carries the kdf params used`() {
        val blob = crypto.encrypt(ByteArray(1), "p")
        assertEquals("AISOULBK", blob.copyOfRange(0, 8).toString(Charsets.US_ASCII))
        assertEquals(BackupCrypto.FORMAT_VERSION, blob[8])
        assertEquals(BackupCrypto.KDF_ARGON2ID, blob[9])
    }

    @Test
    fun `two archives of the same payload differ (random salt and nonce)`() {
        val payload = "same".toByteArray()
        val a = crypto.encrypt(payload, "p")
        val b = crypto.encrypt(payload, "p")
        assertEquals(false, a.contentEquals(b))
    }
}
