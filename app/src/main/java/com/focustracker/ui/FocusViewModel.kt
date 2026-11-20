package com.focustracker.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focustracker.FocusTracker
import com.focustracker.data.AppDatabase
import com.focustracker.data.Category
import com.focustracker.data.FocusSession
import com.focustracker.service.FocusForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FocusViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)

    val categories: StateFlow<List<Category>> = db.categoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<FocusSession>> = db.focusSessionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedCategoryId = MutableStateFlow<Long?>(null)

    // 全局可观测状态（由前台服务写入）
    val isRunning = FocusTracker.isRunning
    val elapsedMs = FocusTracker.elapsedMs
    val focusState = FocusTracker.focusState
    val score = FocusTracker.score
    val mode = FocusTracker.mode

    fun selectCategory(id: Long?) {
        selectedCategoryId.value = id
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { db.categoryDao().insert(Category(name = trimmed)) }
    }

    fun start() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, FocusForegroundService::class.java).apply {
            action = FocusForegroundService.ACTION_START
            putExtra(FocusForegroundService.EXTRA_CATEGORY_ID, selectedCategoryId.value ?: -1L)
        }
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun stop() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, FocusForegroundService::class.java).apply {
            action = FocusForegroundService.ACTION_STOP
        }
        ctx.startService(intent)
    }
}
