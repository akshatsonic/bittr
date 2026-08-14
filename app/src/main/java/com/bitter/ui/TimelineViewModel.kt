package com.bitter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitter.store.EventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val repository: EventRepository,
    val username: String,
) : ViewModel() {

    val items: StateFlow<List<TimelineItem>> = repository.observeTimeline()
        .map { TimelineModel.build(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun post(content: String) {
        viewModelScope.launch { repository.post(content) }
    }

    fun like(targetEventId: String) {
        viewModelScope.launch { repository.like(targetEventId) }
    }
}

class TimelineViewModelFactory(
    private val repository: EventRepository,
    private val username: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TimelineViewModel(repository, username) as T
}
