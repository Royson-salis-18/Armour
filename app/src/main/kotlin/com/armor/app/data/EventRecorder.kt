package com.armor.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Writes the audit trail to Room off the caller's thread. Everything is stored locally first; sync comes later. */
class EventRecorder(private val dao: EventDao, private val scope: CoroutineScope) {
    fun record(type: String, detail: String, timeMs: Long) {
        scope.launch { dao.insert(EventEntity(timeMs = timeMs, type = type, detail = detail)) }
    }
}
