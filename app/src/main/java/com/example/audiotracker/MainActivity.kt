package com.example.audiotracker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.audiotracker.ui.theme.AudioTrackerTheme
import java.util.concurrent.TimeUnit
import androidx.compose.ui.unit.TextUnit
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
    val currentTrack by viewModel.currentTrack.collectAsState()
    var isPlayerOpen by remember { mutableStateOf(false) }

    if (isPlayerOpen && currentTrack != null) {
        FullPlayerScreen(viewModel, onClose = { isPlayerOpen = false })
    } else {
        Scaffold(
            bottomBar = {
                Column {
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

// === КРАСИВЫЙ ЗАГОЛОВОК С ПОИСКОМ ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableSearchBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Если закрыли поиск - очищаем текст и убираем фокус
    fun closeSearch() {
        isSearchActive = false
        onSearchTextChange("")
        focusManager.clearFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(targetState = isSearchActive, label = "search_anim") { active ->
            if (active) {
                // ПОЛЕ ПОИСКА
                TextField(
                    value = searchText,
                    onValueChange = onSearchTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Поиск музыки...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    leadingIcon = {
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { onSearchTextChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
                // Авто-фокус при открытии
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                // ОБЫЧНЫЙ ЗАГОЛОВОК
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Мои треки 🎵", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск", modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

// === БЕГУЩАЯ СТРОКА (MARQUEE) ===
@OptIn(ExperimentalFoundationApi::class) // Нужен для basicMarquee
@Composable
fun MarqueeText(
    text: String,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1,
        modifier = Modifier.basicMarquee() // <--- МАГИЯ БЕГУЩЕЙ СТРОКИ
    )
}


@Composable
fun MusicListScreen(viewModel: MainViewModel) {
    // ВАЖНО: Подписываемся на musicList (это уже отфильтрованный список!)
    val musicList by viewModel.musicList.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val searchText by viewModel.searchQuery.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // Вставляем наш новый поиск
        ExpandableSearchBar(
            searchText = searchText,
            onSearchTextChange = { viewModel.onSearchTextChange(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (musicList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchText.isNotEmpty()) "Ничего не найдено 😢" else "Ищу музыку...",
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(musicList) { file ->
                    val isPlayingThis = currentTrack?.id == file.id
                    val artUri = viewModel.getAlbumArtUri(file.albumId)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlayingThis) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.playTrack(file) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(artUri).error(R.drawable.ic_launcher_foreground).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                // ИСПОЛЬЗУЕМ БЕГУЩУЮ СТРОКУ
                                MarqueeText(
                                    text = file.title,
                                    fontWeight = FontWeight.Bold,
                                    color = if(isPlayingThis) MaterialTheme.colorScheme.primary else Color.Unspecified
                                )
                                MarqueeText(
                                    text = file.artist,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, "Закрыть", modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val artUri = track?.let { viewModel.getAlbumArtUri(it.albumId) } ?: Uri.EMPTY
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(artUri).crossfade(true).error(R.drawable.ic_launcher_foreground).build(),
                contentDescription = "Обложка",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(320.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ТУТ ТОЖЕ БЕГУЩАЯ СТРОКА
            MarqueeText(track?.title ?: "Без названия", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            MarqueeText(track?.artist ?: "Неизвестен", fontSize = 18.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(24.dp))

            Slider(
                value = position.toFloat(),
                onValueChange = { viewModel.seekTo(it) },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position.toLong()), color = Color.Gray, fontSize = 12.sp)
                Text(formatTime(duration.toLong()), color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleRepeat() }) { Icon(Icons.Default.Repeat, null, tint = if(isRepeat) MaterialTheme.colorScheme.primary else Color.Gray) }
                IconButton(onClick = { viewModel.skipPrevious() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(32.dp)) }
                FloatingActionButton(onClick = { viewModel.togglePlayPause() }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { viewModel.skipNext() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = { viewModel.toggleShuffle() }) { Icon(Icons.Default.Shuffle, null, tint = if(isShuffle) MaterialTheme.colorScheme.primary else Color.Gray) }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun MiniPlayer(viewModel: MainViewModel, onClick: () -> Unit) {
    val track by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val artUri = track?.let { viewModel.getAlbumArtUri(it.albumId) } ?: Uri.EMPTY

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(artUri).crossfade(true).error(R.drawable.ic_launcher_foreground).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Gray)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // И ЗДЕСЬ БЕГУЩАЯ СТРОКА
                MarqueeText(track?.title ?: "", fontWeight = FontWeight.Bold)
                MarqueeText(track?.artist ?: "", fontSize = 12.sp)
            }
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d", minutes, seconds)
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
                            // И ДАЖЕ В СТАТИСТИКЕ
                            MarqueeText(track.title, fontWeight = FontWeight.Bold)
                            MarqueeText(track.artist, fontSize = 14.sp)
                        }
                        Text("${track.playCount} раз", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}