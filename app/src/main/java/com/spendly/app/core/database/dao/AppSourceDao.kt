package com.spendly.app.core.database.dao

import androidx.room.*
import com.spendly.app.core.database.entity.AppSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSourceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sources: List<AppSourceEntity>)

    @Update
    suspend fun update(source: AppSourceEntity)

    @Query("SELECT * FROM app_sources ORDER BY display_name ASC")
    fun observeAll(): Flow<List<AppSourceEntity>>

    @Query("SELECT * FROM app_sources WHERE is_enabled = 1")
    suspend fun getEnabledSources(): List<AppSourceEntity>

    @Query("SELECT is_enabled FROM app_sources WHERE package_name = :pkg LIMIT 1")
    suspend fun isEnabled(pkg: String): Boolean?

    @Query("UPDATE app_sources SET is_enabled = :enabled WHERE package_name = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)
}
