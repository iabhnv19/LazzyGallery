package com.lazzy.gallery.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class MediaIdModified(val id: Long, val dateModified: Long)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_index WHERE isTrashed = 0 AND isHidden = 0 ORDER BY dateAdded DESC")
    fun getAllMediaFlow(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_index WHERE isTrashed = 0 AND isHidden = 0 ORDER BY dateAdded DESC")
    suspend fun getAllMedia(): List<MediaEntity>

    @Query("SELECT * FROM media_index WHERE isTrashed = 0 AND isHidden = 0 ORDER BY dateAdded DESC")
    fun getAllMediaBlocking(): List<MediaEntity>

    @Query("SELECT * FROM media_index WHERE isTrashed = 0 AND isHidden = 0 ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentMediaBlocking(limit: Int): List<MediaEntity>

    @Query("SELECT id, dateModified FROM media_index")
    suspend fun getExistingIdAndModified(): List<MediaIdModified>

    @Query("SELECT COUNT(*) FROM media_index WHERE isTrashed = 0")
    suspend fun getCount(): Int

    @Query("SELECT * FROM media_index WHERE isTrashed = 1 ORDER BY dateAdded DESC")
    suspend fun getTrashedMedia(): List<MediaEntity>

    @Query("SELECT * FROM media_index WHERE albumName = :albumName AND isTrashed = 0 AND isHidden = 0 ORDER BY dateAdded DESC")
    suspend fun getMediaByAlbum(albumName: String): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaList: List<MediaEntity>)

    @Query("DELETE FROM media_index WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Removes rows for media no longer present in MediaStore. */
    @Query("DELETE FROM media_index WHERE id NOT IN (:activeIds)")
    suspend fun deleteStaleIds(activeIds: List<Long>)

    @Query("DELETE FROM media_index")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(entities: List<MediaEntity>) {
        clearAll()
        insertAll(entities)
    }

    @Transaction
    suspend fun syncIncremental(entities: List<MediaEntity>) {
        if (entities.isEmpty()) return
        insertAll(entities)
        deleteStaleIds(entities.map { it.id })
    }

    @Query("UPDATE media_index SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE media_index SET isHidden = :isHidden WHERE id = :id")
    suspend fun updateHidden(id: Long, isHidden: Boolean)

    @Query("UPDATE media_index SET isTrashed = :isTrashed WHERE id IN (:ids)")
    suspend fun updateTrashed(ids: List<Long>, isTrashed: Boolean)

    @Query("UPDATE media_index SET ocrText = :text WHERE id = :id")
    suspend fun updateOcrText(id: Long, text: String)

    @Query("SELECT COUNT(*) FROM media_index WHERE ocrText IS NOT NULL AND ocrText != ''")
    suspend fun getOcrIndexedCount(): Int

    @Query("UPDATE media_index SET ocrText = NULL")
    suspend fun clearAllOcrData()
}
