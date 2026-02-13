package com.example.audiotracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audiotracker.ui.theme.AudioTrackerTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) viewModel.loadDeviceMusic() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkStoragePermissions()
        setContent {
            AudioTrackerTheme { MainScreen(viewModel) }
        }
    }

    private fun checkStoragePermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) viewModel.loadDeviceMusic()
        else requestPermissionLauncher.launch(permission)
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    // Следим, играет ли что-то, чтобы показать мини-плеер или полный экран
    val currentTrack by viewModel.currentTrack.collectAsState()
    var isPlayerOpen by remember { mutableStateOf(false) } // Открыт ли плеер на весь экран?

    if (isPlayerOpen && currentTrack != null) {
        // Показываем ПОЛНЫЙ ПЛЕЕР
        FullPlayerScreen(viewModel, onClose = { isPlayerOpen = false })
    } else {
        // Показываем обычный интерфейс с табами
        Scaffold(
            bottomBar = {
                Column {
                    // Мини-плеер над меню (если музыка выбрана)
                    if (currentTrack != null) {
                        MiniPlayer(viewModel, onClick = { isPlayerOpen = true })
                    }
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.List, null) },
                            label = { Text("Треки") },
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Info, null) },
                            label = { Text("Статистика") },
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> MusicListScreen(viewModel)
                    1 -> StatsScreen(viewModel)
                }
            }
        }
    }
}

// === ПОЛНЫЙ ЭКРАН ПЛЕЕРА ===
@Composable
fun FullPlayerScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val isRepeat by viewModel.isRepeat.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Кнопка "Свернуть"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Закрыть", modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 1. АРТ (Большая иконка)
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(150.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Название и Артист
            Text(track?.title ?: "Без названия", fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(track?.artist ?: "Неизвестен", fontSize = 18.sp, color = Color.Gray, maxLines = 1)

            Spacer(modifier = Modifier.height(24.dp))

            // 2. ПОЛОСКА ПРОГРЕССА (Slider)
            Slider(
                value = position.toFloat(),
                onValueChange = { viewModel.seekTo(it) },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )

            // 3. ВРЕМЯ (Слева и Справа)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position.toLong()), color = Color.Gray, fontSize = 12.sp)
                Text(formatTime(duration.toLong()), color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. КНОПКИ УПРАВЛЕНИЯ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Повтор
                IconButton(onClick = { viewModel.toggleRepeat() }) {
                    Icon(Icons.Default.Repeat, null, tint = if(isRepeat) MaterialTheme.colorScheme.primary else Color.Gray)
                }
                // Назад
                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(32.dp))
                }
                // ПЛЕЙ / ПАУЗА (Большая кнопка)
                FloatingActionButton(
                    onClick = { viewModel.togglePlayPause() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
                // Вперед
                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(32.dp))
                }
                // Шафл
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, null, tint = if(isShuffle) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// === МИНИ-ПЛЕЕР (Висит над меню) ===
@Composable
fun MiniPlayer(viewModel: MainViewModel, onClick: () -> Unit) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() }, // По клику открываем полный экран
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track?.title ?: "", fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track?.artist ?: "", fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

// Форматирование времени (03:45)
fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// --- СТАРЫЕ ЭКРАНЫ (Список и Статистика) ---
@Composable
fun MusicListScreen(viewModel: MainViewModel) {
    val musicList by viewModel.deviceMusic.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Мои треки 🎵", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (musicList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ищу музыку...", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(musicList) { file ->
                    val isPlayingThis = currentTrack?.id == file.id
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.playTrack(file) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(file.title, fontWeight = FontWeight.Bold, color = if(isPlayingThis) MaterialTheme.colorScheme.primary else Color.Unspecified, maxLines = 1)
                            Text(file.artist, fontSize = 14.sp, color = Color.Gray, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val tracks by viewModel.statsTracks.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Топ прослушиваний 📊", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (tracks.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Пока пусто", color = Color.Gray) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tracks) { track ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, fontWeight = FontWeight.Bold)
                            Text(track.artist, fontSize = 14.sp)
                        }
                        Text("${track.playCount} раз", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}