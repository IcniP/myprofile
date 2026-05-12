package org.example.project

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.dsl.module

// --- 1. INTERFACES ---

interface DeviceInfo {
    fun getModel(): String
    fun getOS(): String
}

interface NetworkMonitor {
    val isConnected: Flow<Boolean>
}

// --- 2. DATA MODELS ---

data class Note(
    val id: Int,
    val title: String,
    val content: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class HistoryItem(val title: String, val route: String, val timestamp: Long = System.currentTimeMillis())

sealed class Screen(val route: String, val title: String) {
    object Notes : Screen("notes", "Notes")
    object Favorites : Screen("favorites", "Favorite")
    object Profile : Screen("profile", "My Profile")
    object AddNote : Screen("add_note", "Add Notes")
    object Settings : Screen("settings", "Settings")
    object NoteDetail : Screen("note_detail/{noteId}", "Note Details") {
        fun createRoute(noteId: Int) = "note_detail/$noteId"
    }
}

// --- 3. SERVICES ---

class GeminiService(private val apiKey: String) {
    private val client = HttpClient()

    suspend fun summarizeNotes(notes: List<Note>): String = withContext(Dispatchers.IO) {
        if (notes.isEmpty()) return@withContext "Belum ada catatan untuk dirangkum."

        val contentText = notes.joinToString("\\n") {
            "${it.title}: ${it.content}".replace("\"", "\\\"")
        }

        val prompt = "Rangkum catatan berikut dan list deadline tanggalnya jika ada: $contentText"
        val jsonBody = """
            {
                "contents": [{
                    "parts": [{
                        "text": "$prompt"
                    }]
                }]
            }
        """.trimIndent()

        try {
            // Menggunakan gemini-1.5-flash untuk stabilitas di region Indonesia
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }

            if (response.status == HttpStatusCode.NotFound) {
                return@withContext "Error 404: Model tidak ditemukan. Pastikan API Key di-enable untuk Gemini 1.5 Flash."
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext "Gagal (${response.status.value}): ${response.bodyAsText()}"
            }

            val jsonResponse = JSONObject(response.bodyAsText())
            jsonResponse.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            "Gagal memproses AI: ${e.message}"
        }
    }
}

// --- 4. VIEWMODEL ---

class ProfileViewModel(
    private val deviceInfo: DeviceInfo,
    private val networkMonitor: NetworkMonitor,
    private val aiService: GeminiService
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState(
        deviceModel = deviceInfo.getModel(),
        deviceOS = deviceInfo.getOS()
    ))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { status ->
                _uiState.update { it.copy(isOnline = status) }
            }
        }
    }

    fun generateAiSummary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true) }
            val summary = aiService.summarizeNotes(_uiState.value.notes)
            _uiState.update { it.copy(aiSummary = summary, isAiLoading = false) }
        }
    }

    fun updateName(n: String) { _uiState.update { it.copy(name = n) } }
    fun updateBio(b: String) { _uiState.update { it.copy(bio = b) } }
    fun toggleDarkMode(v: Boolean) { _uiState.update { it.copy(isDarkMode = v) } }
    fun toggleEditMode() { _uiState.update { it.copy(isEditMode = !_uiState.value.isEditMode) } }

    fun addNote(t: String, c: String) {
        val n = Note((_uiState.value.notes.maxOfOrNull { it.id } ?: 0) + 1, t, c)
        _uiState.update { it.copy(notes = it.notes + n) }
        applyFilterAndSort()
    }

    fun deleteNote(id: Int) {
        _uiState.update { s -> s.copy(notes = s.notes.filter { it.id != id }) }
        applyFilterAndSort()
    }

    fun toggleFavorite(id: Int) {
        _uiState.update { s -> s.copy(notes = s.notes.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }) }
        applyFilterAndSort()
    }

    fun updateSearch(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        _uiState.update { s ->
            val filtered = s.notes.filter { it.title.contains(s.searchQuery, true) || it.content.contains(s.searchQuery, true) }
            s.copy(filteredNotes = filtered.sortedByDescending { it.createdAt })
        }
    }

    fun addHistory(title: String, route: String) {
        _uiState.update { s ->
            val newList = s.history.toMutableList()
            if (newList.lastOrNull()?.route != route) newList.add(HistoryItem(title, route))
            if (newList.size > 10) newList.removeAt(0)
            s.copy(history = newList)
        }
    }
}

