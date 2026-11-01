package com.offline.due.domain

import com.offline.due.data.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object NaturalDateParser {

    data class Parsed(
        val title: String,
        val date: LocalDate,
        val repeat: RepeatRule,
        val explanation: String,
    )

    private const val EMPTY_TITLE = "新事件"

// ---------- 正则 ----------

    private val absolute = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*[日号]?""")
    private val everyYear = Regex("""每\s*年\s*(?:(\d{1,2})\s*月\s*)?(\d{1,2})\s*[日号]?""")
    private val everyMonth = Regex("""每\s*(?:个)?\s*月\s*(\d{1,2})\s*[日号]""")
    private val nextMonth = Regex("""下\s*个?\s*月\s*(\d{1,2})\s*[日号]""")
    private val monthDay = Regex("""(\d{1,2})\s*[月\-/]\s*(\d{1,2})\s*[日号]?""")
    private val relativeDay = Regex("""(大后天|后天|明天|明日|今天|今日)""")

    private val offset = Regex(
        """(\d{1,3}|[一二两三四五六七八九十]{1,3})\s*(天|日|周|星期|个月|月|年)\s*(后|以后|之后|前|以前|之前)"""
    )

    private val weekday = Regex("""(下下|下|本|这|每)?\s*(?:周|星期|礼拜)\s*([一二三四五六日天1-7])""")

    private val repeatWord = Regex("""每\s*(?:个)?\s*(?:年|月|周|星期|天|日)""")

// ---------- 重复规则推断 ----------

    private val yearlyWords = listOf("生日", "纪念日", "周年", "结婚", "忌日", "祭日", "入职")
    private val monthlyWords = listOf("房租", "还款", "信用卡", "工资", "账单", "月供", "花呗", "贷款", "水电", "物业")
    private val weeklyWords = listOf("例会", "周会", "站会", "复盘")

    fun inferRepeat(text: String): RepeatRule? = when {
        yearlyWords.any { text.contains(it) } -> RepeatRule.YEARLY
        monthlyWords.any { text.contains(it) } -> RepeatRule.MONTHLY
        weeklyWords.any { text.contains(it) } -> RepeatRule.WEEKLY
        else -> null
    }

// ---------- 主入口 ----------

    fun parse(raw: String, today: LocalDate = LocalDate.now()): Parsed? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        val hit = matchDate(input, today)
        val explicit = explicitRepeat(input)

        if (hit == null && explicit == null) return null

        val inferred = if (hit?.kind == DateKind.ABSOLUTE) inferRepeat(input) else null
        val repeat = explicit ?: inferred ?: RepeatRule.NONE

// 没有命中日期时，用重复词本身当要剥掉的片段。
        val range = hit?.range ?: repeatWord.find(input)?.range
        val title = if (range == null) input else stripPhrase(input, range)

        val base = hit ?: Hit(today, IntRange.EMPTY, DateKind.RELATIVE)
        val finalDate = alignAnchorToRule(base.date, repeat, today, base.day, base.kind)

        return Parsed(
            title = title.ifBlank { EMPTY_TITLE },
            date = finalDate,
            repeat = repeat,
            explanation = buildExplanation(finalDate, repeat, today),
        )
    }

// ---------- 日期匹配 ----------

    private enum class DateKind { ABSOLUTE, RELATIVE }

    private data class Hit(
        val date: LocalDate,
        val range: IntRange,
        val kind: DateKind,
        val day: Int = date.dayOfMonth,
    )

    private fun matchDate(input: String, today: LocalDate): Hit? {
        absolute.find(input)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val d = m.groupValues[3].toIntOrNull() ?: return@let
            if (mo !in 1..12 || d !in 1..31) return@let
            return Hit(CountdownMath.clampToMonth(y, mo, d), m.range, DateKind.ABSOLUTE, d)
        }

        everyYear.find(input)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull() ?: today.monthValue
            val d = m.groupValues[2].toIntOrNull() ?: return@let
            if (mo !in 1..12 || d !in 1..31) return@let
            return Hit(CountdownMath.clampToMonth(today.year, mo, d), m.range, DateKind.ABSOLUTE, d)
        }

        everyMonth.find(input)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            if (d !in 1..31) return@let
            return Hit(CountdownMath.clampToMonth(today.year, today.monthValue, d), m.range, DateKind.ABSOLUTE, d)
        }

        nextMonth.find(input)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            if (d !in 1..31) return@let
            val next = today.plusMonths(1)
            return Hit(CountdownMath.clampToMonth(next.year, next.monthValue, d), m.range, DateKind.RELATIVE, d)
        }

        relativeDay.find(input)?.let { m ->
            val date = when (m.groupValues[1]) {
                "今天", "今日" -> today
                "明天", "明日" -> today.plusDays(1)
                "后天" -> today.plusDays(2)
                else -> today.plusDays(3)
            }
            return Hit(date, m.range, DateKind.RELATIVE)
        }

        offset.find(input)?.let { m ->
            val before = if (m.range.first > 0) input[m.range.first - 1] else ' '
            if (before == '月' || before == '日' || before == '号') return@let

            val n = parseCount(m.groupValues[1]) ?: return@let
            val unit = m.groupValues[2]
            val forward = m.groupValues[3].let { it == "后" || it == "以后" || it == "之后" }
            val signed = if (forward) n else -n
            val date = when (unit) {
                "天", "日" -> today.plusDays(signed)
                "周", "星期" -> today.plusWeeks(signed)
                "个月", "月" -> today.plusMonths(signed)
                else -> today.plusYears(signed)
            }
            return Hit(date, m.range, DateKind.RELATIVE)
        }

        weekday.find(input)?.let { m ->
            val prefix = m.groupValues[1]
            val dow = parseDow(m.groupValues[2]) ?: return@let
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val base = when (prefix) {
                "下下" -> monday.plusWeeks(2)
                "下" -> monday.plusWeeks(1)
                else -> monday
            }
            var date = base.with(dow)
            if (prefix != "下" && prefix != "下下" && date.isBefore(today)) {
                date = date.plusWeeks(1)
            }
            return Hit(date, m.range, DateKind.RELATIVE)
        }

        monthDay.find(input)?.let { m ->
            val mo = m.groupValues[1].toIntOrNull() ?: return@let
            val d = m.groupValues[2].toIntOrNull() ?: return@let
            if (mo !in 1..12 || d !in 1..31) return@let
            var date = CountdownMath.clampToMonth(today.year, mo, d)
            if (date.isBefore(today)) date = CountdownMath.clampToMonth(today.year + 1, mo, d)
            return Hit(date, m.range, DateKind.ABSOLUTE, d)
        }

        return null
    }

    private fun parseCount(s: String): Long? {
        s.toLongOrNull()?.let { return it }
        val digit = mapOf(
            '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
        return when {
            s == "十" -> 10L
            s.length == 1 -> digit[s[0]]?.toLong()
// 十几
            s.length == 2 && s[0] == '十' -> (10 + (digit[s[1]] ?: return null)).toLong()
// 几十
            s.length == 2 && s[1] == '十' -> ((digit[s[0]] ?: return null) * 10).toLong()
// 几十几
            s.length == 3 && s[1] == '十' ->
                ((digit[s[0]] ?: return null) * 10 + (digit[s[2]] ?: return null)).toLong()
            else -> null
        }
    }

    private fun parseDow(ch: String): DayOfWeek? = when (ch) {
        "一", "1" -> DayOfWeek.MONDAY
        "二", "2" -> DayOfWeek.TUESDAY
        "三", "3" -> DayOfWeek.WEDNESDAY
        "四", "4" -> DayOfWeek.THURSDAY
        "五", "5" -> DayOfWeek.FRIDAY
        "六", "6" -> DayOfWeek.SATURDAY
        "日", "天", "7" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun explicitRepeat(input: String): RepeatRule? = when {
        input.contains("每年") -> RepeatRule.YEARLY
        input.contains("每月") || input.contains("每个月") -> RepeatRule.MONTHLY
        input.contains("每周") || input.contains("每个星期") -> RepeatRule.WEEKLY
        input.contains("每天") || input.contains("每日") -> RepeatRule.DAILY
        else -> null
    }

    private fun alignAnchorToRule(
        date: LocalDate,
        rule: RepeatRule,
        today: LocalDate,
        day: Int,
        kind: DateKind,
    ): LocalDate = when (rule) {
        RepeatRule.NONE -> date
        RepeatRule.DAILY -> today
        RepeatRule.WEEKLY -> {
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val same = monday.plusDays((date.dayOfWeek.value - 1).toLong())
            if (same.isBefore(today)) same.plusWeeks(1) else same
        }
        RepeatRule.MONTHLY -> {
            val anchorMonth = if (kind == DateKind.ABSOLUTE) YearMonth.from(today) else YearMonth.from(date)
            CountdownMath.nextDate(monthHolding(anchorMonth.year, anchorMonth.monthValue, day), rule, today)
        }
        RepeatRule.YEARLY -> CountdownMath.nextDate(date, rule, today)
    }

    private fun monthHolding(year: Int, month: Int, day: Int): LocalDate {
        var ym = YearMonth.of(year, month)
        repeat(12) {
            if (ym.lengthOfMonth() >= day) return ym.atDay(day)
            ym = ym.plusMonths(1)
        }
        return CountdownMath.clampToMonth(year, month, day)
    }

    private fun stripPhrase(input: String, range: IntRange): String {
        val head = input.substring(0, range.first)
        val tail = input.substring(range.last + 1)
        return (head + " " + tail)
            .replace(Regex("""^[\s,，。.、:：\-—的在]+"""), "")
// 「10月25日之后 体检」剥掉日期后还剩一个「之后」，读起来像标题的一部分。
            .replace(Regex("""^(之后|以后|之前|以前)\s*"""), "")
            .replace(Regex("""[\s,，。.、:：\-—的]+$"""), "")
            .trim()
    }

    private fun buildExplanation(date: LocalDate, rule: RepeatRule, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        val whenText = when {
            days == 0L -> "今天"
            days > 0 -> "还有 $days 天"
            else -> "已过 ${-days} 天"
        }
        val ruleText = if (rule == RepeatRule.NONE) "" else " · ${rule.label}"
        return "$date（$whenText）$ruleText"
    }
}

