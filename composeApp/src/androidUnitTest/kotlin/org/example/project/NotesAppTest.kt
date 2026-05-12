// FILE 3: ComposeAppAndroidUnitTest.kt
package org.example.project

import app.cash.turbine.test
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NoteRepositoryTest {
    private val repository = NoteRepositoryImpl()

    @Test
    fun `initial notes should be empty`() {
        assertTrue(repository.getAllNotes().isEmpty())
    }

    @Test
    fun `add note should increase size`() {
        repository.addNote(Note(1, "Title", "Content"))
        assertEquals(1, repository.getAllNotes().size)
    }

    @Test
    fun `delete note should remove it`() {
        repository.addNote(Note(1, "Test", "Content"))
        repository.deleteNote(1)
        assertTrue(repository.getAllNotes().isEmpty())
    }

    @Test
    fun `toggle favorite should work`() {
        repository.addNote(Note(1, "Fav", "Content", false))
        repository.toggleFavorite(1)
        assertTrue(repository.getAllNotes().first().isFavorite)
    }

    @Test
    fun `search should return matching title`() {
        repository.addNote(Note(1, "Mancing", "Gacor"))
        val result = repository.searchNotes("Mancing")
        assertEquals(1, result.size)
    }
}

class ProfileViewModelTest {
    private val repo = mockk<NoteRepository>(relaxed = true)
    private val device = mockk<DeviceInfo>(relaxed = true)
    private val network = mockk<NetworkMonitor>(relaxed = true)
    private val ai = mockk<GeminiService>(relaxed = true)

    @BeforeTest
    fun setup() {
        every { network.isConnected } returns flowOf(true)
        every { device.getModel() } returns "Emulator"
        every { device.getOS() } returns "Android 15"
    }

    @Test
    fun `updateName should change name in state`() {
        val vm = ProfileViewModel(device, network, repo, ai)
        vm.updateName("Farisi Baru")
        assertEquals("Farisi Baru", vm.uiState.value.name)
    }

    @Test
    fun `toggleEditMode should flip boolean`() {
        val vm = ProfileViewModel(device, network, repo, ai)
        val initial = vm.uiState.value.isEditMode
        vm.toggleEditMode()
        assertEquals(!initial, vm.uiState.value.isEditMode)
    }

    @Test
    fun `addNote should call repository add`() {
        val vm = ProfileViewModel(device, network, repo, ai)
        vm.addNote("Judul", "Isi")
        verify { repo.addNote(any()) }
    }

    @Test
    fun `search should update state via Turbine`() = runTest {
        val vm = ProfileViewModel(device, network, repo, ai)
        vm.uiState.test {
            skipItems(1)
            vm.updateSearch("Testing")
            assertEquals("Testing", awaitItem().searchQuery)
        }
    }

    @Test
    fun `isOnline should react to flow changes`() = runTest {
        val netFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
        every { network.isConnected } returns netFlow
        val vm = ProfileViewModel(device, network, repo, ai)

        vm.uiState.test {
            assertTrue(awaitItem().isOnline)
            netFlow.value = false
            assertFalse(awaitItem().isOnline)
        }
    }
}