package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.server.LocalServer
import com.thuvstu.personalencyclopedia.server.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val localServer: LocalServer,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    val token: StateFlow<String?> = tokenManager.tokenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { tokenManager.getOrCreateToken() }
    }

    fun toggleServer() {
        viewModelScope.launch {
            if (_isRunning.value) {
                localServer.stop()
                _isRunning.value = false
            } else {
                localServer.start()
                _isRunning.value = true
            }
        }
    }

    fun regenerateToken() {
        viewModelScope.launch { tokenManager.regenerateToken() }
    }

    override fun onCleared() {
        super.onCleared()
        localServer.stop()
    }
}