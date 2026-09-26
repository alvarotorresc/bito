package com.alvarotc.bito.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2 (M10): las dos tablas de tareas. Solo CREA — ninguna fila existente se toca, ninguna
 * columna cambia: es la migracion mas barata que existe y conviene que la primera del proyecto
 * sea asi.
 *
 * El DDL esta copiado LITERAL de app/schemas/.../2.json (sustituyendo `${TABLE_NAME}` por el
 * nombre real). Si no coincide caracter a caracter, Room aborta al abrir con
 * IllegalStateException («Migration didn't properly handle...») y la app NO ARRANCA para quien
 * ya tenga la 1.0.0 instalada. Nada de fallbackToDestructiveMigration: eso borraria sus datos.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`firstStep` TEXT, `dueKind` TEXT NOT NULL, `dueDay` INTEGER, `status` TEXT NOT NULL, " +
                    "`createdAtMillis` INTEGER NOT NULL, `createdOnDay` INTEGER NOT NULL, " +
                    "`doneAtMillis` INTEGER, `doneOnDay` INTEGER, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `task_events` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `logicalDay` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`), FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_task_events_taskId_logicalDay` " +
                    "ON `task_events` (`taskId`, `logicalDay`)",
            )
        }
    }

/**
 * 2 -> 3 (M11): la tabla de sesiones de respiracion. Solo CREA — ninguna fila existente se toca,
 * ninguna columna cambia.
 *
 * El DDL esta copiado LITERAL de app/schemas/.../3.json (sustituyendo `${TABLE_NAME}` por el
 * nombre real). Si no coincide caracter a caracter, Room aborta al abrir con
 * IllegalStateException («Migration didn't properly handle...») y la app NO ARRANCA para quien
 * ya tenga la 1.1.0 instalada. Nada de fallbackToDestructiveMigration: eso borraria sus datos.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `breathing_sessions` (`id` TEXT NOT NULL, `mode` TEXT NOT NULL, " +
                    "`startedAtMillis` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, " +
                    "`completed` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }
