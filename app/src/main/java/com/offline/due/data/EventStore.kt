package com.offline.due.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate

object EventStore {

    private const val FILE_NAME = "events.json"
    private const val BACKUP_NAME = "events.bak.json"
    private const val TEMP_NAME = "events.tmp.json"

    private var file: File? = null

    private var appContext: Context? = null

    private var mainTrusted = false

    private val _events = MutableStateFlow<List<CountdownEvent>>(emptyList())
    val events: StateFlow<List<CountdownEvent>> = _events.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        val dir = context.applicationContext.filesDir
        val f = File(dir, FILE_NAME)
        file = f
        val main = readOrNull(f)
        if (main != null) {
            mainTrusted = true
            _events.value = main
            rollPastToElapsed()
            return
        }
        val recovered = readOrNull(File(dir, BACKUP_NAME))
        _events.value = recovered ?: emptyList()

        if (recovered != null) persist()
        rollPastToElapsed()
    }

    private fun rollPastToElapsed(today: LocalDate = LocalDate.now()) {
        val current = _events.value
        var changed = false
        val next = current.map { e ->
            if (e.mode == EventMode.COUNTDOWN &&
                e.repeat == RepeatRule.NONE &&
                e.anchor.isBefore(today)
            ) {
                changed = true
                e.copy(mode = EventMode.ELAPSED)
            } else {
                e
            }
        }
        if (!changed) return
        _events.value = next
        persist()
    }

    private fun readOrNull(f: File): List<CountdownEvent>? {
        if (!f.exists() || f.length() == 0L) return null
        val raw = runCatching { f.readText() }.getOrNull() ?: return null
        return EventCodec.decodeStrict(raw)
    }

    fun snapshot(context: Context): List<CountdownEvent> {
        init(context)
        return _events.value
    }

    fun upsert(event: CountdownEvent) {
        val current = _events.value
        val idx = current.indexOfFirst { it.id == event.id }
        val old = current.getOrNull(idx)
        _events.value = if (idx >= 0) {
            current.toMutableList().also { it[idx] = event }
        } else {
            current + event
        }
        persist()
        if (old != null && old.cardImage != null && old.cardImage != event.cardImage) {
            appContext?.let { CardImage.remove(it, old.cardImage) }
        }
    }

    fun delete(id: String) {
        _events.value = _events.value.filterNot { it.id == id }
        persist()
        appContext?.let { CardImage.removeAllOf(it, id) }
    }

    fun clearAll() {
        val ids = _events.value.map { it.id }
        _events.value = emptyList()
        persist()
        appContext?.let { ctx -> ids.forEach { CardImage.removeAllOf(ctx, it) } }
    }

    fun mergeIncoming(list: List<CountdownEvent>): Int {
        val existing = _events.value.associateBy { it.id }
        val added = list.filter { it.id !in existing }
        if (added.isNotEmpty()) {
            _events.value = _events.value + added
            persist()
        }
        return added.size
    }

    private fun persist() {
        val f = file ?: return
        val json = runCatching { EventCodec.encode(_events.value) }.getOrNull() ?: return
        val dir = f.parentFile ?: return
        runCatching {
            val tmp = File(dir, TEMP_NAME)
            tmp.writeText(json)
// 只有确认过主文件是好的，才拿它当备份。理由见 [mainTrusted]。
            if (mainTrusted && f.exists() && f.length() > 0L) {
                f.copyTo(File(dir, BACKUP_NAME), overwrite = true)
            }
            if (!tmp.renameTo(f)) {
                f.writeText(json)
                tmp.delete()
            }
            mainTrusted = true
        }
    }
}

