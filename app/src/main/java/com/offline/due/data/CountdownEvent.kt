package com.offline.due.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class RepeatRule(val label: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年");

    companion object {
        fun from(name: String?): RepeatRule =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

enum class RemindRule(val label: String) {
    ONCE("只在当天"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月");

    fun effective(mode: EventMode): RemindRule =
        if (mode == EventMode.ELAPSED && this == ONCE) DAILY else this

    companion object {
        fun from(name: String?): RemindRule =
            entries.firstOrNull { it.name == name } ?: ONCE

        fun options(mode: EventMode): List<RemindRule> =
            if (mode == EventMode.ELAPSED) entries.filter { it != ONCE } else entries.toList()
    }
}

enum class EventMode(val label: String, val hint: String) {
    COUNTDOWN("倒数", "离目标日还有几天"),
    ELAPSED("计时", "从起始日走到今天");

    companion object {
        fun from(name: String?): EventMode =
            entries.firstOrNull { it.name == name } ?: COUNTDOWN
    }
}

data class CountdownEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val anchor: LocalDate,
    val mode: EventMode = EventMode.COUNTDOWN,

    val endDate: LocalDate? = null,

    val includeStartDay: Boolean = false,

    val showSeconds: Boolean = false,
    val repeat: RepeatRule = RepeatRule.NONE,
    val category: String = DEFAULT_CATEGORY,

    val color: Long = DEFAULT_COLOR,

    val cardColor: Long = NO_COLOR,

    val numberColor: Long = NO_COLOR,

    val cardImage: String? = null,
    val pinned: Boolean = false,
    val note: String = "",

    val capsule: Boolean = false,
    val capsuleText: String = "",
    val remind: Boolean = false,
    val remindHour: Int = 9,
    val remindMinute: Int = 0,

    val remindRule: RemindRule = RemindRule.ONCE,

    val remindWeekday: Int = 0,

    val remindDayOfMonth: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("anchor", anchor.toString())
        put("mode", mode.name)
        put("endDate", endDate?.toString() ?: JSONObject.NULL)
        put("includeStartDay", includeStartDay)
        put("showSeconds", showSeconds)
        put("repeat", repeat.name)
        put("category", category)
        put("color", color)
        put("cardColor", cardColor)
        put("numberColor", numberColor)
        put("cardImage", cardImage ?: JSONObject.NULL)
        put("pinned", pinned)
        put("note", note)
        put("capsule", capsule)
        put("capsuleText", capsuleText)
        put("remind", remind)
        put("remindHour", remindHour)
        put("remindMinute", remindMinute)
        put("remindRule", remindRule.name)
        put("remindWeekday", remindWeekday)
        put("remindDayOfMonth", remindDayOfMonth)
        put("createdAt", createdAt)
    }

    companion object {
        const val DEFAULT_CATEGORY = "生活"
        const val DEFAULT_COLOR = 0xFF4A8FE7

        const val NO_COLOR = 0L

        val CATEGORY_PRESETS = listOf("生活", "生日", "纪念日", "工作", "学习", "账单", "旅行")

        val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        fun weekdayName(value: Int): String? = WEEKDAY_NAMES.getOrNull(value - 1)

        val COLOR_PRESETS = listOf(
            0xFF4A8FE7, // 冰川蓝
            0xFF5AA9A0, // 雾青
            0xFF8E7BE8, // 薰衣草
            0xFFE8735A, // 珊瑚
            0xFFD99A3C, // 琥珀
            0xFF6B9E5A, // 苔绿
            0xFFC97BA8, // 藕荷
            0xFF6B7C8F, // 石墨
        )

        private val COLOR_NAMES = listOf(
            "冰川蓝", "雾青", "薰衣草", "珊瑚", "琥珀", "苔绿", "藕荷", "石墨",
        )

        fun colorName(argb: Long): String {
            if (argb == NO_COLOR) return "不着色"
            val i = COLOR_PRESETS.indexOf(argb)
            return if (i >= 0) COLOR_NAMES[i] else hexOf(argb)
        }

        fun hexOf(argb: Long): String =
            "#%02X%02X%02X".format(
                ((argb shr 16) and 0xFF).toInt(),
                ((argb shr 8) and 0xFF).toInt(),
                (argb and 0xFF).toInt(),
            )

        fun fromJson(o: JSONObject): CountdownEvent? = runCatching {
            val mode = EventMode.from(o.optString("mode"))
            CountdownEvent(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = o.optString("title", "未命名"),
                anchor = LocalDate.parse(o.getString("anchor")),
                mode = mode,
// optString 对 JSONObject.NULL 会返回 "null" 字面量，这里必须判空
                endDate = o.optString("endDate")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                includeStartDay = o.optBoolean("includeStartDay", false),
                showSeconds = o.optBoolean("showSeconds", false),
                repeat = RepeatRule.from(o.optString("repeat")),
                category = o.optString("category", DEFAULT_CATEGORY),
                color = o.optLong("color", DEFAULT_COLOR),
                cardColor = o.optLong("cardColor", NO_COLOR),
                numberColor = o.optLong("numberColor", NO_COLOR),
// 老备份里没有这个字段；optString 对 JSONObject.NULL 会返回 "null" 字面量
                cardImage = o.optString("cardImage")
                    .takeIf { it.isNotBlank() && it != "null" },
                pinned = o.optBoolean("pinned", false),
                note = o.optString("note", ""),
                capsule = o.optBoolean("capsule", false),
                capsuleText = o.optString("capsuleText", ""),
                remind = o.optBoolean("remind", false),
                remindHour = o.optInt("remindHour", 9),
                remindMinute = o.optInt("remindMinute", 0),
                remindRule = RemindRule.from(o.optString("remindRule")),
                remindWeekday = o.optInt("remindWeekday", 0).coerceIn(0, 7),
                remindDayOfMonth = o.optInt("remindDayOfMonth", 0).coerceIn(0, 31),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }.getOrNull()
    }
}

object EventCodec {

    const val SCHEMA_VERSION = 5

    fun encode(events: List<CountdownEvent>): String {
        val arr = JSONArray()
        events.forEach { arr.put(it.toJson()) }
        return JSONObject().apply {
            put("app", "due")
            put("version", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("events", arr)
        }.toString(2)
    }

    fun decode(raw: String): List<CountdownEvent> = decodeStrict(raw) ?: emptyList()

    fun decodeStrict(raw: String): List<CountdownEvent>? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return runCatching {
            if (text.startsWith("[")) {
                val arr = JSONArray(text)
                (0 until arr.length()).mapNotNull { EventJsonHelper.from(arr.optJSONObject(it)) }
            } else {
                val root = JSONObject(text)
                val arr = root.optJSONArray("events") ?: JSONArray()
                (0 until arr.length()).mapNotNull { EventJsonHelper.from(arr.optJSONObject(it)) }
            }
        }.getOrNull()
    }
}

private object EventJsonHelper {
    fun from(o: JSONObject?): CountdownEvent? = o?.let { CountdownEvent.fromJson(it) }
}

