package com.imavinashexdev.sipcalling.data

import android.content.Context
import com.imavinashexdev.sipcalling.model.CallLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CallLogRepository {

    private var dbHelper: CallLogDatabaseHelper? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _recentCalls = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val recentCalls: StateFlow<List<CallLogEntry>> = _recentCalls.asStateFlow()

    fun initialize(context: Context) {
        if (dbHelper != null) return
        dbHelper = CallLogDatabaseHelper(context.applicationContext)
        loadCalls()
    }

    fun loadCalls() {
        scope.launch {
            val list = dbHelper?.getAll() ?: emptyList()
            _recentCalls.value = list
        }
    }

    fun insertCall(entry: CallLogEntry) {
        scope.launch {
            dbHelper?.insert(entry)
            val updated = dbHelper?.getAll() ?: emptyList()
            _recentCalls.value = updated
        }
    }

    fun deleteCall(id: Long) {
        scope.launch {
            dbHelper?.delete(id)
            val updated = dbHelper?.getAll() ?: emptyList()
            _recentCalls.value = updated
        }
    }

    fun clearAll() {
        scope.launch {
            dbHelper?.clearAll()
            _recentCalls.value = emptyList()
        }
    }
}
