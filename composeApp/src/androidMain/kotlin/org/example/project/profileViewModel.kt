package org.example.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.ViewModel

data class ProfileUiState(
    val name: String = "Muhammad Farisi Suyitno",
    val nim: String = "123140152",
    val bio: String = "I Love Racing.",
    val email: String = "muhammad.123140152@student.itera.ac.id",
    val phone: String = "08123",
    val location: String = "Bandar Lampung",
    val isDarkMode: Boolean = false,
    val isEditMode: Boolean = false
)

class ProfileViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun updateName(newName: String) {
        _uiState.update { it.copy(name = newName) }
    }

    fun updateBio(newBio: String) {
        _uiState.update { it.copy(bio = newBio) }
    }

    fun toggleDarkMode(isDark: Boolean) {
        _uiState.update { it.copy(isDarkMode = isDark) }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !_uiState.value.isEditMode) }
    }
}
