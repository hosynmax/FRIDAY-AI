package com.musiclove.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executor

private val Ink = Color(0xFF080A0D)
private val Panel = Color(0xFF12161B)
private val Panel2 = Color(0xFF1A2027)
private val Pink = Color(0xFFFF8A24)
private val Violet = Color(0xFFFFAD55)
private val Cyan = Color(0xFF20D69A)
private val Muted = Color(0xFFA4ADB7)

data class Song(
    val id: String, val title: String, val artist: String, val album: String,
    val uri: String, val artwork: String = "", val duration: Long = 0L,
    val online: Boolean = false, val sourcePage: String = "", val license: String = ""
)
private data class NavItem(val title: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { MusicLoveApp() }
    }
}

@Composable
private fun MusicLoveApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("music_love", Context.MODE_PRIVATE) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var current by remember { mutableStateOf<Song?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var volume by remember { mutableFloatStateOf(1f) }
    var tab by remember { mutableIntStateOf(0) }
    var fullPlayer by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf(emptyList<Song>()) }
    var favorites by remember { mutableStateOf(readSongs(prefs.getString("favorites", "[]") ?: "[]")) }
    var recents by remember { mutableStateOf(readSongs(prefs.getString("recents", "[]") ?: "[]")) }
    var playlists by remember { mutableStateOf(readPlaylists(prefs.getString("playlists", "{}") ?: "{}")) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var pendingSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Song>()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("") }
    var sleepTimer by remember { mutableIntStateOf(0) }
    var showSleep by remember { mutableStateOf(false) }
    var sleepJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) library = loadLocalSongs(context)
        else Toast.makeText(context, "برای دیدن آهنگ‌های گوشی، دسترسی صدا لازم است.", Toast.LENGTH_LONG).show()
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { controller = future.get() }
                .onFailure { Toast.makeText(context, "اتصال پخش‌کننده برقرار نشد.", Toast.LENGTH_LONG).show() }
        }, Executor { it.run() })
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            library = loadLocalSongs(context)
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                playing = it.isPlaying
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.takeIf { d -> d > 0 } ?: current?.duration ?: 0L
                volume = it.volume.coerceIn(0f, 1f)
            }
            delay(450)
        }
    }

    fun saveFavorites(list: List<Song>) {
        favorites = list.distinctBy { it.id }
        prefs.edit().putString("favorites", writeSongs(favorites)).apply()
    }
    fun savePlaylists(map: Map<String, List<Song>>) {
        playlists = map
        prefs.edit().putString("playlists", writePlaylists(playlists)).apply()
    }
    fun favorite(song: Song) {
        saveFavorites(if (favorites.any { it.id == song.id }) favorites.filterNot { it.id == song.id } else favorites + song)
    }
    fun addSong(song: Song, name: String) {
        val next = playlists.toMutableMap()
        next[name] = (next[name].orEmpty() + song).distinctBy { it.id }
        savePlaylists(next)
        pendingSong = null
        Toast.makeText(context, "به پلی‌لیست «" + name + "» اضافه شد.", Toast.LENGTH_SHORT).show()
    }
    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        val player = controller
        if (player == null) {
            Toast.makeText(context, "پخش‌کننده در حال آماده‌شدن است؛ دوباره امتحان کن.", Toast.LENGTH_SHORT).show()
            return
        }
        val items = (listOf(song) + queue.filterNot { it.id == song.id }).map { s ->
            val meta = MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .apply { if (s.artwork.isNotBlank()) setArtworkUri(Uri.parse(s.artwork)) }.build()
            MediaItem.Builder().setMediaId(s.id).setUri(Uri.parse(s.uri)).setMediaMetadata(meta).build()
        }
        current = song
        player.setMediaItems(items, 0, 0L)
        player.prepare()
        player.play()
        recents = (listOf(song) + recents.filterNot { it.id == song.id }).take(30)
        prefs.edit().putString("recents", writeSongs(recents)).apply()
    }
    fun scanLibrary() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            library = loadLocalSongs(context)
        } else permission.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }
    fun doSearch() {
        val term = searchText.trim()
        if (term.isBlank() || searching) return
        scope.launch {
            searching = true
            results = emptyList()
            searchError = ""
            val response = withContext(Dispatchers.IO) { searchArchive(term) }
            results = response.first
            searchError = response.second
            searching = false
        }
    }

    val nav = listOf(
        NavItem("خانه", Icons.Default.Home), NavItem("کتابخانه", Icons.Default.LibraryMusic),
        NavItem("جست‌وجو", Icons.Default.Search), NavItem("علاقه‌مندی", Icons.Default.Favorite),
        NavItem("پلی‌لیست", Icons.Default.QueueMusic), NavItem("تنظیمات", Icons.Default.Settings)
    )

    MaterialTheme(colorScheme = darkColorScheme(primary = Pink, secondary = Cyan, background = Ink, surface = Panel)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Ink, Color(0xFF17102D), Color(0xFF102039))))) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Pink, Violet, Cyan))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(27.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MUSIC LOVE", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                            Text("YOUR MUSIC • YOUR MOOD", fontSize = 9.sp, color = Cyan, letterSpacing = 1.5.sp)
                        }
                        Surface(color = Panel2, shape = CircleShape) {
                            Text("3D", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Pink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                },
                bottomBar = {
                    Column {
                        current?.let { song ->
                            MiniPlayer(song, playing, { fullPlayer = true },
                                { if (controller?.isPlaying == true) controller?.pause() else controller?.play() },
                                { controller?.seekToNextMediaItem() })
                        }
                        NavigationBar(containerColor = Color(0xFF080A0D)) {
                            nav.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index; if (index == 1) scanLibrary() },
                                    icon = { Icon(item.icon, item.title) },
                                    label = { Text(item.title, fontSize = 9.sp, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Pink, selectedTextColor = Pink, indicatorColor = Color(0xFF27332F),
                                        unselectedIconColor = Muted, unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (tab) {
                        0 -> HomeScreen(library.size, recents, { tab = 1; scanLibrary() }, { tab = 2 }, { playSong(it, recents) }, ::favorite)
                        1 -> LibraryScreen(library, libraryFilter, { libraryFilter = it }, ::scanLibrary, { playSong(it, library) }, favorites, ::favorite) { pendingSong = it }
                        2 -> SearchScreen(searchText, { searchText = it }, results, searching, searchError, ::doSearch, { playSong(it, results) }, favorites, ::favorite) { pendingSong = it }
                        3 -> TrackListScreen("آهنگ‌های موردعلاقه", "همه آهنگ‌هایی که پسندیدی", favorites, "هنوز آهنگی به علاقه‌مندی‌ها اضافه نکردی.", { playSong(it, favorites) }, favorites, ::favorite) { pendingSong = it }
                        4 -> PlaylistScreen(playlists, selectedPlaylist, { selectedPlaylist = it }, { selectedPlaylist = null }, { createPlaylist = true },
                            { playSong(it, playlists[selectedPlaylist].orEmpty()) }, favorites, ::favorite, { pendingSong = it },
                            { name -> savePlaylists(playlists - name); if (selectedPlaylist == name) selectedPlaylist = null })
                        else -> SettingsScreen(volume, { v -> volume = v; controller?.volume = v }, { showSleep = true }, sleepTimer, ::scanLibrary)
                    }
                }
            }

            if (fullPlayer && current != null) {
                FullPlayerScreen(
                    song = current!!, playing = playing, position = position, duration = duration, volume = volume,
                    onBack = { fullPlayer = false },
                    onToggle = { if (controller?.isPlaying == true) controller?.pause() else controller?.play() },
                    onPrevious = { controller?.seekToPreviousMediaItem() },
                    onNext = { controller?.seekToNextMediaItem() },
                    onSeek = { value -> if (duration > 0) controller?.seekTo((value * duration).toLong()) },
                    onVolume = { v -> volume = v; controller?.volume = v },
                    onShuffle = { controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false) },
                    onRepeat = { controller?.repeatMode = when (controller?.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    } },
                    onSleep = { showSleep = true }
                )
            }
        }

        if (createPlaylist) {
            AlertDialog(
                onDismissRequest = { createPlaylist = false },
                title = { Text("پلی‌لیست جدید") },
                text = { OutlinedTextField(playlistName, { playlistName = it }, label = { Text("نام پلی‌لیست") }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        val name = playlistName.trim()
                        if (name.isNotEmpty()) {
                            if (name !in playlists) savePlaylists(playlists + (name to emptyList()))
                            playlistName = ""
                            createPlaylist = false
                            selectedPlaylist = name
                        }
                    }) { Text("ایجاد") }
                },
                dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("لغو") } },
                containerColor = Panel2
            )
        }

        pendingSong?.let { song ->
            AlertDialog(
                onDismissRequest = { pendingSong = null },
                title = { Text("افزودن به پلی‌لیست") },
                text = {
                    if (playlists.isEmpty()) Text("اول یک پلی‌لیست بساز.")
                    else Column { playlists.keys.forEach { name -> TextButton(onClick = { addSong(song, name) }, modifier = Modifier.fillMaxWidth()) { Text(name) } } }
                },
                confirmButton = { TextButton(onClick = { pendingSong = null }) { Text("بستن") } },
                dismissButton = { TextButton(onClick = { pendingSong = null; createPlaylist = true }) { Text("ساخت پلی‌لیست") } },
                containerColor = Panel2
            )
        }

        if (showSleep) {
            AlertDialog(
                onDismissRequest = { showSleep = false },
                title = { Text("تایمر خاموشی") },
                text = {
                    Column {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            TextButton(onClick = {
                                sleepJob?.cancel()
                                sleepTimer = mins
                                sleepJob = scope.launch { delay(mins * 60_000L); controller?.pause(); sleepTimer = 0 }
                                showSleep = false
                                Toast.makeText(context, "تایمر " + mins + " دقیقه‌ای فعال شد.", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) { Text("$mins دقیقه") }
                        }
                        TextButton(onClick = { sleepJob?.cancel(); sleepTimer = 0; showSleep = false }) { Text("لغو تایمر") }
                    }
                },
                confirmButton = { TextButton(onClick = { showSleep = false }) { Text("بستن") } },
                containerColor = Panel2
            )
        }
    }
}

