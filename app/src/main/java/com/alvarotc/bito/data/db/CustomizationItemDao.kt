package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomizationItemDao {
    @Upsert
    suspend fun upsert(item: CustomizationItemEntity)

    @Query("SELECT * FROM customization_items WHERE itemId = :itemId")
    suspend fun byId(itemId: String): CustomizationItemEntity?

    @Query("UPDATE customization_items SET equipped = 0 WHERE category = :category")
    suspend fun unequipCategory(category: CustomizationCategory)

    @Query("SELECT * FROM customization_items")
    fun observeAll(): Flow<List<CustomizationItemEntity>>
}
