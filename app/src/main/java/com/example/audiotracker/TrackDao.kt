package com.example.audiotracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow // <--- Важный импорт

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE artist = :artist AND title = :title LIMIT 1")
    fun findTrack(artist: String, title: String): Track?

    @Insert
    fun insert(track: Track)

    @Update
    fun update(track: Track)

    // Изменили List на Flow. Теперь список будет обновляться сам!
    @Query("SELECT * FROM tracks ORDER BY playCount DESC")
    fun getAll(): Flow<List<Track>>
}