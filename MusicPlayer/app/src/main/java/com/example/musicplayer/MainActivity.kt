package com.example.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

val PrimaryPurple = Color(0xFFBB9EFF)
val BackgroundDark = Color(0xFF170529)
val SurfaceDark = Color(0xFF250E3B)

class MainActivity : ComponentActivity() {

    private val tracks = mutableStateListOf<Track>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadTracks()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = PrimaryPurple,
                    background = BackgroundDark,
                    surface = SurfaceDark,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayerApp(tracks = tracks)
                }
            }
        }
    }

    private fun loadTracks() {
        tracks.clear()
        tracks.addAll(MediaStoreHelper.getTracks(this))
    }

    private fun requestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(permission)
        } else {
            loadTracks()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(tracks: List<Track>) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...") },
                            singleLine = true,
                            colors = TextFieldDefaults.textFieldColors(
                                containerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = PrimaryPurple,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("Onyx Rhythm", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            MiniPlayer()
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundDark,
                contentColor = PrimaryPurple,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, color = if (selectedTab == index) PrimaryPurple else Color.Gray) }
                    )
                }
            }

            val filteredTracks = tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }

            when (selectedTab) {
                0 -> SongsList(filteredTracks)
                1 -> AlbumsList(filteredTracks)
                2 -> ArtistsList(filteredTracks)
                3 -> PlaylistsList()
            }
        }
    }
}

@Composable
fun SongsList(tracks: List<Track>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (tracks.isEmpty()) {
            item {
                Text("No songs found", color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
        } else {
            items(tracks) { track ->
                SongItem(title = track.title, artist = track.artist)
            }
        }
    }
}

@Composable
fun AlbumsList(tracks: List<Track>) {
    val albums = tracks.map { it.album }.distinct()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (albums.isEmpty()) {
            item {
                Text("No albums found", color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
        } else {
            items(albums) { album ->
                Text(album, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun ArtistsList(tracks: List<Track>) {
    val artists = tracks.map { it.artist }.distinct()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (artists.isEmpty()) {
             item {
                Text("No artists found", color = Color.Gray, modifier = Modifier.padding(16.dp))
            }
        } else {
            items(artists) { artist ->
                Text(artist, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun PlaylistsList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Favorites", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }
        item {
            Text("Recently Played", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}


@Composable
fun SongItem(title: String, artist: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Text(artist, fontSize = 14.sp, color = Color.Gray)
        }
        IconButton(onClick = { /* TODO */ }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Gray)
        }
    }
}

@Composable
fun MiniPlayer() {
    Surface(
        color = SurfaceDark,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Velvet Thunder", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Luna Eclipse", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = PrimaryPurple)
            }
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next", tint = Color.White)
            }
        }
    }
}