@Composable
private fun HomeScreen(count: Int, recents: List<Song>, onLibrary: () -> Unit, onSearch: () -> Unit, onPlay: (Song) -> Unit, onFavorite: (Song) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Text("سلام، عاشق موسیقی!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("حال و هوای امروزت رو با موسیقی بساز.", color = Muted, fontSize = 13.sp)
        }
        item {
            Box(Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF6A3517), Color(0xFF202820), Color(0xFF174536)))).padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("YOUR SOUND. YOUR WORLD.", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("موسیقی، با امضای تو", fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("از آهنگ‌های گوشی یا آثار آنلاین دارای مجوز آزاد لذت ببر.", color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onLibrary, colors = ButtonDefaults.buttonColors(containerColor = Pink)) { Icon(Icons.Default.LibraryMusic, null); Spacer(Modifier.width(5.dp)); Text("کتابخانه من") }
                        OutlinedButton(onClick = onSearch, border = BorderStroke(1.dp, Cyan)) { Icon(Icons.Default.Cloud, null, tint = Cyan); Spacer(Modifier.width(4.dp)); Text("آنلاین", color = Color.White) }
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).size(76.dp).background(Brush.radialGradient(listOf(Color(0x88FF6BB1), Color.Transparent)), CircleShape))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("آهنگ‌های گوشی", count.toString(), Icons.Default.MusicNote, Modifier.weight(1f))
                StatCard("اخیراً پخش‌شده", recents.size.toString(), Icons.Default.Repeat, Modifier.weight(1f))
            }
        }
        item { Text("اخیراً گوش دادی", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        if (recents.isEmpty()) item { EmptyState("هنوز آهنگی پخش نکردی", "از کتابخانه گوشی یا جست‌وجوی آنلاین شروع کن.") }
        else items(recents.take(10), key = { it.id }) { SongRow(it, false, onPlay, onFavorite, {}) }
    }
}

