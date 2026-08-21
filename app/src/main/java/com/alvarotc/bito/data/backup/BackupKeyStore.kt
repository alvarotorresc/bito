package com.alvarotc.bito.data.backup

import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * Private key store for the derived backup key.
 * Layout: salt(16) · memoryKib(4) · iterations(4) · parallelism(4) · key(32) = 60 bytes total.
 * Ints are big-endian. The file is never exported and stays on-device only.
 */
class BackupKeyStore(private val dir: File) {
    private val keyFile = File(dir, "backup.key")
    private val tmpFile = File(dir, "backup.key.tmp")

    fun save(derived: DerivedKey) {
        // Write to tmp first, then rename to avoid leaving a truncated key file on crash.
        tmpFile.outputStream().use { fileOut ->
            DataOutputStream(fileOut).use { out ->
                out.write(derived.salt)
                out.writeInt(derived.params.memoryKib)
                out.writeInt(derived.params.iterations)
                out.writeInt(derived.params.parallelism)
                out.write(derived.key)
            }
        }
        if (!tmpFile.renameTo(keyFile)) {
            tmpFile.delete()
            throw IOException("Failed to rename backup.key.tmp to backup.key")
        }
    }

    fun load(): DerivedKey? {
        if (!keyFile.exists()) return null
        if (keyFile.length() != 60L) {
            // Truncated file; don't try to parse it.
            return null
        }

        return keyFile.inputStream().use { fileIn ->
            val salt = ByteArray(16)
            fileIn.read(salt)

            val memoryKib = fileIn.readInt()
            val iterations = fileIn.readInt()
            val parallelism = fileIn.readInt()

            val key = ByteArray(32)
            fileIn.read(key)

            DerivedKey(
                key = key,
                salt = salt,
                params = Argon2Params(memoryKib = memoryKib, iterations = iterations, parallelism = parallelism),
            )
        }
    }

    fun clear() {
        keyFile.delete()
    }

    private fun java.io.InputStream.readInt(): Int {
        val bytes = ByteArray(4)
        read(bytes)
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }
}
