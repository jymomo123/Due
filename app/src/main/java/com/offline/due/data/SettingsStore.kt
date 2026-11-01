package com.offline.due.data

import android.content.Context
import android.content.SharedPreferences
import com.offline.due.domain.CardNumberMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class BgStyle(val label: String) {

    ICE("冰川"),

    SOLID("纯色"),

    IMAGE("图片"),
}

enum class BgTone(val label: String, val solidColor: Long) {

    ICE("淡蓝", DEFAULT_BG_COLOR),

    BLOSSOM("淡粉", 0xFFFCE8EE);

    companion object {

        fun fromColor(argb: Long): BgTone {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val hi = maxOf(r, g, b)
            val lo = minOf(r, g, b)
            val d = hi - lo
            if (hi <= 0f || d / hi < 0.04f) return ICE
            val h = when (hi) {
                r -> (60f * ((g - b) / d) + 360f) % 360f
                g -> 60f * ((b - r) / d) + 120f
                else -> 60f * ((r - g) / d) + 240f
            }
            return if (h < 95f || h >= 285f) BLOSSOM else ICE
        }
    }
}

private const val DEFAULT_BG_COLOR = 0xFFDCE9F9

enum class TextScale(val label: String, val factor: Float) {
    SMALL("小", 0.90f),
    NORMAL("标准", 1.00f),
    LARGE("大", 1.12f),
    XL("特大", 1.25f),
}

enum class RowDensity(
    val label: String,

    val verticalPaddingDp: Float,

    val numberSp: Float,

    val titleScale: Float,
) {
    COMPACT("紧凑", 8f, 36f, 0.90f),
    STANDARD("标准", 13f, 48f, 1.00f),
    LOOSE("宽松", 20f, 60f, 1.08f),
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    val ongoingNotification: Boolean = true,

    val reminderAlert: Boolean = true,

    val headerTitle: String = "如期",

    val headerCountdown: Boolean = true,

    val headerAlmanac: Boolean = true,

    val headerSearch: Boolean = true,

    val yearHeader: Boolean = true,

    val quickAdd: Boolean = true,

    val quickAddHint: Boolean = true,

    val listCardStyle: Boolean = true,

    val betaAck: Boolean = false,

    val cardHintAck: Boolean = false,

// ── 显示 ──────────────────────────────────────────────

    val textScale: TextScale = TextScale.NORMAL,

    val rowDensity: RowDensity = RowDensity.STANDARD,

    val defaultCardMode: CardNumberMode = CardNumberMode.DAY,

    val onboarded: Boolean = false,

// ── 背景 ──────────────────────────────────────────────
    val bgStyle: BgStyle = BgStyle.ICE,

    val bgTone: BgTone = BgTone.ICE,

    val bgToneAuto: Boolean = true,

    val bgColor: Long = DEFAULT_BG_COLOR,

    val bgImage: String? = null,

    val bgScrim: Float = 0.34f,

// ── 桌面小组件 ────────────────────────────────────────

    val widgetEventId: String? = null,
)

object SettingsStore {

    private const val NAME = "due_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ONGOING = "ongoing_notification"
    private const val KEY_REMINDER = "reminder_alert"
    private const val KEY_YEAR_HEADER = "year_header"
    private const val KEY_LIST_CARD = "list_card_style"
    private const val KEY_QUICK_ADD = "quick_add"
    private const val KEY_QUICK_ADD_HINT = "quick_add_hint"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_ROW_DENSITY = "row_density"
    private const val KEY_CARD_MODE = "default_card_mode"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_HEADER_TITLE = "header_title"
    private const val KEY_HEADER_COUNTDOWN = "header_countdown"
    private const val KEY_HEADER_ALMANAC = "header_almanac"
    private const val KEY_HEADER_SEARCH = "header_search"
    private const val KEY_BETA_ACK = "beta_ack"
    private const val KEY_CARD_HINT = "card_hint_ack"
    private const val KEY_BG_STYLE = "bg_style"
    private const val KEY_BG_COLOR = "bg_color"
    private const val KEY_BG_IMAGE = "bg_image"
    private const val KEY_BG_SCRIM = "bg_scrim"
    private const val KEY_BG_TONE = "bg_tone"
    private const val KEY_BG_TONE_AUTO = "bg_tone_auto"
    private const val KEY_WIDGET_EVENT = "widget_event_id"

    private var sp: SharedPreferences? = null

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp = p

