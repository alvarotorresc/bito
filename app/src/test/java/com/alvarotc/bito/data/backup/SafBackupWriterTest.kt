package com.alvarotc.bito.data.backup

import org.junit.Test
import kotlin.test.assertEquals

class SafBackupWriterTest {
    @Test
    fun `keeps the newest N by name`() {
        val names =
            listOf(
                "bito-backup-2026-08-15-1000.bito",
                "bito-backup-2026-08-16-1000.bito",
                "bito-backup-2026-08-17-1000.bito",
            )

        val victims = rotationVictims(names, keep = 1)

        // Names sort desc (newest first); the surviving victims stay in that same order.
        assertEquals(
            listOf(
                "bito-backup-2026-08-16-1000.bito",
                "bito-backup-2026-08-15-1000.bito",
            ),
            victims,
        )
    }

    @Test
    fun `deletes nothing at or under the cap`() {
        val names =
            listOf(
                "bito-backup-2026-08-15-1000.bito",
                "bito-backup-2026-08-16-1000.bito",
            )

        assertEquals(emptyList(), rotationVictims(names, keep = 2))
        assertEquals(emptyList(), rotationVictims(names, keep = 5))
    }

    @Test
    fun `ignores names that are not bito backups`() {
        val names =
            listOf(
                "bito-backup-2026-08-15-1000.bito",
                "vacaciones.jpg",
                "bito-backup-notas.txt",
                "bito-backup-2026-08-16-1000.bito.tmp",
            )

        val victims = rotationVictims(names, keep = 0)

        assertEquals(listOf("bito-backup-2026-08-15-1000.bito"), victims)
    }

    @Test
    fun `keep of five on seven backups deletes the two oldest`() {
        val names =
            listOf(
                "bito-backup-2026-08-10-1000.bito",
                "bito-backup-2026-08-11-1000.bito",
                "bito-backup-2026-08-12-1000.bito",
                "bito-backup-2026-08-13-1000.bito",
                "bito-backup-2026-08-14-1000.bito",
                "bito-backup-2026-08-15-1000.bito",
                "bito-backup-2026-08-16-1000.bito",
            )

        val victims = rotationVictims(names, keep = 5)

        assertEquals(
            listOf(
                "bito-backup-2026-08-11-1000.bito",
                "bito-backup-2026-08-10-1000.bito",
            ),
            victims,
        )
    }

    @Test
    fun `deletes the previous file with the same final name`() {
        val names = listOf("bito-backup-2026-08-21-1200.bito", "bito-backup-2026-08-20-1000.bito")

        val stale = staleWriteTargets(names, fileName = "bito-backup-2026-08-21-1200.bito")

        assertEquals(listOf("bito-backup-2026-08-21-1200.bito"), stale)
    }

    @Test
    fun `deletes an orphaned tmp from any earlier run`() {
        val names = listOf("bito-backup-2026-08-20-1000.bito.tmp")

        val stale = staleWriteTargets(names, fileName = "bito-backup-2026-08-21-1200.bito")

        assertEquals(listOf("bito-backup-2026-08-20-1000.bito.tmp"), stale)
    }

    @Test
    fun `leaves another app's tmp untouched`() {
        val names = listOf("notas.txt.tmp", "vacaciones.jpg")

        val stale = staleWriteTargets(names, fileName = "bito-backup-2026-08-21-1200.bito")

        assertEquals(emptyList(), stale)
    }
}
