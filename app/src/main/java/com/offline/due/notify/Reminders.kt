package com.offline.due.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventMode
import com.offline.due.data.EventStore
import com.offline.due.data.RemindRule
import com.offline.due.data.SettingsStore
import com.offline.due.domain.CountdownMath
import com.offline.due.widget.TodayWidgetProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ReminderScheduler {

    const val ACTION_REMIND = "com.offline.due.REMIND"

    private fun allowed(): Boolean = SettingsStore.state.value.reminderAlert

    fun rescheduleAll(context: Context, events: List<CountdownEvent>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val on = allowed()
        events.forEach { event ->
            val pi = pendingIntent(context, event.id)
            if (!on || !event.remind) {
                am.cancel(pi)
                return@forEach
            }
            val trigger = nextTriggerMillis(event) ?: run {
                am.cancel(pi)
                return@forEach
            }
            scheduleAt(am, trigger, pi)
        }
    }

    fun scheduleOne(context: Context, event: CountdownEvent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, event.id)
        val trigger = nextTriggerMillis(event)
        if (!allowed() || !event.remind || trigger == null) {
            am.cancel(pi)
            return
        }
        scheduleAt(am, trigger, pi)
    }

    fun cancel(context: Context, eventId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, eventId))
    }

    private fun scheduleAt(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            val ok = runCatching {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }.isSuccess
            if (ok) return
        }
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun pendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun nextTriggerMillis(
        event: CountdownEvent,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long? {
        val time = LocalTime.of(
            event.remindHour.coerceIn(0, 23),
            event.remindMinute.coerceIn(0, 59),
        )
        val today = now.toLocalDate()
        val rule = event.remindRule.effective(event.mode)

        val at = if (event.mode == EventMode.ELAPSED) {
            elapsedAt(event, today, rule, time, now)
        } else {
            countdownAt(event, today, rule, time, now)
        } ?: return null

        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun elapsedAt(
        event: CountdownEvent,
        today: LocalDate,
        rule: RemindRule,
        time: LocalTime,
        now: LocalDateTime,
    ): LocalDateTime? {
        val end = event.endDate
        if (end != null && today.isAfter(end)) return null

// 起始日还没到的计时，从起始日那天开始响。不然会每天推一条"已走过 0 天"。
        val start = if (today.isBefore(event.anchor)) event.anchor else today
        val at = when (rule) {
            RemindRule.ONCE, RemindRule.DAILY -> {
                val first = start.atTime(time)
                if (first.isAfter(now)) first else first.plusDays(1)
            }
            RemindRule.WEEKLY -> {
                val wd = event.remindWeekday.takeIf { it in 1..7 } ?: start.dayOfWeek.value
                val ahead = ((wd - start.dayOfWeek.value) + 7) % 7
                val first = start.plusDays(ahead.toLong()).atTime(time)
                if (first.isAfter(now)) first else first.plusWeeks(1)
            }
            RemindRule.MONTHLY -> {
                val dom = event.remindDayOfMonth.takeIf { it in 1..31 } ?: start.dayOfMonth
                var month = YearMonth.from(start)
                var first = monthDayIn(month, dom).atTime(time)
                if (first.toLocalDate().isBefore(start)) {
                    month = month.plusMonths(1)
                    first = monthDayIn(month, dom).atTime(time)
                }
                if (first.isAfter(now)) first else monthDayIn(month.plusMonths(1), dom).atTime(time)
            }
        }
        if (end != null && at.toLocalDate().isAfter(end)) return null
        return at
    }

    private fun countdownAt(
        event: CountdownEvent,
        today: LocalDate,
        rule: RemindRule,
        time: LocalTime,
        now: LocalDateTime,
    ): LocalDateTime? {
        for (attempt in 0..1) {
            val from = today.plusDays(attempt.toLong())
            val target = CountdownMath.occurrence(event, from).target
            val day = remindDay(
                target = target,
                from = from,
                rule = rule,
                weekday = event.remindWeekday,
                monthDay = event.remindDayOfMonth,
            ) ?: continue
            val at = day.atTime(time)
            if (at.isAfter(now)) return at
        }
        return null
    }

    private fun remindDay(
        target: LocalDate,
        from: LocalDate,
        rule: RemindRule,
        weekday: Int,
        monthDay: Int,
    ): LocalDate? {
        if (target.isBefore(from)) return null
        val best: LocalDate? = when (rule) {
            RemindRule.ONCE -> target
            RemindRule.DAILY -> from
            RemindRule.WEEKLY -> {
// 0 = 跟目标日同一个星期几（老事件的默认）。
                val wd = weekday.takeIf { it in 1..7 } ?: target.dayOfWeek.value
                val anchor = target.minusDays(((target.dayOfWeek.value - wd + 7) % 7).toLong())
                if (anchor.isBefore(from)) {
                    null
                } else {
                    anchor.minusDays((ChronoUnit.DAYS.between(from, anchor) / 7L) * 7L)
                }
            }
            RemindRule.MONTHLY -> {
// 0 = 跟目标日同一个号（老事件的默认）。
                val dom = monthDay.takeIf { it in 1..31 } ?: target.dayOfMonth
                var month = YearMonth.from(target)
                var anchor = monthDayIn(month, dom)
                if (anchor.isAfter(target)) {
                    month = month.minusMonths(1)
                    anchor = monthDayIn(month, dom)
                }
                if (anchor.isBefore(from)) {
                    null
                } else {
                    var day = anchor
                    var k = 1L
                    while (k <= 240L) {
                        val candidate = monthDayIn(month.minusMonths(k), dom)
                        if (candidate.isBefore(from)) break
                        day = candidate
                        k++
                    }
                    day
                }
            }
        }
// 见上面「目标日当天总会有一条」。
        return best ?: target
    }

    private fun monthDayIn(month: YearMonth, dom: Int): LocalDate =
        month.atDay(dom.coerceIn(1, month.lengthOfMonth()))

    const val EXTRA_EVENT_ID = "extra_event_id"
}

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return

        if (!SettingsStore.state.value.reminderAlert) return

        EventStore.init(context)
        val event = EventStore.snapshot(context).firstOrNull { it.id == id } ?: return

        val today = LocalDate.now()
        val info = CountdownMath.occurrence(event, today)
        Notifications.showReminder(context, event, info)

        ReminderScheduler.scheduleOne(context, event)
        Notifications.updateOngoing(context, EventStore.snapshot(context))
    }
}

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        EventStore.init(context)
        ReminderScheduler.rescheduleAll(context, EventStore.snapshot(context))
        Notifications.updateOngoing(context, EventStore.snapshot(context))
        TodayWidgetProvider.refreshAll(context)
    }
}

