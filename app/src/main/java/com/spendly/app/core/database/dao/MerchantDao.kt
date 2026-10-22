package com.spendly.app.core.database.dao

import androidx.room.*
import com.spendly.app.core.database.entity.MerchantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(merchant: MerchantEntity): Long

    @Update
    suspend fun update(merchant: MerchantEntity)

    @Query("SELECT * FROM merchants ORDER BY usage_count DESC LIMIT :limit")
    fun observeTopMerchants(limit: Int = 20): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE name LIKE '%' || :query || '%' ORDER BY usage_count DESC LIMIT 10")
    suspend fun searchMerchants(query: String): List<MerchantEntity>

    @Query("SELECT * FROM merchants WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): MerchantEntity?

    @Query("UPDATE merchants SET usage_count = usage_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long = System.currentTimeMillis())
}