@Composable
private fun LibraryScreen(songs: List<Song>, filter: String, onFilter: (String) -> Unit, onRescan: () -> Unit,
    onPlay: (Song) -> Unit, favorites: List<Song>, onFavorite: (Song) -> Unit, onAdd: (Song) -> Unit) {
    val filtered = remember(songs, filter) { songs.filter { it.title.contains(filter, true) || it.artist.contains(filter, true) || it.album.contains(filter, true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                val count = songs.size
                Text("کتابخانه آفلاین", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("$count آهنگ در گوشی", color = Muted, fontSize = 12.sp)
            }
            OutlinedButton(onClick = onRescan) { Text("اسکن دوباره", fontSize = 11.sp) }
        }
        OutlinedTextField(filter, onFilter, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("جست‌وجو در آهنگ‌ها") },
            leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(18.dp))
        Spacer(Modifier.height(10.dp))
        if (songs.isEmpty()) EmptyState("هنوز آهنگی پیدا نشد", "دسترسی موسیقی را بده و دوباره اسکن کن.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(filtered, key = { it.id }) { SongRow(it, favorites.any { fav -> fav.id == it.id }, onPlay, onFavorite, onAdd) }
        }
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, results: List<Song>, loading: Boolean, error: String,
    onSearch: () -> Unit, onPlay: (Song) -> Unit, favorites: List<Song>, onFavorite: (Song) -> Unit, onAdd: (Song) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("جست‌وجوی آنلاین", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Text("پیدا کردن موسیقی با مجوز آزاد", color = Cyan, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, onQuery, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("نام آهنگ یا هنرمند") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(18.dp))
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = onSearch, enabled = !loading && query.isNotBlank(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Pink)) { Icon(Icons.Default.Search, "جست‌وجو") }
        }
        Text("منبع: Internet Archive • آثار دارای مجوز CC0، CC BY و CC BY-SA", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 10.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pink) }
            error.isNotBlank() -> Text(error, color = Color(0xFFFF9B9B), modifier = Modifier.padding(8.dp))
            results.isEmpty() -> EmptyState("موسیقی موردنظرت رو پیدا کن", "نام آهنگ یا خواننده را وارد کن و جست‌وجو بزن.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results, key = { it.id }) { SongRow(it, favorites.any { f -> f.id == it.id }, onPlay, onFavorite, onAdd) }
            }
        }
    }
}