data class ProfileUiState(
    val name: String = "Muhammad Farisi Suyitno",
    val nim: String = "123140152",
    val bio: String = "I Love Racing.",
    val email: String = "muhammad.123140152@student.itera.ac.id",
    val phone: String = "089624428",
    val location: String = "Bandar Lampung",
    val isDarkMode: Boolean = false,
    val isEditMode: Boolean = false,
    val notes: List<Note> = emptyList(),
    val filteredNotes: List<Note> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val searchQuery: String = "",
    val deviceModel: String = "",
    val deviceOS: String = "",
    val isOnline: Boolean = true,
    val aiSummary: String = "",
    val isAiLoading: Boolean = false
)

// --- 5. DEPENDENCY INJECTION ---

val appModule = module {
    single { GeminiService("AIzaSyDayOTb5ef-4Z85KYZixrcH38LMtCOA984") }
    single { ProfileViewModel(get(), get(), get()) }
}

// --- 6. UI COMPONENTS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    KoinContext {
        val viewModel: ProfileViewModel = koinInject()
        val uiState by viewModel.uiState.collectAsState()
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        LaunchedEffect(navController) {
            navController.currentBackStackEntryFlow.collect { backStackEntry ->
                backStackEntry.destination.route?.let { route ->
                    val title = when {
                        route == Screen.Notes.route -> Screen.Notes.title
                        route == Screen.Favorites.route -> Screen.Favorites.title
                        route == Screen.Profile.route -> Screen.Profile.title
                        route == Screen.AddNote.route -> Screen.AddNote.title
                        route == Screen.Settings.route -> Screen.Settings.title
                        route.startsWith("note_detail") -> Screen.NoteDetail.title
                        else -> "Unknown"
                    }
                    viewModel.addHistory(title, route)
                }
            }
        }

        MaterialTheme(colorScheme = if (uiState.isDarkMode) darkColorScheme() else lightColorScheme(primary = Color(0xFF1E88E5))) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Text("Riwayat Navigasi", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                        HorizontalDivider()
                        LazyColumn(modifier = Modifier.fillMaxHeight().padding(8.dp)) {
                            items(uiState.history.reversed()) { item ->
                                NavigationDrawerItem(label = { Text(item.title) }, selected = false, onClick = { scope.launch { drawerState.close(); navController.navigate(item.route) } }, icon = { Icon(Icons.Default.History, null) })
                            }
                        }
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        Column {
                            CenterAlignedTopAppBar(
                                title = { Text("Farisi AI Gacor") },
                                navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = null) } }
                            )
                            NetworkStatusIndicator(uiState.isOnline)
                        }
                    },
                    bottomBar = {
                        NavigationBar {
                            val navEntry by navController.currentBackStackEntryAsState()
                            val current = navEntry?.destination?.route
                            listOf(
                                Triple("Notes", Screen.Notes.route, Icons.Default.Description),
                                Triple("Favs", Screen.Favorites.route, Icons.Default.Favorite),
                                Triple("Profile", Screen.Profile.route, Icons.Default.Person)
                            ).forEach { (label, route, icon) ->
                                NavigationBarItem(
                                    selected = current == route,
                                    onClick = { navController.navigate(route) { popUpTo(navController.graph.startDestinationId); launchSingleTop = true } },
                                    label = { Text(label) },
                                    icon = { Icon(icon, null) }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        val navEntry by navController.currentBackStackEntryAsState()
                        if (navEntry?.destination?.route == Screen.Notes.route) {
                            FloatingActionButton(onClick = { navController.navigate(Screen.AddNote.route) }) { Icon(Icons.Default.Add, contentDescription = null) }
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController, Screen.Notes.route, Modifier.padding(innerPadding)) {
                        composable(Screen.Notes.route) { NotesScreen(navController, viewModel, uiState) }
                        composable(Screen.Favorites.route) { FavoritesScreen(navController, viewModel, uiState) }
                        composable(Screen.Profile.route) { ProfileScreen(viewModel, uiState) }
                        composable(Screen.Settings.route) { SettingsScreen(viewModel, uiState) }
                        composable(Screen.AddNote.route) { AddNoteScreen(navController, viewModel) }
                        composable(Screen.NoteDetail.route, listOf(navArgument("noteId") { type = NavType.IntType })) { backStackEntry ->
                            NoteDetailScreen(navController, viewModel, backStackEntry.arguments?.getInt("noteId") ?: 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkStatusIndicator(isOnline: Boolean) {
    Surface(color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff, null, modifier = Modifier.size(12.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isOnline) "Connected" else "Offline", color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun NotesScreen(navController: NavHostController, viewModel: ProfileViewModel, uiState: ProfileUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Summary & Deadline", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    if (uiState.isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = { viewModel.generateAiSummary() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Summarize", fontSize = 11.sp)
                        }
                    }
                }
                if (uiState.aiSummary.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.aiSummary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }

        OutlinedTextField(value = uiState.searchQuery, onValueChange = { viewModel.updateSearch(it) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(12.dp))
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.filteredNotes) { note ->
                NoteItem(note, { navController.navigate(Screen.NoteDetail.createRoute(note.id)) }, { viewModel.toggleFavorite(note.id) }, { viewModel.deleteNote(note.id) })
            }
        }
    }
}

@Composable
fun NoteItem(note: Note, onNoteClick: () -> Unit, onFavClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onNoteClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(note.title, fontWeight = FontWeight.Bold)
                Text(note.content, maxLines = 1, color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onFavClick) { Icon(if (note.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (note.isFavorite) Color.Red else Color.Gray) }
            IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
        }
    }
}

@Composable
fun FavoritesScreen(navController: NavHostController, viewModel: ProfileViewModel, uiState: ProfileUiState) {
    val favs = uiState.notes.filter { it.isFavorite }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Favorit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (favs.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Belum ada favorit.") }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(favs) { note -> NoteItem(note, { navController.navigate(Screen.NoteDetail.createRoute(note.id)) }, { viewModel.toggleFavorite(note.id) }, { viewModel.deleteNote(note.id) }) }
        }
    }
}

@Composable
fun AddNoteScreen(navController: NavHostController, viewModel: ProfileViewModel) {
    var t by remember { mutableStateOf("") }; var c by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
        OutlinedTextField(t, { t = it }, label = { Text("Judul") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(c, { c = it }, label = { Text("Konten") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = { if (t.isNotBlank()) { viewModel.addNote(t, c); navController.popBackStack() } }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Simpan") }
    }
}

@Composable
fun NoteDetailScreen(navController: NavHostController, viewModel: ProfileViewModel, noteId: Int) {
    val uiState by viewModel.uiState.collectAsState()
    val note = uiState.notes.find { it.id == noteId }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
        note?.let { Text(it.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(it.content) }
    }
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, uiState: ProfileUiState) {
    val uri = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileHeader(uiState.name, uiState.nim, uiState.bio)
        Spacer(modifier = Modifier.height(16.dp))
        AnimatedVisibility(uiState.isEditMode) {
            Column {
                OutlinedTextField(uiState.name, { viewModel.updateName(it) }, label = { Text("Nama") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(uiState.bio, { viewModel.updateBio(it) }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())
            }
        }
        ProfileCard {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoItem(Icons.Default.Email, "Email", uiState.email)
                InfoItem(Icons.Default.Phone, "Phone", uiState.phone)
                InfoItem(Icons.Default.LocationOn, "Location", uiState.location)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.toggleEditMode() }, modifier = Modifier.weight(1f)) { Text(if (uiState.isEditMode) "Simpan" else "Edit") }
            OutlinedButton(onClick = { uri.openUri("https://github.com/IcniP") }, modifier = Modifier.weight(1f)) { Text("Github") }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: ProfileViewModel, uiState: ProfileUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device Info", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Model: ${uiState.deviceModel}")
                Text("OS: ${uiState.deviceOS}")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mode Gelap", modifier = Modifier.weight(1f))
            Switch(uiState.isDarkMode, { viewModel.toggleDarkMode(it) })
        }
    }
}

@Composable
fun ProfileHeader(name: String, nim: String, bio: String) {
    val ctx = LocalContext.current
    val img = remember(ctx) { try { ctx.assets.open("foto_profil.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } } catch (e: Exception) { null } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(100.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            if (img != null) Image(img, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, null, modifier = Modifier.size(50.dp), tint = Color.LightGray)
        }
        Text(name, fontWeight = FontWeight.Bold); Text(nim, color = Color.Gray); Text(bio, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProfileCard(c: @Composable () -> Unit) { Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { c() } }

@Composable
fun InfoItem(i: ImageVector, l: String, v: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(i, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column { Text(l, style = MaterialTheme.typography.labelSmall, color = Color.Gray); Text(v) }
    }
}