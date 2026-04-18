package org.example.project

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.net.URL

// --- Data Models ---
data class Article(
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val content: String?,
    val author: String?,
    val sourceName: String?
)

sealed class NewsUiState {
    object Loading : NewsUiState()
    data class Success(val articles: List<Article>) : NewsUiState()
    data class Error(val message: String) : NewsUiState()
}

// --- Repository ---
class NewsRepository {
    private val apiKey = "49674eebda834c09afd166613d4e15a1"
    private val apiUrl = "https://newsapi.org/v2/everything?q=Racing&apiKey=$apiKey"

    suspend fun fetchTopHeadlines(): List<Article> = withContext(Dispatchers.IO) {
        val url = java.net.URL(apiUrl)
        val connection = url.openConnection() as java.net.HttpURLConnection

        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.requestMethod = "GET"

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val articlesJson = json.getJSONArray("articles")
        val articles = mutableListOf<Article>()

        for (i in 0 until articlesJson.length()) {
            val item = articlesJson.getJSONObject(i)
            val articleUrl = item.optString("urlToImage", null)

            if (!articleUrl.isNullOrBlank() && articleUrl != "null") {
                articles.add(
                    Article(
                        title = item.optString("title", "No Title"),
                        description = item.optString("description", "No Description"),
                        urlToImage = articleUrl,
                        content = item.optString("content", ""),
                        author = item.optString("author", "Unknown Author"),
                        sourceName = item.getJSONObject("source").optString("name", "General")
                    )
                )
            }
        }
        articles
    }
}

// --- ViewModel ---
class NewsViewModel : ViewModel() {
    private val repository = NewsRepository()
    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    var currentArticle by mutableStateOf<Article?>(null)

    init { loadNews() }

    fun loadNews() {
        viewModelScope.launch {
            _uiState.value = NewsUiState.Loading
            try {
                val data = repository.fetchTopHeadlines()
                _uiState.value = NewsUiState.Success(data)
            } catch (e: Exception) {
                _uiState.value = NewsUiState.Error("Error: ${e.localizedMessage}")
            }
        }
    }
}

// --- Main App Entry ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val viewModel = remember { NewsViewModel() }
    val navController = rememberNavController()

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFD32F2F))) {
        NavHost(navController, startDestination = "list") {
            composable("list") {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("FARISI RACING NEWS", fontWeight = FontWeight.Black) },
                            actions = {
                                IconButton(onClick = { viewModel.loadNews() }) {
                                    Icon(Icons.Default.Refresh, "Refresh")
                                }
                            }
                        )
                    }
                ) { padding ->
                    NewsListScreen(viewModel, padding) { article ->
                        viewModel.currentArticle = article
                        navController.navigate("detail")
                    }
                }
            }
            composable("detail") {
                viewModel.currentArticle?.let { article ->
                    NewsDetailScreen(article) { navController.popBackStack() }
                }
            }
        }
    }
}

// --- List Screen ---
@Composable
fun NewsListScreen(viewModel: NewsViewModel, padding: PaddingValues, onArticleClick: (Article) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when (val uiState = state) {
            is NewsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is NewsUiState.Error -> Text(uiState.message, Modifier.align(Alignment.Center), color = Color.Red)
            is NewsUiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.articles) { article ->
                        NewsCard(article) { onArticleClick(article) }
                    }
                }
            }
        }
    }
}

// --- Card Component ---
@Composable
fun NewsCard(article: Article, onClick: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            AsyncImage(
                model = article.urlToImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFFE0E0E0)),
                contentScale = ContentScale.Crop,
                error = null
            )

            Text(
                text = " ${article.urlToImage}",
                fontSize = 8.sp,
                color = Color.Red,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Column(Modifier.padding(12.dp)) {
                Text(
                    text = article.sourceName ?: "Racing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = article.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = article.description ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = Color.DarkGray
                )
            }
        }
    }
}

// --- Detail Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(article: Article, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Berita") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(article.urlToImage)
                    .crossfade(true)
                    .listener(
                        onError = { _, result ->
                            println("COIL_ERROR: ${result.throwable.message}")
                        }
                    )
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(220.dp).background(Color.Gray),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(16.dp)) {
                Text(article.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("By ${article.author} • ${article.sourceName}", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(
                    text = article.content ?: article.description ?: "No content available.",
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp
                )
            }
        }
    }
}