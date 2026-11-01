package com.offline.due.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.offline.due.MainActivity
import com.offline.due.R
import com.offline.due.data.BgTone
import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventMode
import com.offline.due.data.EventStore
import com.offline.due.data.SettingsStore
import com.offline.due.data.ThemeMode
import com.offline.due.domain.ColorMath
import com.offline.due.domain.CountdownInfo
import com.offline.due.domain.CountdownMath
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        update(context, appWidgetManager, appWidgetId)
    }

    override fun onEnabled(context: Context) {
        refreshAll(context)
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(midnightIntent(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    companion object {

        const val ACTION_REFRESH = "com.offline.due.WIDGET_REFRESH"

        private const val MIDNIGHT_REQUEST = 40210

        private const val DAY_MS = 24L * 60L * 60L * 1000L

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日")

// 亮色
        private const val LIGHT_TITLE = 0xFF4C6684.toInt()
        private const val LIGHT_NUMBER = 0xFF14263C.toInt()
        private const val LIGHT_SUB = 0xFF6B83A0.toInt()

// 深色（夜冰）
        private const val DARK_TITLE = 0xFF93AAC4.toInt()
        private const val DARK_NUMBER = 0xFFE8F1FA.toInt()
        private const val DARK_SUB = 0xFF7C93AC.toInt()

        private const val LIGHT_TITLE_PINK = 0xFF844C5F.toInt()
        private const val LIGHT_NUMBER_PINK = 0xFF3C1421.toInt()
        private const val LIGHT_SUB_PINK = 0xFFA06B7C.toInt()

        private const val DARK_TITLE_PINK = 0xFFC493A3.toInt()
        private const val DARK_NUMBER_PINK = 0xFFFAE8EF.toInt()
        private const val DARK_SUB_PINK = 0xFFAC7C8D.toInt()

        private const val ACCENT_IDLE_LIGHT = 0x338CA4BE.toInt()
        private const val ACCENT_IDLE_DARK = 0x337C93AC.toInt()

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            ids.forEach { update(context, mgr, it) }
        }

        fun update(context: Context, mgr: AppWidgetManager, widgetId: Int) {
            EventStore.init(context)
            SettingsStore.init(context)

            val dark = isDark(context)
            val (wDp, hDp) = sizeOf(context, mgr, widgetId)

            val wide = wDp.toFloat() / hDp >= 2.6f
            val views = RemoteViews(
                context.packageName,
                if (wide) R.layout.widget_today_wide else R.layout.widget_today,
            )

            val pink = SettingsStore.state.value.bgTone == BgTone.BLOSSOM

            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                when {
                    pink && dark -> R.drawable.widget_bg_blossom_dark
                    pink -> R.drawable.widget_bg_blossom
                    dark -> R.drawable.widget_bg_dark
                    else -> R.drawable.widget_bg
                },
            )
            val titleColor = when {
                pink && dark -> DARK_TITLE_PINK
                pink -> LIGHT_TITLE_PINK
                dark -> DARK_TITLE
                else -> LIGHT_TITLE
            }
            val subColor = when {
                pink && dark -> DARK_SUB_PINK
                pink -> LIGHT_SUB_PINK
                dark -> DARK_SUB
                else -> LIGHT_SUB
            }

            val events = EventStore.snapshot(context)
            val today = LocalDate.now()

            val pinned = SettingsStore.state.value.widgetEventId
                ?.let { id -> events.firstOrNull { it.id == id } }
            val nearest = pinned
                ?.let { it to CountdownMath.occurrence(it, today) }
                ?: pickNearest(events, today)

            val numberColor = nearest
                ?.first
                ?.numberColor
                ?.takeIf { it != CountdownEvent.NO_COLOR }
                ?.let { ColorMath.readable(it.toInt(), dark) }
                ?: when {
                    pink && dark -> DARK_NUMBER_PINK
                    pink -> LIGHT_NUMBER_PINK
                    dark -> DARK_NUMBER
                    else -> LIGHT_NUMBER
                }

            views.setTextColor(R.id.widget_title, titleColor)
            views.setTextColor(R.id.widget_number, numberColor)
            views.setTextColor(R.id.widget_date, subColor)
            views.setTextColor(R.id.widget_precise, subColor)

            val innerW = (wDp - 14 * 2 - 3 - 12).coerceAtLeast(40)
            val innerH = (hDp - 11 * 2).coerceAtLeast(24)

// 各行的固定高度（dp）：字号 × 1.15 是字体行盒，日期和秒再加 2dp 上间距
            val titleH = 11f * 1.15f
            val dateH = 10f * 1.15f + 2f
            val clockH = 11f * 1.15f + 2f

            val empty = nearest == null
            val numberText = when {
                nearest == null -> "—"
                nearest.second.isToday -> "今天"
                else -> CountdownMath.headline(nearest.second)
            }
            val numEm = emWidth(numberText).coerceAtLeast(0.1f)

            val showTitle: Boolean
            val showDate: Boolean
            val showClock: Boolean
            val numberDp: Float

            if (wide) {
                showTitle = true
                showDate = empty || innerH >= titleH + dateH
                showClock = !empty && innerH >= titleH + dateH + clockH
                val byWidth = (innerW * 0.55f) / numEm
                val byHeight = innerH / 1.15f
                numberDp = min(byWidth, byHeight).coerceIn(16f, 80f)
            } else {
                val minNumH = 20f * 1.15f
                showTitle = empty || innerH - titleH - dateH >= minNumH
                showDate = empty || innerH - dateH >= minNumH
                showClock = !empty && showDate &&
                    innerH - (if (showTitle) titleH else 0f) - dateH - clockH >= minNumH

                val usedH = (if (showTitle) titleH else 0f) +
                    (if (showDate) dateH else 0f) +
                    (if (showClock) clockH else 0f)
                val byWidth = innerW / numEm
                val byHeight = (innerH - usedH) / 1.15f
                numberDp = min(byWidth, byHeight).coerceIn(16f, 64f)
            }

            views.setTextViewTextSize(R.id.widget_number, TypedValue.COMPLEX_UNIT_DIP, numberDp)
            views.setViewVisibility(R.id.widget_title, if (showTitle) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_date, if (showDate) View.VISIBLE else View.GONE)

            if (nearest == null) {
                views.setTextViewText(R.id.widget_title, "如期")
                views.setTextViewText(R.id.widget_number, "—")
                views.setTextViewText(R.id.widget_date, "点一下添加第一个")
                views.setViewVisibility(R.id.widget_precise, View.GONE)
                views.setInt(
                    R.id.widget_accent,
                    "setBackgroundColor",
                    if (dark) ACCENT_IDLE_DARK else ACCENT_IDLE_LIGHT,
                )
            } else {
                val (event, info) = nearest
                views.setTextViewText(R.id.widget_title, event.title)
                views.setTextViewText(R.id.widget_number, numberText)

                val suffix = when {
                    info.isToday -> "就是今天"
                    info.mode == EventMode.ELAPSED && info.hasEnd ->
                        "已走过 ${info.days} 天 · 共 ${info.totalDays} 天"
                    info.mode == EventMode.ELAPSED -> "已走过 ${info.days} 天"
                    info.isFuture -> "还有 ${info.absDays} 天"
                    else -> "已过 ${info.absDays} 天"
                }
                val date = if (info.mode == EventMode.ELAPSED) info.cycleStart else info.target
                views.setTextViewText(
                    R.id.widget_date,
                    "${date.format(DATE_FORMAT)} · $suffix",
                )

// 事件色：整个小组件唯一的彩色
                views.setInt(R.id.widget_accent, "setBackgroundColor", event.color.toInt())

                bindPrecise(views, event, allow = showClock)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                nearest?.let { putExtra(MainActivity.EXTRA_EVENT_ID, it.first.id) }
            }
            val pi = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(widgetId, views)

            scheduleMidnight(context)
        }

        private fun emWidth(text: String): Float =
            text.sumOf { c -> if (c.code < 0x2E80) 0.555 else 1.0 }.toFloat()

        private fun bindPrecise(views: RemoteViews, event: CountdownEvent, allow: Boolean) {
            if (!allow || !event.showSeconds) {
                views.setViewVisibility(R.id.widget_precise, View.GONE)
                return
            }
            val zone = ZoneId.systemDefault()
            val nowWall = System.currentTimeMillis()
            val startOfToday = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

            val countDown = event.mode == EventMode.COUNTDOWN
            val subDay = if (countDown) {
                val target = CountdownMath.nextDate(event.anchor, event.repeat, LocalDate.now())
                val targetMillis = target.atStartOfDay(zone).toInstant().toEpochMilli()
                (targetMillis - nowWall).mod(DAY_MS)
            } else {
                (nowWall - startOfToday).mod(DAY_MS)
            }

            val base = if (countDown) {
                SystemClock.elapsedRealtime() + subDay
            } else {
                SystemClock.elapsedRealtime() - subDay
            }
            views.setViewVisibility(R.id.widget_precise, View.VISIBLE)
            views.setChronometerCountDown(R.id.widget_precise, countDown)
            views.setChronometer(R.id.widget_precise, base, "%s", true)
        }

        private fun scheduleMidnight(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val zone = ZoneId.systemDefault()
            val next = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(zone)
                .plusSeconds(2)
                .toInstant()
                .toEpochMilli()
// 已经过去了（比如刚好在零点后 2 秒内），就排到明天
            val at = if (next <= System.currentTimeMillis()) next + DAY_MS else next
            runCatching {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, midnightIntent(context))
            }
        }

        private fun midnightIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                MIDNIGHT_REQUEST,
                Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun sizeOf(
            context: Context,
            mgr: AppWidgetManager,
            widgetId: Int,
        ): Pair<Int, Int> {
            val o = runCatching { mgr.getAppWidgetOptions(widgetId) }.getOrNull()
                ?: return 110 to 56
            val portrait =
                context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val w = o.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                110,
            )
            val h = o.getInt(
                if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                56,
            )
            return w to h
        }

        private fun pickNearest(
            events: List<CountdownEvent>,
            today: LocalDate,
        ): Pair<CountdownEvent, CountdownInfo>? = CountdownMath.nearest(events, today)

        private fun isDark(context: Context): Boolean =
            when (SettingsStore.state.value.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> {
                    val night = context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                    night == Configuration.UI_MODE_NIGHT_YES
                }
            }
    }
}

