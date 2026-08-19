package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.alvarotc.bito.domain.model.CustomizationCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomizationItemDao {
    @Upsert
    suspend fun upsert(item: CustomizationItemEntity)

    /** Achievement grants: existing rows (and their equipped flag) are never touched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CustomizationItemEntity>)

    @Query("SELECT * FROM customization_items WHERE itemId = :itemId")
    suspend fun byId(itemId: String): CustomizationItemEntity?

    @Query("UPDATE customization_items SET equipped = 0 WHERE category = :category")
    suspend fun unequipCategory(category: CustomizationCategory)

    @Query("SELECT * FROM customization_items")
    fun observeAll(): Flow<List<CustomizationItemEntity>>

    @Query("SELECT * FROM customization_items")
    suspend fun all(): List<CustomizationItemEntity>

    @Query("DELETE FROM customization_items")
    suspend fun deleteAll()
}
