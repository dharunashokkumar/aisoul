package com.aisoul.app.backup

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode

/**
 * IMPLEMENTATION §8 KDF. argon2kt is a thin JNI binding over the reference
 * argon2 implementation (D-024) — much lighter than lazysodium+JNA for the
 * one primitive we need. Android-only; JVM tests inject a fake [Kdf].
 */
class Argon2Kdf : Kdf {

    private val argon2 by lazy { Argon2Kt() }

    override fun derive(
        passphrase: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): ByteArray =
        argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = passphrase,
            salt = salt,
            tCostInIterations = iterations,
            mCostInKibibyte = memoryKiB,
            parallelism = parallelism,
            hashLengthInBytes = 32,
        ).rawHashAsByteArray()
}
