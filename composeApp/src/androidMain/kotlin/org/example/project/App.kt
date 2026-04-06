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
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
data class Note(val id: Int, val title: String, val content: String, val isFavorite: Boolean = false)
data class HistoryItem(val title: String, val route: String, val timestamp: Long = System.currentTimeMillis())

sealed class Screen(val route: String, val title: String) {
    object Notes : Screen("notes", "Notes")
    object Favorites : Screen("favorites", "Favorite")
    object Profile : Screen("profile", "My Profile")
    object AddNote : Screen("add_note", "Add Notes")
    object NoteDetail : Screen("note_detail/{noteId}", "Notes") {
        fun createRoute(noteId: Int) = "note_detail/$noteId"
    }
}
// Farisi Suyitno 123140152
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val viewModel = remember { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // histori
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val route = backStackEntry.destination.route
            if (route != null) {
                val title = when {
                    route == Screen.Notes.route -> Screen.Notes.title
                    route == Screen.Favorites.route -> Screen.Favorites.title
                    route == Screen.Profile.route -> Screen.Profile.title
                    route == Screen.AddNote.route -> Screen.AddNote.title
                    route.startsWith("note_detail") -> Screen.NoteDetail.title
                    else -> "Unknown"
                }
                viewModel.addHistory(title, route)
            }
        }
    }

    val colors = if (uiState.isDarkMode) darkColorScheme() else lightColorScheme(
        primary = Color(0xFF1E88E5),
        surfaceVariant = Color(0xFFF5F5F5)
    )

    MaterialTheme(colorScheme = colors) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Riwayat Navigasi", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                    HorizontalDivider()

                    LazyColumn(modifier = Modifier.fillMaxHeight().padding(8.dp)) {
                        items(uiState.history.reversed()) { item ->
                            NavigationDrawerItem(
                                label = { Text(item.title) },
                                selected = false,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(item.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Default.History, null) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Farisi Gacor") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = null)
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        listOf(
                            Triple("Notes", Screen.Notes.route, Icons.Default.Description),
                            Triple("Favorites", Screen.Favorites.route, Icons.Default.Favorite),
                            Triple("Profile", Screen.Profile.route, Icons.Default.Person)
                        ).forEach { (label, route, icon) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                label = { Text(label) },
                                icon = { Icon(icon, null) }
                            )
                        }
                    }
                },
                floatingActionButton = {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    if (navBackStackEntry?.destination?.route == Screen.Notes.route) {
                        FloatingActionButton(onClick = { navController.navigate(Screen.AddNote.route) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Notes.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Notes.route) { NotesScreen(navController, viewModel, uiState) }
                    composable(Screen.Favorites.route) { FavoritesScreen(navController, viewModel, uiState) }
                    composable(Screen.Profile.route) { ProfileScreen(viewModel, uiState) }
                    composable(Screen.AddNote.route) { AddNoteScreen(navController, viewModel) }
                    composable(
                        route = Screen.NoteDetail.route,
                        arguments = listOf(navArgument("noteId") { type = NavType.IntType })
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getInt("noteId") ?: 0
                        NoteDetailScreen(navController, viewModel, noteId)
                    }
                }
            }
        }
    }
}


@Composable
fun NotesScreen(navController: NavHostController, viewModel: ProfileViewModel, uiState: ProfileUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Daftar Catatan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada catatan.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.notes) { note ->
                    NoteItem(note, onNoteClick = { navController.navigate(Screen.NoteDetail.createRoute(note.id)) },
                        onFavClick = { viewModel.toggleFavorite(note.id) })
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(navController: NavHostController, viewModel: ProfileViewModel, uiState: ProfileUiState) {
    val favNotes = uiState.notes.filter { it.isFavorite }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Catatan Favorit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (favNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Belum ada favorit.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(favNotes) { note ->
                    NoteItem(note, onNoteClick = { navController.navigate(Screen.NoteDetail.createRoute(note.id)) },
                        onFavClick = { viewModel.toggleFavorite(note.id) })
                }
            }
        }
    }
}