@Composable
private fun TrackListScreen(title: String, subtitle: String, songs: List<Song>, emptyText: String, onPlay: (Song) -> Unit,
    favorites: List<Song>, onFavorite: (Song) -> Unit, onAdd: (Song) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        if (songs.isEmpty()) EmptyState("لیست خالی است", emptyText)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(songs, key = { it.id }) { SongRow(it, favorites.any { f -> f.id == it.id }, onPlay, onFavorite, onAdd) }
        }
    }
}

@Composable
private fun PlaylistScreen(playlists: Map<String, List<Song>>, selected: String?, onSelect: (String) -> Unit, onBack: () -> Unit,
    onCreate: () -> Unit, onPlay: (Song) -> Unit, favorites: List<Song>, onFavorite: (Song) -> Unit, onAdd: (Song) -> Unit,
    onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") }
                Column {
                    val count = playlists[selected].orEmpty().size
                    Text(selected, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("$count آهنگ", color = Muted, fontSize = 11.sp)
                }
            }
            val songs = playlists[selected].orEmpty()
            if (songs.isEmpty()) EmptyState("این پلی‌لیست خالی است", "از دکمه افزودن کنار هر آهنگ استفاده کن.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(songs, key = { it.id }) { SongRow(it, favorites.any { f -> f.id == it.id }, onPlay, onFavorite, onAdd) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)) {
                Column(Modifier.weight(1f)) { Text("پلی‌لیست‌های من", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text("موسیقی‌ها را به سلیقه خودت دسته‌بندی کن.", color = Muted, fontSize = 12.sp) }
                FilledTonalIconButton(onClick = onCreate) { Icon(Icons.Default.Add, "ساخت پلی‌لیست") }
            }
            if (playlists.isEmpty()) EmptyState("هنوز پلی‌لیستی نساختی", "روی + بزن و اولین پلی‌لیستت را بساز.")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(playlists.keys.toList(), key = { it }) { name ->
                    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { onSelect(name) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Pink, Violet))), contentAlignment = Alignment.Center) { Icon(Icons.Default.QueueMusic, null) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                val count = playlists[name].orEmpty().size
                                Text(name, fontWeight = FontWeight.Bold); Text("$count آهنگ", color = Muted, fontSize = 11.sp)
                            }
                            TextButton(onClick = { onDelete(name) }) { Text("حذف", color = Color(0xFFFF8BAA)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(volume: Float, onVolume: (Float) -> Unit, onSleep: () -> Unit, sleepMinutes: Int, onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("تنظیمات", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null, tint = Cyan); Spacer(Modifier.width(8.dp)); Text("صدای پخش", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f)); val percent = (volume * 100).toInt(); Text("$percent%", color = Pink)
                }
                Slider(volume, onVolume, colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink))
            }
        }
        SettingRow(Icons.Default.Timer, "تایمر خاموشی", if (sleepMinutes > 0) "$sleepMinutes دقیقه فعال" else "خاموش", onSleep)
        SettingRow(Icons.Default.LibraryMusic, "اسکن کتابخانه", "به‌روزرسانی فهرست آهنگ‌های گوشی", onRescan)
        Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("درباره MUSIC LOVE", fontWeight = FontWeight.Bold, color = Cyan)
                Spacer(Modifier.height(6.dp))
                Text("نسخه 1.0.0 • پخش آفلاین و آنلاین آثار دارای مجوز آزاد", color = Muted, fontSize = 12.sp)
                Text("پخش پس‌زمینه با اعلان و کنترل‌های صفحه قفل انجام می‌شود.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Pink); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun FullPlayerScreen(song: Song, playing: Boolean, position: Long, duration: Long, volume: Float, onBack: () -> Unit,
    onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Float) -> Unit, onVolume: (Float) -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit, onSleep: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "vinyl")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "disc")
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF18112F), Ink, Color(0xFF0C2036)))).padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "بازگشت") }
            Text("NOW PLAYING", Modifier.weight(1f), color = Cyan, textAlign = TextAlign.Center, fontSize = 11.sp, letterSpacing = 2.sp)
            IconButton(onClick = onSleep) { Icon(Icons.Default.Timer, "تایمر") }
        }
        Spacer(Modifier.weight(.4f))
        Box(Modifier.size(260.dp).shadow(28.dp, CircleShape).graphicsLayer { rotationZ = if (playing) rotation else 0f }
            .clip(CircleShape).background(Brush.sweepGradient(listOf(Color(0xFF24352F), Pink, Violet, Cyan, Color(0xFF24352F)))), contentAlignment = Alignment.Center) {
            Box(Modifier.size(236.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF242B27), Panel, Ink))), contentAlignment = Alignment.Center) {
                Box(Modifier.size(110.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Pink, Violet, Cyan))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(56.dp)) }
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = .8f)))
            }
        }
        Spacer(Modifier.weight(.4f))
        Text(song.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(22.dp))
        Slider(if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f, onSeek,
            colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink, inactiveTrackColor = Color(0xFF3B4541)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatTime(position), color = Muted, fontSize = 10.sp); Text(formatTime(duration), color = Muted, fontSize = 10.sp) }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, "تصادفی", tint = Cyan) }
            IconButton(onClick = onPrevious, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.SkipPrevious, "قبلی", modifier = Modifier.size(32.dp)) }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(70.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Pink)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "پخش یا توقف", modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.SkipNext, "بعدی", modifier = Modifier.size(32.dp)) }
            IconButton(onClick = onRepeat) { Icon(Icons.Default.Repeat, "تکرار", tint = Cyan) }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeUp, null, tint = Muted)
            Slider(volume, onVolume, Modifier.weight(1f).padding(horizontal = 10.dp), colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan))
            val percent = (volume * 100).toInt(); Text("$percent%", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.weight(.5f))
        Text(if (song.online) "پخش آنلاین • منبع دارای مجوز" else "پخش آفلاین • حافظه دستگاه", color = Cyan, fontSize = 10.sp)
    }
}

