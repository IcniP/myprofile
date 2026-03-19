package org.example.project

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Composable
fun App() {
    val viewModel = remember { ProfileViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val colors = if (uiState.isDarkMode) darkColorScheme() else lightColorScheme(
        primary = Color(0xFF1E88E5),
        surfaceVariant = Color(0xFFF5F5F5)
    )

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ProfileScreen(viewModel, uiState)
        }
    }
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, uiState: ProfileUiState) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(Icons.Default.Brightness4, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = uiState.isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )
        }

        ProfileHeader(
            name = uiState.name,
            nim = uiState.nim,
            bio = uiState.bio
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(visible = uiState.isEditMode) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Edit Profile", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Nama") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.bio,
                        onValueChange = { viewModel.updateBio(it) },
                        label = { Text("Bio") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        ProfileCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Informasi Kontak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                InfoItem(Icons.Default.Email, "Email", uiState.email)
                InfoItem(Icons.Default.Phone, "Phone", uiState.phone)
                InfoItem(Icons.Default.LocationOn, "Location", uiState.location)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.toggleEditMode() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isEditMode) Color.Green else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit, null)
                Spacer(Modifier.width(8.dp))
                Text(if (uiState.isEditMode) "Save" else "Edit Profile")
            }

            OutlinedButton(
                onClick = { uriHandler.openUri("https://github.com/IcniP") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Github")
            }
        }
    }
}

@Composable
fun ProfileHeader(name: String, nim: String, bio: String) {
    val context = LocalContext.current
    val bitmap = remember(context) {
        try {
            val inputStream = context.assets.open("foto_profil.jpg")
            BitmapFactory.decodeStream(inputStream).asImageBitmap()
        } catch (e: Exception) { null }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(70.dp), tint = Color.LightGray)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("NIM: $nim", color = Color.Gray)
        Text(bio, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun ProfileCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) { content() }
}

@Composable
fun InfoItem(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
