package com.alvarotc.bito.data.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCryptoTest {
    private val passphrase = "correct horse battery staple".toCharArray()

    // Params mínimos para que la suite no pague 19 MiB de Argon2 por test.
    private val fastParams = Argon2Params(memoryKib = 64, iterations = 1, parallelism = 1)

    private fun derived() = BackupCrypto.deriveKey(passphrase, BackupCrypto.newSalt(), fastParams)

    @Test
    fun `encrypt then decrypt round-trips the json`() {
        val json = """{"schemaVersion":3,"habits":[]}"""
        val container = BackupCrypto.encrypt(json, derived())

        val decrypted = BackupCrypto.decrypt(container, passphrase)

        assertEquals(json, decrypted)
    }

    @Test
    fun `container starts with the BITO1 magic and isEncrypted sees it`() {
        val container = BackupCrypto.encrypt("""{"a":1}""", derived())

        val magic = container.copyOfRange(0, 5).toString(Charsets.US_ASCII)

        assertEquals("BITO1", magic)
        assertTrue(BackupCrypto.isEncrypted(container))
    }

    @Test
    fun `plain json is not detected as encrypted`() {
        val plain = """{"schemaVersion":3,"habits":[]}""".toByteArray()

        assertFalse(BackupCrypto.isEncrypted(plain))
    }

    @Test
    fun `wrong passphrase throws WrongPassphraseException`() {
        val container = BackupCrypto.encrypt("""{"a":1}""", derived())

        assertFailsWith<WrongPassphraseException> {
            BackupCrypto.decrypt(container, "wrong passphrase entirely".toCharArray())
        }
    }

    @Test
    fun `flipping one ciphertext byte throws WrongPassphraseException`() {
        val container = BackupCrypto.encrypt("""{"a":1}""", derived())
        val tampered = container.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()

        assertFailsWith<WrongPassphraseException> {
            BackupCrypto.decrypt(tampered, passphrase)
        }
    }

    @Test
    fun `header carries salt and params so decrypt needs no external state`() {
        val json = """{"schemaVersion":3,"habits":[]}"""
        val container = BackupCrypto.encrypt(json, derived())

        // decrypt takes only the container bytes and the passphrase — no salt/params passed in.
        val decrypted = BackupCrypto.decrypt(container, passphrase)

        assertEquals(json, decrypted)
    }

    @Test
    fun `two encrypts of the same json differ`() {
        val key = derived()
        val json = """{"a":1}"""

        val first = BackupCrypto.encrypt(json, key)
        val second = BackupCrypto.encrypt(json, key)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `unknown container version throws BackupFormatException`() {
        val container = BackupCrypto.encrypt("""{"a":1}""", derived())
        val tampered = container.copyOf()
        tampered[5] = 2

        assertFailsWith<BackupFormatException> {
            BackupCrypto.decrypt(tampered, passphrase)
        }
    }

    @Test
    fun `truncated container throws BackupFormatException`() {
        val container = BackupCrypto.encrypt("""{"a":1}""", derived())
        val truncated = container.copyOfRange(0, 20) // magic + version + salt, cut before the nonce

        assertFailsWith<BackupFormatException> {
            BackupCrypto.decrypt(truncated, passphrase)
        }
    }

    @Test
    fun `tampered argon2 params byte throws BackupFormatException`() {
        val container = BackupCrypto.encrypt("""{"a":1}""", derived())
        val tampered = container.copyOf()
        tampered[22] = 0x7F.toByte() // flips memoryKib's top byte to a value far past the sane cap

        assertFailsWith<BackupFormatException> {
            BackupCrypto.decrypt(tampered, passphrase)
        }
    }

    @Test
    fun `deriveKey is deterministic for same salt and params`() {
        val salt = BackupCrypto.newSalt()

        val first = BackupCrypto.deriveKey(passphrase, salt, fastParams)
        val second = BackupCrypto.deriveKey(passphrase, salt, fastParams)

        assertContentEquals(first.key, second.key)
    }
}
