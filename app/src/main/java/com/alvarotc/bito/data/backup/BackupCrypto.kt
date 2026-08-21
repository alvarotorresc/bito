package com.alvarotc.bito.data.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Wrong passphrase and a tampered container are indistinguishable by design (GCM tag). */
class WrongPassphraseException(cause: Throwable? = null) :
    Exception("Wrong passphrase or corrupted container", cause)

/** Argon2id cost. Moderate-for-mobile defaults (OWASP-sanctioned tier); the header carries them, so future bumps stay restorable. */
data class Argon2Params(val memoryKib: Int = 19456, val iterations: Int = 2, val parallelism: Int = 1)

/** A derived AES-256 key plus the salt/params that produced it (the header needs them). */
class DerivedKey(val key: ByteArray, val salt: ByteArray, val params: Argon2Params)

object BackupCrypto {
    private const val MAGIC = "BITO1"
    private const val CONTAINER_VERSION = 1
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_SIZE = 32

    // Sane caps for Argon2 header fields read from an untrusted container. Our own headers always
    // carry memoryKib/iterations/parallelism within these ranges (default 19456/2/1); a header
    // outside them is corrupted or hostile, never a real Bito backup. Rejecting here — before
    // Bouncy Castle allocates or validates internally — keeps the failure inside the two blessed
    // exception types instead of an untyped IllegalArgumentException or an OutOfMemoryError.
    private const val MIN_MEMORY_KIB = 1
    private const val MAX_MEMORY_KIB = 1_048_576 // 1 GiB
    private const val MIN_ITERATIONS = 1
    private const val MAX_ITERATIONS = 64
    private const val MIN_PARALLELISM = 1
    private const val MAX_PARALLELISM = 16

    // Fixed offsets — see the class KDoc header layout for the full byte map.
    private const val OFFSET_MAGIC = 0
    private const val OFFSET_VERSION = 5
    private const val OFFSET_SALT = 6
    private const val OFFSET_MEMORY_KIB = 22
    private const val OFFSET_ITERATIONS = 26
    private const val OFFSET_PARALLELISM = 30
    private const val OFFSET_NONCE = 34
    private const val OFFSET_CIPHERTEXT = 46

    /** Container layout v1 — all offsets fixed:
     *  [0..4] ASCII "BITO1" · [5] container version = 1 · [6..21] salt (16) ·
     *  [22..25] memoryKib · [26..29] iterations · [30..33] parallelism (Int big-endian each) ·
     *  [34..45] GCM nonce (12) · [46..] ciphertext+tag (tag 128 bits). */
    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < OFFSET_SALT) return false
        return bytes.copyOfRange(OFFSET_MAGIC, OFFSET_VERSION).toString(Charsets.US_ASCII) == MAGIC
    }

    fun newSalt(): ByteArray = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }

    fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        params: Argon2Params = Argon2Params(),
    ): DerivedKey = DerivedKey(argon2(passphrase, salt, params), salt, params)

    fun encrypt(
        json: String,
        derived: DerivedKey,
    ): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(derived.key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        val header = header(derived.salt, derived.params, nonce)
        return header + ciphertext
    }

    fun decrypt(
        bytes: ByteArray,
        passphrase: CharArray,
    ): String {
        if (!isEncrypted(bytes)) throw BackupFormatException("Missing BITO1 magic")
        val version = bytes[OFFSET_VERSION].toInt()
        if (version != CONTAINER_VERSION) throw BackupFormatException("Unsupported container version $version")
        if (bytes.size < OFFSET_CIPHERTEXT) throw BackupFormatException("Truncated container")

        val salt = bytes.copyOfRange(OFFSET_SALT, OFFSET_MEMORY_KIB)
        val params =
            Argon2Params(
                memoryKib = bytes.readIntAt(OFFSET_MEMORY_KIB),
                iterations = bytes.readIntAt(OFFSET_ITERATIONS),
                parallelism = bytes.readIntAt(OFFSET_PARALLELISM),
            )
        validateParams(params)
        val nonce = bytes.copyOfRange(OFFSET_NONCE, OFFSET_CIPHERTEXT)
        val ciphertext = bytes.copyOfRange(OFFSET_CIPHERTEXT, bytes.size)

        val key = argon2(passphrase, salt, params)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val plaintext =
            try {
                cipher.doFinal(ciphertext)
            } catch (e: BadPaddingException) {
                // GCM has no padding: the only way doFinal throws this family is a tag mismatch —
                // either the wrong key (wrong passphrase) or tampered ciphertext. AEADBadTagException
                // is SunJCE's concrete subtype; catching the supertype avoids depending on which
                // provider (SunJCE on the JVM, Conscrypt on Android) is active.
                throw WrongPassphraseException(e)
            }
        return plaintext.toString(Charsets.UTF_8)
    }

    private fun validateParams(params: Argon2Params) {
        if (params.memoryKib !in MIN_MEMORY_KIB..MAX_MEMORY_KIB ||
            params.iterations !in MIN_ITERATIONS..MAX_ITERATIONS ||
            params.parallelism !in MIN_PARALLELISM..MAX_PARALLELISM
        ) {
            throw BackupFormatException("Argon2 params out of range: $params")
        }
    }

    private fun header(
        salt: ByteArray,
        params: Argon2Params,
        nonce: ByteArray,
    ): ByteArray {
        val header = ByteArray(OFFSET_CIPHERTEXT)
        MAGIC.toByteArray(Charsets.US_ASCII).copyInto(header, OFFSET_MAGIC)
        header[OFFSET_VERSION] = CONTAINER_VERSION.toByte()
        salt.copyInto(header, OFFSET_SALT)
        header.writeIntAt(OFFSET_MEMORY_KIB, params.memoryKib)
        header.writeIntAt(OFFSET_ITERATIONS, params.iterations)
        header.writeIntAt(OFFSET_PARALLELISM, params.parallelism)
        nonce.copyInto(header, OFFSET_NONCE)
        return header
    }

    private fun ByteArray.writeIntAt(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private fun ByteArray.readIntAt(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)

    private fun argon2(
        passphrase: CharArray,
        salt: ByteArray,
        params: Argon2Params,
    ): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKib)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build(),
        )
        val key = ByteArray(KEY_SIZE)
        generator.generateBytes(passphrase.concatToString().toByteArray(Charsets.UTF_8), key)
        return key
    }
}
