/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  YACR – Your All Call Recorder  |  presentation/screens/home/HomeViewModel ║
 * ║  Developer : MNM YOUNUS                                                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.mnmyounus.yacr.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnmyounus.yacr.domain.model.Recording
import com.mnmyounus.yacr.domain.usecase.DeleteRecordingUseCase
import com.mnmyounus.yacr.domain.usecase.GetAllRecordingsUseCase
import com.mnmyounus.yacr.domain.usecase.SearchRecordingsUseCase
import com.mnmyounus.yacr.domain.usecase.ToggleFlagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllRecordings: GetAllRecordingsUseCase,
    private val searchRecordings: SearchRecordingsUseCase,
    private val deleteRecording: DeleteRecordingUseCase,
    private val toggleFlag: ToggleFlagUseCase
) : ViewModel() {

    // ─── UI State ────────────────────────────────────────────────────────────

    data class UiState(
        val recordings: List<Recording> = emptyList(),
        val isLoading: Boolean = true,
        val searchQuery: String = "",
        val selectedIds: Set<String> = emptySet(),
        val isSelectionMode: Boolean = false,
        val snackbarMessage: String? = null
    ) {
        val isSearchActive: Boolean get() = searchQuery.isNotBlank()
        val hasRecordings: Boolean get() = recordings.isNotEmpty()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    // ─── Recordings Flow ─────────────────────────────────────────────────────

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val recordings: StateFlow<List<Recording>> = _searchQuery
        .debounce(300L)
        .flatMapLatest { query ->
            if (query.isBlank()) getAllRecordings() else searchRecordings(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            recordings.collect { list ->
                _uiState.update { it.copy(recordings = list, isLoading = false) }
            }
        }
    }

    // ─── Event Handlers ──────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchClear() = onSearchQueryChange("")

    fun onRecordingLongPress(id: String) {
        _uiState.update { state ->
            val newSelected = state.selectedIds + id
            state.copy(selectedIds = newSelected, isSelectionMode = true)
        }
    }

    fun onRecordingSelect(id: String) {
        _uiState.update { state ->
            if (!state.isSelectionMode) return@update state
            val newSelected = if (id in state.selectedIds) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(
                selectedIds = newSelected,
                isSelectionMode = newSelected.isNotEmpty()
            )
        }
    }

    fun onClearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun onSelectAll() {
        _uiState.update { state ->
            state.copy(selectedIds = state.recordings.map { it.id }.toSet())
        }
    }

    fun onDeleteSelected() {
        val toDelete = _uiState.value.selectedIds.toList()
        if (toDelete.isEmpty()) return

        viewModelScope.launch {
            try {
                val deleted = deleteRecording.deleteMany(toDelete)
                _uiState.update { state ->
                    state.copy(
                        selectedIds = emptySet(),
                        isSelectionMode = false,
                        snackbarMessage = "$deleted recording(s) deleted"
                    )
                }
                Timber.d("HomeViewModel: Deleted $deleted recordings")
            } catch (e: Exception) {
                Timber.e(e, "HomeViewModel: Delete failed")
                _uiState.update { it.copy(snackbarMessage = "Delete failed: ${e.message}") }
            }
        }
    }

    fun onDeleteSingle(id: String) {
        viewModelScope.launch {
            try {
                deleteRecording(id)
                _uiState.update { it.copy(snackbarMessage = "Recording deleted") }
            } catch (e: Exception) {
                Timber.e(e, "HomeViewModel: Single delete failed for $id")
                _uiState.update { it.copy(snackbarMessage = "Delete failed") }
            }
        }
    }

    fun onToggleFlag(id: String) {
        viewModelScope.launch {
            try {
                toggleFlag(id)
            } catch (e: Exception) {
                Timber.e(e, "HomeViewModel: Toggle flag failed for $id")
            }
        }
    }

    fun onSnackbarDismissed() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
