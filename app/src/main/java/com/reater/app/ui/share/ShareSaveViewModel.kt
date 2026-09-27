package com.reater.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reater.app.data.remote.FetchedPostResult
import com.reater.app.data.remote.ThreadsGraphQLClient
import com.reater.app.data.repository.ThreadPostRepository
import com.reater.app.domain.UrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShareSaveUiState(
    val rawText: String = "",
    val targetUrl: String = "",
    val shortcode: String = "",
    val authorHandle: String = "",
    val bodyText: String = "",
    val commentsText: String = "",
    val manualNote: String = "",
    val isFetching: Boolean = false,
    val isFetchFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val fetchedResult: FetchedPostResult? = null
)

@HiltViewModel
class ShareSaveViewModel @Inject constructor(
    private val repository: ThreadPostRepository,
    private val graphQLClient: ThreadsGraphQLClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareSaveUiState())
    val uiState: StateFlow<ShareSaveUiState> = _uiState.asStateFlow()

    fun processIncomingText(sharedText: String) {
        _uiState.update { it.copy(rawText = sharedText) }
        val extractedUrl = UrlParser.extractFirstThreadsUrl(sharedText) ?: sharedText.trim()
        val parsed = UrlParser.parseAndCanonicalize(extractedUrl)

        if (parsed != null) {
            _uiState.update {
                it.copy(
                    targetUrl = parsed.canonicalUrl,
                    shortcode = parsed.shortcode,
                    authorHandle = parsed.handle,
                    isFetching = true
                )
            }
            fetchRemoteData(parsed.shortcode)
        } else {
            // Non-Threads URL or plain text
            _uiState.update {
                it.copy(
                    targetUrl = extractedUrl,
                    bodyText = sharedText
                )
            }
        }
    }

    private fun fetchRemoteData(shortcode: String) {
        viewModelScope.launch {
            val result = graphQLClient.fetchPostByPostIdOrShortcode(shortcode, shortcode)
            result.onSuccess { fetched ->
                _uiState.update { current ->
                    val commentsJoined = fetched.comments.joinToString("\n---\n") { "${it.author}: ${it.text}" }
                    current.copy(
                        isFetching = false,
                        bodyText = if (current.bodyText.isBlank()) fetched.bodyText else current.bodyText,
                        commentsText = if (current.commentsText.isBlank()) commentsJoined else current.commentsText,
                        authorHandle = if (current.authorHandle.isBlank()) fetched.authorHandle else current.authorHandle,
                        fetchedResult = fetched
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        isFetching = false,
                        isFetchFailed = true
                    )
                }
            }
        }
    }

    fun onBodyTextChanged(text: String) {
        _uiState.update { it.copy(bodyText = text) }
    }

    fun onCommentsTextChanged(text: String) {
        _uiState.update { it.copy(commentsText = text) }
    }

    fun onNoteChanged(text: String) {
        _uiState.update { it.copy(manualNote = text) }
    }

    fun savePost() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                repository.savePost(
                    canonicalUrl = state.targetUrl.ifBlank { "https://www.threads.com/unknown/${System.currentTimeMillis()}" },
                    shortcode = state.shortcode.ifBlank { "sc_${System.currentTimeMillis()}" },
                    authorHandle = state.authorHandle,
                    bodyText = state.bodyText,
                    commentsText = state.commentsText,
                    manualNote = state.manualNote,
                    manualSummary = "",
                    categoryId = null,
                    fetchedResult = state.fetchedResult
                )
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