        val style = runCatching {
            BgStyle.valueOf(p.getString(KEY_BG_STYLE, BgStyle.ICE.name)!!)
        }.getOrDefault(BgStyle.ICE)
        val color = p.getLong(KEY_BG_COLOR, DEFAULT_BG_COLOR)
        val toneAuto = p.getBoolean(KEY_BG_TONE_AUTO, true)
        val storedTone = runCatching {
            BgTone.valueOf(p.getString(KEY_BG_TONE, BgTone.ICE.name)!!)
        }.getOrDefault(BgTone.ICE)
        val tone = if (toneAuto && style == BgStyle.SOLID) BgTone.fromColor(color) else storedTone
        if (tone != storedTone) p.edit().putString(KEY_BG_TONE, tone.name).apply()

        _state.value = Settings(
            themeMode = runCatching {
                ThemeMode.valueOf(p.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
            }.getOrDefault(ThemeMode.SYSTEM),
            ongoingNotification = p.getBoolean(KEY_ONGOING, true),
            reminderAlert = p.getBoolean(KEY_REMINDER, true),
            yearHeader = p.getBoolean(KEY_YEAR_HEADER, true),
            listCardStyle = p.getBoolean(KEY_LIST_CARD, true),
            quickAdd = p.getBoolean(KEY_QUICK_ADD, true),
            quickAddHint = p.getBoolean(KEY_QUICK_ADD_HINT, true),
            headerTitle = p.getString(KEY_HEADER_TITLE, "如期")?.ifBlank { "如期" } ?: "如期",
            headerCountdown = p.getBoolean(KEY_HEADER_COUNTDOWN, true),
            headerAlmanac = p.getBoolean(KEY_HEADER_ALMANAC, true),
            headerSearch = p.getBoolean(KEY_HEADER_SEARCH, true),
            betaAck = p.getBoolean(KEY_BETA_ACK, false),
            cardHintAck = p.getBoolean(KEY_CARD_HINT, false),
            textScale = runCatching {
                TextScale.valueOf(p.getString(KEY_TEXT_SCALE, TextScale.NORMAL.name)!!)
            }.getOrDefault(TextScale.NORMAL),
            rowDensity = runCatching {
                RowDensity.valueOf(p.getString(KEY_ROW_DENSITY, RowDensity.STANDARD.name)!!)
            }.getOrDefault(RowDensity.STANDARD),
            defaultCardMode = runCatching {
                CardNumberMode.valueOf(
                    p.getString(KEY_CARD_MODE, CardNumberMode.DAY.name)!!
                )
            }.getOrDefault(CardNumberMode.DAY),
            onboarded = p.getBoolean(KEY_ONBOARDED, p.all.isNotEmpty()),
            bgStyle = style,
            bgColor = color,
            bgImage = p.getString(KEY_BG_IMAGE, null)?.takeIf { it.isNotBlank() },
            bgScrim = p.getFloat(KEY_BG_SCRIM, 0.34f).coerceIn(0f, 1f),
            bgTone = tone,
            bgToneAuto = toneAuto,
// 老版本没有这个键。缺省 null = 自动，也就是老版本的行为。
            widgetEventId = p.getString(KEY_WIDGET_EVENT, null)?.takeIf { it.isNotBlank() },
        )
    }

    fun setBetaAck() {
        sp?.edit()?.putBoolean(KEY_BETA_ACK, true)?.apply()
        _state.value = _state.value.copy(betaAck = true)
    }

    fun setCardHintAck() {
        sp?.edit()?.putBoolean(KEY_CARD_HINT, true)?.apply()
        _state.value = _state.value.copy(cardHintAck = true)
    }

    fun setTheme(mode: ThemeMode) {
        sp?.edit()?.putString(KEY_THEME, mode.name)?.apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setOngoing(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_ONGOING, enabled)?.apply()
        _state.value = _state.value.copy(ongoingNotification = enabled)
    }

    fun setReminderAlert(enabled: Boolean) {
        val alsoOngoing = !enabled && _state.value.ongoingNotification
        val editor = sp?.edit()
        editor?.putBoolean(KEY_REMINDER, enabled)
        if (alsoOngoing) editor?.putBoolean(KEY_ONGOING, false)
        editor?.apply()
        _state.value = _state.value.copy(
            reminderAlert = enabled,
            ongoingNotification = if (alsoOngoing) false else _state.value.ongoingNotification,
        )
    }

