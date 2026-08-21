package com.alvarotc.bito.data.backup

import org.junit.Test
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupKeyStoreTest {
    @Test
    fun `save then load round-trips key, salt and params`() {
        val dir = createTempDirectory().toFile()
        val store = BackupKeyStore(dir)
        val derived =
            DerivedKey(
                key = ByteArray(32) { i -> i.toByte() },
                salt = ByteArray(16) { i -> (i + 100).toByte() },
                params = Argon2Params(memoryKib = 19456, iterations = 2, parallelism = 1),
            )

        store.save(derived)
        val loaded = store.load()

        assertEquals(derived.params.memoryKib, loaded?.params?.memoryKib)
        assertEquals(derived.params.iterations, loaded?.params?.iterations)
        assertEquals(derived.params.parallelism, loaded?.params?.parallelism)
        assertEquals(derived.salt.toList(), loaded?.salt?.toList())
        assertEquals(derived.key.toList(), loaded?.key?.toList())
    }

    @Test
    fun `load returns null when no file`() {
        val dir = createTempDirectory().toFile()
        val store = BackupKeyStore(dir)

        assertNull(store.load())
    }

    @Test
    fun `clear removes the file`() {
        val dir = createTempDirectory().toFile()
        val store = BackupKeyStore(dir)
        val derived =
            DerivedKey(
                key = ByteArray(32) { i -> i.toByte() },
                salt = ByteArray(16) { i -> (i + 100).toByte() },
                params = Argon2Params(memoryKib = 19456, iterations = 2, parallelism = 1),
            )

        store.save(derived)
        store.clear()

        assertNull(store.load())
    }

    @Test
    fun `load returns null on truncated file`() {
        val dir = createTempDirectory().toFile()
        val keyFile = java.io.File(dir, "backup.key")
        keyFile.writeBytes(ByteArray(40)) // < 60 bytes

        val store = BackupKeyStore(dir)
        assertNull(store.load())
    }
}
