package com.offline.due

import android.app.Application
import com.offline.due.data.EventStore
import com.offline.due.data.SettingsStore
import com.offline.due.notify.Notifications
import com.offline.due.notify.ReminderScheduler
import com.offline.due.ui.QuickHints

class CountdownApp : Application() {

    override fun onCreate() {
        super.onCreate()
        EventStore.init(this)
        SettingsStore.init(this)
        Notifications.createChannels(this)
        QuickHints.start(this)
// 进程冷启动时补排一次闹钟：系统可能因省电策略清掉过
        ReminderScheduler.rescheduleAll(this, EventStore.snapshot(this))
        if (SettingsStore.state.value.ongoingNotification) {
            Notifications.updateOngoing(this, EventStore.snapshot(this))
        }
    }
}

