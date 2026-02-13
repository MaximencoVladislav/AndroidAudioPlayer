package com.example.audiotracker

import android.app.Application
import android.content.ContentUris
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val duration: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null // Работа для обновления таймера

    // Данные для UI
    val statsTracks = db.trackDao().getAll()
    private val _deviceMusic = MutableStateFlow<List<AudioFile>>(emptyList())
    val deviceMusic = _deviceMusic.asStateFlow()

    // СОСТОЯНИЕ ПЛЕЕРА
    private val _currentTrack = MutableStateFlow<AudioFile?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0) // Текущая секунда (мс)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0) // Общая длина (мс)
    val duration = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false) // Шафл
    val isShuffle = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false) // Повтор
    val isRepeat = _isRepeat.asStateFlow()

    // 1. ЗАПУСК ТРЕКА
    fun playTrack(file: AudioFile) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(getApplication(), file.uri)
                prepare()
                start()
                setOnCompletionListener { onTrackComplete() }
            }

            _currentTrack.value = file
            _duration.value = mediaPlayer?.duration ?: 0
            _isPlaying.value = true

            startProgressTracker() // Запускаем таймер
            addToStatistics(file)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. ПАУЗА / ПРОДОЛЖИТЬ
    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            } else {
                player.start()
                _isPlaying.value = true
                startProgressTracker()
            }
        }
    }

    // 3. ПЕРЕМОТКА (Seek bar)
    fun seekTo(position: Float) {
        mediaPlayer?.seekTo(position.toInt())
        _currentPosition.value = position.toInt()
    }

    // 4. СЛЕДУЮЩИЙ / ПРЕДЫДУЩИЙ ТРЕК
    fun skipNext() {
        val list = _deviceMusic.value
        val current = _currentTrack.value ?: return
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current.id }
        val nextIndex = if (_isShuffle.value) {
            list.indices.random() // Случайный трек
        } else {
            if (currentIndex + 1 < list.size) currentIndex + 1 else 0 // Следующий или первый
        }
        playTrack(list[nextIndex])
    }

    fun skipPrevious() {
        val list = _deviceMusic.value
        val current = _currentTrack.value ?: return
        val currentIndex = list.indexOfFirst { it.id == current.id }

        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else list.lastIndex
        playTrack(list[prevIndex])
    }

    // 5. УПРАВЛЕНИЕ РЕЖИМАМИ
    fun toggleShuffle() { _isShuffle.value = !_isShuffle.value }
    fun toggleRepeat() { _isRepeat.value = !_isRepeat.value }

    // Логика окончания трека
    private fun onTrackComplete() {
        if (_isRepeat.value) {
            mediaPlayer?.start() // Повторяем этот же
        } else {
            skipNext() // Играем следующий
        }
    }

    // Таймер обновления полоски (тикает раз в секунду)
    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                if (mediaPlayer?.isPlaying == true) {
                    _currentPosition.value = mediaPlayer?.currentPosition ?: 0
                }
                delay(1000)
            }
        }
    }

    // (Остальной код: addToStatistics, loadDeviceMusic, onCleared - оставляем как было)
    private fun addToStatistics(file: AudioFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = db.trackDao()
            val existing = dao.findTrack(file.artist, file.title)
            if (existing == null) dao.insert(Track(artist = file.artist, title = file.title))
            else dao.update(existing.copy(playCount = existing.playCount + 1, lastPlayed = System.currentTimeMillis()))
        }
    }

    fun loadDeviceMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            val musicList = mutableListOf<AudioFile>()
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION)
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            getApplication<Application>().contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    musicList.add(AudioFile(id, cursor.getString(titleCol), cursor.getString(artistCol) ?: "<Unknown>", uri, cursor.getInt(durCol)))
                }
            }
            _deviceMusic.value = musicList
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        progressJob?.cancel()
    }
}