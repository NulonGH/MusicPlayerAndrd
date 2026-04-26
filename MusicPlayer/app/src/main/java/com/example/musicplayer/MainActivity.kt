package com.example.musicplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val PrimaryPurple = Color(0xFFBB9EFF)
val BackgroundDark = Color(0xFF170529)
val SurfaceDark = Color(0xFF250E3B)

class MainActivity : ComponentActivity() {

    private val tracks = mutableStateListOf<Track>()

    private val folderSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scanFolder(it)
        }
    }

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
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            MusicPlayerApp(
                                tracks = tracks,
                                navController = navController,
                                onSelectFolder = { folderSelectionLauncher.launch(null) }
                            )
                        }
                        composable("search") {
                            SearchScreen(tracks = tracks, navController = navController)
                        }
                    }
                }
            }
        }
    }

    private fun loadTracks() {
        tracks.clear()
        tracks.addAll(MediaStoreHelper.getTracks(this))
    }

    private fun scanFolder(treeUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val newTracks = mutableListOf<Track>()
            val documentFile = DocumentFile.fromTreeUri(this@MainActivity, treeUri)
            if (documentFile != null) {
                traverseFolder(documentFile, newTracks)
            }
            withContext(Dispatchers.Main) {
                tracks.clear()
                tracks.addAll(newTracks)
            }
        }
    }

    private fun traverseFolder(folder: DocumentFile, tracksOut: MutableList<Track>) {
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                traverseFolder(file, tracksOut)
            } else if (file.isFile && (file.type?.startsWith("audio/") == true || file.name?.endsWith(".mp3") == true)) {
                extractTrackMetadata(file)?.let { tracksOut.add(it) }
            }
        }
    }

    private fun extractTrackMetadata(file: DocumentFile): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, file.uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: file.name ?: "Unknown Title"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            Track(
                id = file.uri.hashCode().toLong(),
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                dataPath = file.uri.toString()
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) { }
        }
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
fun MusicPlayerApp(tracks: List<Track>, navController: NavController, onSelectFolder: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Onyx Rhythm", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSelectFolder) {
                        Icon(Icons.Default.Add, contentDescription = "Select Folder")
                    }
                    IconButton(onClick = { navController.navigate("search") }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
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

            when (selectedTab) {
                0 -> SongsList(tracks)
                1 -> AlbumsList(tracks)
                2 -> ArtistsList(tracks)
                3 -> PlaylistsList()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(tracks: List<Track>, navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = tracks.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.artist.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search songs, artists...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = PrimaryPurple,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (filteredTracks.isEmpty() && searchQuery.isNotEmpty()) {
                item {
                    Text("No results found", color = Color.Gray, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(filteredTracks) { track ->
                    SongItem(title = track.title, artist = track.artist)
                }
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
                Text("No songs found. Tap the + icon to scan a directory.", color = Color.Gray, modifier = Modifier.padding(16.dp))
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