    fun setYearHeader(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_YEAR_HEADER, enabled)?.apply()
        _state.value = _state.value.copy(yearHeader = enabled)
    }

    fun setQuickAdd(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_QUICK_ADD, enabled)?.apply()
        _state.value = _state.value.copy(quickAdd = enabled)
    }

    fun setQuickAddHint(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_QUICK_ADD_HINT, enabled)?.apply()
        _state.value = _state.value.copy(quickAddHint = enabled)
    }

    fun setListCardStyle(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_LIST_CARD, enabled)?.apply()
        _state.value = _state.value.copy(listCardStyle = enabled)
    }

    fun setTextScale(scale: TextScale) {
        sp?.edit()?.putString(KEY_TEXT_SCALE, scale.name)?.apply()
        _state.value = _state.value.copy(textScale = scale)
    }

    fun setRowDensity(density: RowDensity) {
        sp?.edit()?.putString(KEY_ROW_DENSITY, density.name)?.apply()
        _state.value = _state.value.copy(rowDensity = density)
    }

    fun setOnboarded(done: Boolean = true) {
        sp?.edit()?.putBoolean(KEY_ONBOARDED, done)?.apply()
        _state.value = _state.value.copy(onboarded = done)
    }

    fun setDefaultCardMode(mode: CardNumberMode) {
        sp?.edit()?.putString(KEY_CARD_MODE, mode.name)?.apply()
        _state.value = _state.value.copy(defaultCardMode = mode)
    }

    fun setHeaderTitle(title: String) {
        val v = title.trim().ifBlank { "如期" }
        sp?.edit()?.putString(KEY_HEADER_TITLE, v)?.apply()
        _state.value = _state.value.copy(headerTitle = v)
    }

    fun setHeaderCountdown(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_HEADER_COUNTDOWN, enabled)?.apply()
        _state.value = _state.value.copy(headerCountdown = enabled)
    }

    fun setHeaderAlmanac(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_HEADER_ALMANAC, enabled)?.apply()
        _state.value = _state.value.copy(headerAlmanac = enabled)
    }

    fun setHeaderSearch(enabled: Boolean) {
        sp?.edit()?.putBoolean(KEY_HEADER_SEARCH, enabled)?.apply()
        _state.value = _state.value.copy(headerSearch = enabled)
    }

// ── 背景 ──────────────────────────────────────────────

    fun setBgStyle(style: BgStyle) {
        sp?.edit()?.putString(KEY_BG_STYLE, style.name)?.apply()
        _state.value = _state.value.copy(bgStyle = style)
    }

    fun setBgTone(tone: BgTone, manual: Boolean = true) {
        val old = _state.value
        val untouchedSolid = old.bgColor == DEFAULT_BG_COLOR
        sp?.edit()?.apply {
            putString(KEY_BG_TONE, tone.name)
            if (untouchedSolid) putLong(KEY_BG_COLOR, tone.solidColor)
            if (manual) putBoolean(KEY_BG_TONE_AUTO, false)
        }?.apply()
        _state.value = old.copy(
            bgTone = tone,
            bgColor = if (untouchedSolid) tone.solidColor else old.bgColor,
            bgToneAuto = if (manual) false else old.bgToneAuto,
        )
    }

    fun setBgColor(argb: Long) {
        sp?.edit()?.putLong(KEY_BG_COLOR, argb)?.apply()
        _state.value = _state.value.copy(bgColor = argb)
    }

    fun setBgImage(name: String?) {
        sp?.edit()?.apply {
            if (name == null) remove(KEY_BG_IMAGE) else putString(KEY_BG_IMAGE, name)
        }?.apply()
        _state.value = _state.value.copy(bgImage = name)
    }

    fun setBgScrim(value: Float) {
        val v = value.coerceIn(0f, 1f)
        sp?.edit()?.putFloat(KEY_BG_SCRIM, v)?.apply()
        _state.value = _state.value.copy(bgScrim = v)
    }

// ── 桌面小组件 ────────────────────────────────────────

    fun setWidgetEvent(id: String?) {
        val v = id?.takeIf { it.isNotBlank() }
        sp?.edit()?.apply {
            if (v == null) remove(KEY_WIDGET_EVENT) else putString(KEY_WIDGET_EVENT, v)
        }?.apply()
        _state.value = _state.value.copy(widgetEventId = v)
    }
}

