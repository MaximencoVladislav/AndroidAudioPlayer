package com.example.audiotracker

import android.app.Application
import android.content.ContentUris
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val duration: Int,
    val albumId: Long
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    val statsTracks = db.trackDao().getAll()

    // 1. ПОЛНЫЙ СПИСОК (ВСЕ ПЕСНИ)
    private val _allMusic = MutableStateFlow<List<AudioFile>>(emptyList())

    // 2. ТЕКСТ ПОИСКА
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // 3. ОТФИЛЬТРОВАННЫЙ СПИСОК (ТО, ЧТО ВИДИТ ПОЛЬЗОВАТЕЛЬ)
    val musicList = combine(_allMusic, _searchQuery) { music, query ->
        if (query.isBlank()) {
            music
        } else {
            music.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    private val _currentTrack = MutableStateFlow<AudioFile?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat = _isRepeat.asStateFlow()

    // === ФУНКЦИЯ ПОИСКА ===
    fun onSearchTextChange(text: String) {
        _searchQuery.value = text
    }

    fun playTrack(file: AudioFile) {
        try {
            _duration.value = file.duration
            _currentTrack.value = file
            _currentPosition.value = 0

            mediaPlayer?.release()
            mediaPlayer = null

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(getApplication(), file.uri)
                prepare()
                start()
                setOnCompletionListener { onTrackComplete() }
            }

            _isPlaying.value = true
            startProgressTracker()
            addToStatistics(file)

        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
        }
    }

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

    fun seekTo(position: Float) {
        mediaPlayer?.seekTo(position.toInt())
        _currentPosition.value = position.toInt()
    }

    fun skipNext() {
        // Важно: переключаем по ТЕКУЩЕМУ (отфильтрованному) списку или по полному?
        // Обычно логичнее играть по тому списку, который виден, но для простоты берем полный
        // Чтобы шафл работал корректно по всей библиотеке
        val list = _allMusic.value
        val current = _currentTrack.value ?: return
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) { playTrack(list[0]); return }

        val nextIndex = if (_isShuffle.value) {
            list.indices.random()
        } else {
            if (currentIndex + 1 < list.size) currentIndex + 1 else 0
        }
        playTrack(list[nextIndex])
    }

    fun skipPrevious() {
        val list = _allMusic.value
        val current = _currentTrack.value ?: return
        if (list.isEmpty()) return

        val currentIndex = list.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) { playTrack(list[0]); return }

        val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else list.lastIndex
        playTrack(list[prevIndex])
    }

    fun toggleShuffle() { _isShuffle.value = !_isShuffle.value }
    fun toggleRepeat() { _isRepeat.value = !_isRepeat.value }

    private fun onTrackComplete() {
        if (_isRepeat.value) mediaPlayer?.start() else skipNext()
    }

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

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            getApplication<Application>().contentResolver.query(
                collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val duration = cursor.getInt(durCol)
                    if (duration > 0) {
                        musicList.add(AudioFile(
                            id = id,
                            title = cursor.getString(titleCol),
                            artist = cursor.getString(artistCol) ?: "<Unknown>",
                            uri = uri,
                            duration = duration,
                            albumId = cursor.getLong(albumIdCol)
                        ))
                    }
                }
            }
            _allMusic.value = musicList // Сохраняем в общий список
        }
    }

    fun getAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        progressJob?.cancel()
    }
}