@Composable
private fun MiniPlayer(song: Song, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Surface(color = Color(0xFF211934), shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Pink, Violet))), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 10.sp)
            }
            IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "پخش/توقف", tint = Pink) }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "بعدی") }
        }
    }
}

@Composable
private fun SongRow(song: Song, favorite: Boolean, onPlay: (Song) -> Unit, onFavorite: (Song) -> Unit, onAdd: (Song) -> Unit) {
    Surface(color = Panel.copy(alpha = .96f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { onPlay(song) }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFF593116), Color(0xFF202820), Color(0xFF174536)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Album, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title.ifBlank { "بدون عنوان" }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist.ifBlank { "هنرمند نامشخص" }, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (song.online) "ONLINE • LICENSED" else "ON DEVICE", color = if (song.online) Cyan else Color(0xFFFFAD55), fontSize = 8.sp, letterSpacing = 1.sp)
            }
            IconButton(onClick = { onFavorite(song) }) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "علاقه‌مندی", tint = if (favorite) Pink else Muted, modifier = Modifier.size(19.dp)) }
            IconButton(onClick = { onAdd(song) }) { Icon(Icons.Default.Add, "افزودن به پلی‌لیست", tint = Cyan, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Pink, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(10.dp))
            Column { Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black); Text(label, fontSize = 10.sp, color = Muted) }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF593116), Color(0xFF202820)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Cyan, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(message, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp), textAlign = TextAlign.Center)
    }
}

