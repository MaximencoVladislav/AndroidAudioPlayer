package com.example.audiotracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val artist: String,
    val title: String,
    val playCount: Int = 1,          // Счетчик прослушиваний
    val lastPlayed: Long = System.currentTimeMillis() // Время последнего прослушивания
)