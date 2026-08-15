package com.alvarotc.bito.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitEntity::class,
        TargetChangeEntity::class,
        PauseIntervalEntity::class,
        EntryEntity::class,
        DaySealEntity::class,
        FreezerUseEntity::class,
        PointsLedgerEntity::class,
        BadgeEntity::class,
        CustomizationItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BitoDatabase : RoomDatabase() {
    companion object {
        private const val NAME = "bito.db"

        fun build(context: Context): BitoDatabase = Room.databaseBuilder(context, BitoDatabase::class.java, NAME).build()
    }
}