private fun loadLocalSongs(context: Context): List<Song> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID)
    val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
    val result = mutableListOf<Song>()
    try {
        context.contentResolver.query(collection, projection, selection, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { cursor ->
            val idC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdC = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idC)
                val albumId = cursor.getLong(albumIdC)
                result += Song("local:$id", cursor.getString(titleC).orEmpty(), cursor.getString(artistC).orEmpty(),
                    cursor.getString(albumC).orEmpty(), Uri.withAppendedPath(collection, id.toString()).toString(),
                    "content://media/external/audio/albumart/$albumId", cursor.getLong(durationC).coerceAtLeast(0L))
            }
        }
    } catch (_: Exception) { return emptyList() }
    return result
}

private fun searchArchive(term: String): Pair<List<Song>, String> {
    return try {
        val safe = term.replace("\"", " ").replace("\\\\", " ").trim()
        val q = "mediatype:audio AND (title:\"" + safe + "\" OR creator:\"" + safe + "\")"
        val params = "q=" + URLEncoder.encode(q, "UTF-8") + "&fl[]=identifier&fl[]=title&fl[]=creator&rows=18&page=1&output=json"
        val docs = JSONObject(httpGet("https://archive.org/advancedsearch.php?" + params))
            .optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
        val found = mutableListOf<Song>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val id = doc.optString("identifier")
            if (id.isBlank()) continue
            val encodedId = Uri.encode(id)
            val item = runCatching { JSONObject(httpGet("https://archive.org/metadata/" + encodedId)) }.getOrNull() ?: continue
            val meta = item.optJSONObject("metadata") ?: JSONObject()
            val license = meta.optString("licenseurl")
            if (!isAllowedLicense(license)) continue
            val files = item.optJSONArray("files") ?: continue
            var filename = ""
            for (j in 0 until files.length()) {
                val file = files.optJSONObject(j) ?: continue
                val name = file.optString("name")
                val size = file.optString("size").toLongOrNull() ?: 0L
                if (name.endsWith(".mp3", true) && size < 350_000_000L && !file.optString("format").contains("Metadata", true)) {
                    filename = name; break
                }
            }
            if (filename.isBlank()) continue
            val encodedFile = Uri.encode(filename)
            val title = meta.optString("title").takeIf { it.isNotBlank() } ?: doc.optString("title", "بدون عنوان")
            val artist = meta.optString("creator").takeIf { it.isNotBlank() } ?: doc.optString("creator", "هنرمند نامشخص")
            found += Song("ia:$id:$filename", title, artist, "Internet Archive",
                "https://archive.org/download/" + encodedId + "/" + encodedFile,
                "https://archive.org/services/img/" + encodedId, online = true,
                sourcePage = "https://archive.org/details/" + encodedId, license = license)
        }
        if (found.isEmpty()) Pair(emptyList(), "نتیجه‌ای با فایل MP3 و مجوز قابل استفاده پیدا نشد. نام دیگری امتحان کن.")
        else Pair(found, "")
    } catch (_: Exception) {
        Pair(emptyList(), "جست‌وجو ناموفق بود. اتصال اینترنت را بررسی کن و دوباره تلاش کن.")
    }
}

