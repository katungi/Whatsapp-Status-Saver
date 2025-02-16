package com.breens.whatsappstatussaver.statuses.presentation

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.breens.whatsappstatussaver.preferences.domain.PreferencesRepository
import com.breens.whatsappstatussaver.save.domain.SaveImagesRepository
import com.breens.whatsappstatussaver.statuses.domain.GetStatusesRepository
import com.breens.whatsappstatussaver.statuses.domain.Media
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class StatusesViewModel @Inject constructor(
    private val getStatusesRepository: GetStatusesRepository,
    private val saveImagesRepository: SaveImagesRepository,
    private val preferencesRepository: PreferencesRepository,
    private val analytics: FirebaseAnalytics,
) : ViewModel() {
    private val _statusesScreenUiState = MutableStateFlow(StatusesScreenUiState())
    val statusesScreenUiState = _statusesScreenUiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<StatusesScreenUiEvents>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun sendEvent(event: StatusesScreenUiEvents) {
        viewModelScope.launch {
            when (event) {
                is StatusesScreenUiEvents.ShowSnackBar -> {
                    _eventFlow.emit(
                        StatusesScreenUiEvents.ShowSnackBar(message = event.message)
                    )
                }

                is StatusesScreenUiEvents.GetStatusImages -> {
                    val mediaType = if (statusesScreenUiState.value.selectedTab == 0) {
                        "image"
                    } else {
                        "video"
                    }

                    getStatusImages(
                        uri = event.uri,
                        fromNormalStorage = event.fromNormalStorage,
                        mediaType = mediaType
                    )
                }

                is StatusesScreenUiEvents.SaveMediaFile -> {
                    saveImageFile(mediaFile = event.mediaFile)
                }

                is StatusesScreenUiEvents.ChangeTab -> {
                    changeTab(selectedTab = event.tab)
                }

                is StatusesScreenUiEvents.ShareMediaFile -> {
                    _eventFlow.emit(
                        StatusesScreenUiEvents.ShareMediaFile(mediaFile = event.mediaFile)
                    )
                }

                is StatusesScreenUiEvents.ShowFullImageDialog -> {
                    showFullImageDialog(showFullImageDialog = event.show, imageUri = event.imageUri)
                }

                is StatusesScreenUiEvents.PlayVideo -> {
                    _eventFlow.emit(
                        StatusesScreenUiEvents.PlayVideo(videoUri = event.videoUri)
                    )
                }

                is StatusesScreenUiEvents.ShowHelpInstructions -> {
                    showHelpInstructions(helpInstructionsIsDialogOpen = event.show)
                }
            }
        }
    }

    private fun showHelpInstructions(helpInstructionsIsDialogOpen: Boolean) {
        _statusesScreenUiState.update {
            it.copy(
                helpInstructionsIsDialogOpen = helpInstructionsIsDialogOpen
            )
        }
    }

    private fun showFullImageDialog(showFullImageDialog: Boolean, imageUri: Uri?) {
        _statusesScreenUiState.update {
            it.copy(
                showFullImageDialog = showFullImageDialog,
                imageUriClicked = imageUri
            )
        }
    }

    private fun changeTab(selectedTab: Int) {
        viewModelScope.launch {
            _statusesScreenUiState.update {
                it.copy(
                    selectedTab = selectedTab
                )
            }

            val uri = statusesScreenUiState.value.uri
            val fromNormalStorage = statusesScreenUiState.value.fromNormalStorage
            val mediaType = if (selectedTab == 0) {
                "image"
            } else {
                "video"
            }

            getStatusImages(
                uri = uri,
                fromNormalStorage = fromNormalStorage,
                mediaType = mediaType
            )
        }
    }

    private fun getStatusImages(uri: Uri?, fromNormalStorage: Boolean, mediaType: String) {
        viewModelScope.launch {
            _statusesScreenUiState.update {
                it.copy(
                    isLoading = true,
                    uri = uri,
                    fromNormalStorage = fromNormalStorage,
                )
            }

            val media = getStatusesRepository.getStatuses(
                uri = uri,
                fromNormalStorage = fromNormalStorage,
                mediaType = mediaType,
            )

            _statusesScreenUiState.update {
                it.copy(
                    isLoading = false,
                    media = media
                )
            }

            Log.e("MEDIA", "$media")

            preferencesRepository.setUri(uri = uri)
        }
    }

    private fun saveImageFile(mediaFile: Media) {
        viewModelScope.launch {
            _statusesScreenUiState.update {
                it.copy(
                    savingImage = true
                )
            }

            val isSavedSuccessfully = saveImagesRepository.saveImage(mediaFile = mediaFile)

            if (isSavedSuccessfully) {
                _statusesScreenUiState.update {
                    it.copy(
                        savingImage = false,
                        imageSavedSuccessfully = true
                    )
                }
                return@launch _eventFlow.emit(
                    StatusesScreenUiEvents.ShowSnackBar(message = "Image saved successfully")
                )
            }

            _statusesScreenUiState.update {
                it.copy(
                    savingImage = false,
                    imageSavedSuccessfully = false
                )
            }
            _eventFlow.emit(
                StatusesScreenUiEvents.ShowSnackBar(message = "Failed to save image, try again.")
            )
        }
    }

    fun getSavedUri() {
        viewModelScope.launch {
            val savedUri = preferencesRepository.getUri().first()

            _statusesScreenUiState.update {
                it.copy(
                    uri = savedUri
                )
            }

            // if uri is empty ask permission to fetch images
            // if its not empty used saved uri to fetch images
        }
    }

    fun analytics() = analytics
}