@Composable
fun NoteItem(note: Note, onNoteClick: () -> Unit, onFavClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onNoteClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(note.title, fontWeight = FontWeight.Bold)
                Text(note.content, maxLines = 1, color = Color.Gray)
            }
            IconButton(onClick = onFavClick) {
                Icon(if (note.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null,
                    tint = if (note.isFavorite) Color.Red else Color.Gray)
            }
        }
    }
}

@Composable
fun AddNoteScreen(navController: NavHostController, viewModel: ProfileViewModel) {
    var t by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
        Text("Tambah Catatan", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Judul") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = c, onValueChange = { c = it }, label = { Text("Konten") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (t.isNotBlank()) { viewModel.addNote(t, c); navController.popBackStack() } }, modifier = Modifier.fillMaxWidth()) {
            Text("Simpan")
        }
    }
}

@Composable
fun NoteDetailScreen(navController: NavHostController, viewModel: ProfileViewModel, noteId: Int) {
    val uiState by viewModel.uiState.collectAsState()
    val note = uiState.notes.find { it.id == noteId }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) }
        if (note != null) {
            Text(note.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(note.content)
        }
    }
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, uiState: ProfileUiState) {
    val uri = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
            Icon(Icons.Default.Brightness4, null, Modifier.size(20.dp))
            Switch(checked = uiState.isDarkMode, onCheckedChange = { viewModel.toggleDarkMode(it) })
        }
        ProfileHeader(uiState.name, uiState.nim, uiState.bio)
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(uiState.isEditMode) {
            Column {
                OutlinedTextField(uiState.name, { viewModel.updateName(it) }, label = { Text("Nama") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(uiState.bio, { viewModel.updateBio(it) }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())
            }
        }
        ProfileCard {
            Column(Modifier.padding(16.dp)) {
                InfoItem(Icons.Default.Email, "Email", uiState.email)
                InfoItem(Icons.Default.Phone, "Phone", uiState.phone)
                InfoItem(Icons.Default.LocationOn, "Location", uiState.location)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.toggleEditMode() }, Modifier.weight(1f)) {
                Icon(if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit, null)
                Text(if (uiState.isEditMode) " Save" else " Edit")
            }
            OutlinedButton(onClick = { uri.openUri("https://github.com/IcniP") }, Modifier.weight(1f)) { Text("Github") }
        }
    }
}

@Composable
fun ProfileHeader(name: String, nim: String, bio: String) {
    val context = LocalContext.current
    val bitmap = remember(context) {
        try { context.assets.open("foto_profil.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } } catch (e: Exception) { null }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(100.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), Alignment.Center) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, null, Modifier.size(50.dp), tint = Color.LightGray)
        }
        Text(name, fontWeight = FontWeight.Bold)
        Text(nim, color = Color.Gray)
        Text(bio, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProfileCard(c: @Composable () -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { c() } }

@Composable
fun InfoItem(i: ImageVector, l: String, v: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(i, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column { Text(l, style = MaterialTheme.typography.labelSmall); Text(v) }
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
    val history: List<HistoryItem> = emptyList()
)

class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun updateName(n: String) { _uiState.update { it.copy(name = n) } }
    fun updateBio(b: String) { _uiState.update { it.copy(bio = b) } }
    fun toggleDarkMode(v: Boolean) { _uiState.update { it.copy(isDarkMode = v) } }
    fun toggleEditMode() { _uiState.update { it.copy(isEditMode = !_uiState.value.isEditMode) } }
    fun addNote(t: String, c: String) {
        val n = Note((_uiState.value.notes.maxOfOrNull { it.id } ?: 0) + 1, t, c)
        _uiState.update { it.copy(notes = it.notes + n) }
    }
    fun toggleFavorite(id: Int) {
        _uiState.update { s -> s.copy(notes = s.notes.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }) }
    }

    fun addHistory(title: String, route: String) {
        _uiState.update { s ->
            val newList = s.history.toMutableList()
            if (newList.lastOrNull()?.route != route) {
                newList.add(HistoryItem(title, route))
            }
            if (newList.size > 10) newList.removeAt(0)
            s.copy(history = newList)
        }
    }
}