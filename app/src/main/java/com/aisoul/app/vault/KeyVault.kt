package com.aisoul.app.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * IMPLEMENTATION.md §9 — a random AES-256 key lives in Android Keystore
 * (never exportable); provider API keys are AES-GCM-wrapped with it.
 * Wrapped blobs go to DataStore; nothing here is ever backed up or logged.
 */
class KeyVault {

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "aisoul_vault_key"
        const val GCM_TAG_BITS = 128
    }

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // background distill/refresh must be able to unwrap keys
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    fun wrap(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ct = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$iv:$ct"
    }

    fun unwrap(blob: String): String {
        val (iv, ct) = blob.split(":", limit = 2).let { it[0] to it[1] }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            masterKey(),
            GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}
