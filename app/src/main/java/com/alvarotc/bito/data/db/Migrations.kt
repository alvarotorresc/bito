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

/**
 * 3 -> 4: el catálogo de Habi cambia de ids y de ejes. Ninguna tabla cambia de forma, pero una fila
 * anterior de customization_items con PATTERN, UPPER o LOWER haría fallar a Room al leer el enum.
 * El catálogo no se conserva entre esas versiones: se vacía SOLO esa tabla. Hábitos, registros, puntos, logros, tareas y respiración no se tocan. Los puntos
 * gastados en artículos viejos no se devuelven.
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM `customization_items`")
        }
    }

/**
 * 4 -> 5 (pieza 4): las cinco tablas de la memoria de Habi (habi_memoria, lo_gordo,
 * aviso_aceptado, propuesta_vigente, habla_usada) y sus indices. Solo CREA — ninguna fila
 * existente se toca, ninguna columna cambia.
 *
 * El DDL esta copiado LITERAL de app/schemas/.../5.json (sustituyendo `${TABLE_NAME}` por el
 * nombre real). Si no coincide caracter a caracter, Room aborta al abrir con
 * IllegalStateException («Migration didn't properly handle...») y la app NO ARRANCA para quien
 * ya tenga la version anterior instalada. Nada de fallbackToDestructiveMigration: eso borraria
 * sus datos.
 */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habi_memoria` (`id` TEXT NOT NULL, `tipo` TEXT NOT NULL, " +
                    "`dia` INTEGER NOT NULL, `ref` TEXT, `valor` INTEGER, `creado_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_habi_memoria_tipo_dia` ON `habi_memoria` (`tipo`, `dia`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `lo_gordo` (`dia` INTEGER NOT NULL, `posicion` INTEGER NOT NULL, " +
                    "`tipo` TEXT NOT NULL, `ref_id` TEXT NOT NULL, `creado_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`dia`, `posicion`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lo_gordo_ref_id` ON `lo_gordo` (`ref_id`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `aviso_aceptado` (`id` TEXT NOT NULL, `dia` INTEGER NOT NULL, " +
                    "`habit_id` TEXT NOT NULL, `minuto` INTEGER NOT NULL, `pospuesto` INTEGER NOT NULL, " +
                    "`estado` TEXT NOT NULL, `propuesta_id` TEXT NOT NULL, `creado_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_aviso_aceptado_dia` ON `aviso_aceptado` (`dia`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `propuesta_vigente` (`ambito` TEXT NOT NULL, " +
                    "`habit_id` TEXT NOT NULL, `dia` INTEGER NOT NULL, `situacion` TEXT NOT NULL, `ref` TEXT, " +
                    "`trozos` TEXT NOT NULL, `huecos` TEXT NOT NULL, `acciones` TEXT NOT NULL, `hora` INTEGER, " +
                    "`creado_millis` INTEGER NOT NULL, PRIMARY KEY(`ambito`, `habit_id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habla_usada` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`voz` TEXT NOT NULL, `bolsa` TEXT NOT NULL, `indice` INTEGER NOT NULL, " +
                    "`combinacion` TEXT NOT NULL, `dia` INTEGER NOT NULL, `usado_millis` INTEGER NOT NULL, " +
                    "`situacion` TEXT)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_habla_usada_voz_bolsa_usado_millis` " +
                    "ON `habla_usada` (`voz`, `bolsa`, `usado_millis`)",
            )
        }
    }

/**
 * 5 -> 6 (pieza 5): la tabla de sesiones de foco y su indice. Solo CREA — ninguna fila existente
 * se toca, ninguna columna cambia.
 *
 * El DDL esta copiado LITERAL de app/schemas/.../6.json (sustituyendo `${TABLE_NAME}` por el
 * nombre real). Si no coincide caracter a caracter, Room aborta al abrir con
 * IllegalStateException («Migration didn't properly handle...») y la app NO ARRANCA para quien
 * ya tenga la version anterior instalada. Nada de fallbackToDestructiveMigration: eso borraria
 * sus datos.
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sesiones` (`id` TEXT NOT NULL, `objetivo_tipo` TEXT NOT NULL, " +
                    "`objetivo_id` TEXT, `dia` INTEGER NOT NULL, `inicio_millis` INTEGER NOT NULL, " +
                    "`fin_millis` INTEGER NOT NULL, `minutos_foco` INTEGER NOT NULL, " +
                    "`bloques_hechos` INTEGER NOT NULL, `bloques_plan` INTEGER NOT NULL, " +
                    "`resultado` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_sesiones_objetivo_id_dia` ON `sesiones` (`objetivo_id`, `dia`)",
            )
        }
    }
