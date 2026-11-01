package com.offline.due.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.offline.due.MainActivity
import com.offline.due.R
import com.offline.due.data.CountdownEvent
import com.offline.due.domain.CountdownInfo
import com.offline.due.domain.CountdownMath

object Notifications {

    const val CHANNEL_REMINDER = "reminder"
    const val CHANNEL_ONGOING = "ongoing"

    private const val ID_ONGOING = 1001

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            "事件提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "事件到期或提前提醒" }

        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "常驻倒数",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在通知栏常驻显示最近一个事件的倒数"
            setShowBadge(false)
        }

        nm.createNotificationChannel(reminder)
        nm.createNotificationChannel(ongoing)
    }

    private fun contentIntent(context: Context, eventId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (eventId != null) putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getActivity(
            context,
            eventId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun showReminder(context: Context, event: CountdownEvent, info: CountdownInfo) {
// 倒数/计时两种模式的措辞不同，统一交给 CountdownMath，避免这里再写一遍 when
        val text = CountdownMath.caption(info)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${info.target} · $text"))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, event.id))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
        }
    }

    fun updateOngoing(context: Context, events: List<CountdownEvent>) {
        val nm = NotificationManagerCompat.from(context)
        if (events.isEmpty()) {
            nm.cancel(ID_ONGOING)
            return
        }

        val today = java.time.LocalDate.now()
        val nearest = CountdownMath.nearest(events, today) ?: return
        val (event, info) = nearest

        val text = CountdownMath.caption(info)

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(context, event.id))
            .build()

        runCatching { nm.notify(ID_ONGOING, notification) }
    }

    fun cancelOngoing(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_ONGOING) }
    }
}

