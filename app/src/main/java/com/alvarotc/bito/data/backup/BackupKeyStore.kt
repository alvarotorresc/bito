package com.alvarotc.bito.data.backup

import java.io.DataInputStream
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

        return try {
            keyFile.inputStream().use { fileIn ->
                DataInputStream(fileIn).use { din ->
                    val salt = ByteArray(16)
                    din.readFully(salt)

                    val memoryKib = din.readInt()
                    val iterations = din.readInt()
                    val parallelism = din.readInt()

                    val key = ByteArray(32)
                    din.readFully(key)

                    DerivedKey(
                        key = key,
                        salt = salt,
                        params = Argon2Params(memoryKib = memoryKib, iterations = iterations, parallelism = parallelism),
                    )
                }
            }
        } catch (e: IOException) {
            // Corrupt local key file (including truncation mid-read) reads as "no key",
            // matching the DerivedKey? contract — no crash.
            null
        }
    }

    fun clear() {
        keyFile.delete()
    }
}
