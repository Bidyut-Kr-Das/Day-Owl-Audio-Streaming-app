package com.example.dayowl.ui.host

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dayowl.network.SessionManager
import com.example.dayowl.repository.SessionRepository
import com.example.dayowl.service.HostService

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class HostViewModel(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<HostEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val isBroadcasting: StateFlow<Boolean> = sessionRepository.isBroadcasting

    fun onStartClicked() {
        // Trigger projection request in UI
        viewModelScope.launch {
            _events.emit(HostEvent.RequestProjection)
        }
    }

    sealed class HostEvent {
        object RequestProjection : HostEvent()
    }

    fun stopBroadcast() {
        val intent = Intent(context, HostService::class.java).apply {
            action = HostService.ACTION_STOP_BROADCAST
        }
        context.startService(intent)
        sessionManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopBroadcast()
    }
}