private fun isAllowedLicense(url: String): Boolean {
    val s = url.lowercase()
    return s.contains("creativecommons.org/publicdomain/zero/") ||
        s.contains("creativecommons.org/licenses/by/") || s.contains("creativecommons.org/licenses/by-sa/")
}

private fun httpGet(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"; connectTimeout = 18000; readTimeout = 22000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "MUSIC-LOVE-Android/1.0")
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return body
    } finally { connection.disconnect() }
}

private fun readSongs(raw: String): List<Song> = runCatching {
    val arr = JSONArray(raw)
    buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(Song(o.optString("id"), o.optString("title"), o.optString("artist"), o.optString("album"),
                o.optString("uri"), o.optString("artwork"), o.optLong("duration"), o.optBoolean("online"),
                o.optString("sourcePage"), o.optString("license")))
        }
    }
}.getOrDefault(emptyList())

private fun writeSongs(songs: List<Song>): String {
    val arr = JSONArray()
    songs.forEach { s ->
        arr.put(JSONObject().put("id", s.id).put("title", s.title).put("artist", s.artist).put("album", s.album)
            .put("uri", s.uri).put("artwork", s.artwork).put("duration", s.duration).put("online", s.online)
            .put("sourcePage", s.sourcePage).put("license", s.license))
    }
    return arr.toString()
}

private fun readPlaylists(raw: String): Map<String, List<Song>> = runCatching {
    val obj = JSONObject(raw)
    buildMap {
        val keys = obj.keys()
        while (keys.hasNext()) { val key = keys.next(); put(key, readSongs(obj.optJSONArray(key)?.toString() ?: "[]")) }
    }
}.getOrDefault(emptyMap())

private fun writePlaylists(map: Map<String, List<Song>>): String {
    val obj = JSONObject()
    map.forEach { (name, songs) -> obj.put(name, JSONArray(writeSongs(songs))) }
    return obj.toString()
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
