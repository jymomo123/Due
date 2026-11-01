package com.offline.due.domain

import com.offline.due.data.CountdownEvent
import com.offline.due.data.EventMode
import com.offline.due.data.RepeatRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class CountdownInfo(
    val mode: EventMode,
    val target: LocalDate,
    val days: Long,
    val cycleStart: LocalDate,
    val today: LocalDate,

    val hasEnd: Boolean = false,

    val totalDays: Long = 0L,

    val includeStart: Boolean = false,
) {
    val isElapsed: Boolean get() = mode == EventMode.ELAPSED

    val isToday: Boolean get() = mode == EventMode.COUNTDOWN && days == 0L
    val isFuture: Boolean get() = mode == EventMode.COUNTDOWN && days > 0L
    val absDays: Long get() = if (days < 0L) -days else days

    val hasProgress: Boolean
        get() = when (mode) {
            EventMode.ELAPSED -> hasEnd && totalDays > 0L
            EventMode.COUNTDOWN -> true
        }

    val progress: Float
        get() {
            if (mode == EventMode.ELAPSED) {
                if (!hasEnd || totalDays <= 0L) return 0f
                return (days.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)
            }
            val total = ChronoUnit.DAYS.between(cycleStart, target)
            if (total <= 0L) return 1f
            val done = ChronoUnit.DAYS.between(cycleStart, today)
            return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

data class Precise(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val negative: Boolean,
) {
    fun text(): String {
        val sign = if (negative) "−" else ""
        return "$sign$days 天 %02d:%02d:%02d".format(hours, minutes, seconds)
    }

    fun clock(): String = "%02d:%02d:%02d".format(hours, minutes, seconds)
}

enum class CardNumberMode(val label: String) {
    DAY("日"),
    YMD("年月日"),
    MD("月日"),
    WEEK("周");

    fun next(): CardNumberMode = entries[(ordinal + 1) % entries.size]
}

data class NumberPart(val value: String, val unit: String)

data class CardNumber(
    val parts: List<NumberPart>,
    val lead: String = "",
)

object CountdownMath {

    fun bigNumber(info: CountdownInfo): String = when {
        info.mode == EventMode.ELAPSED ->
            if (info.absDays > 99999L) "99999+" else info.absDays.toString()
        info.absDays > 9999L -> "9999+"
        else -> info.absDays.toString()
    }

    fun signedNumber(info: CountdownInfo): String = when {
        info.mode == EventMode.ELAPSED ->
            if (info.absDays > 99999L) "99999+" else info.absDays.toString()
        info.isToday -> "0"
        info.isFuture -> if (info.absDays > 9999L) "9999+" else info.absDays.toString()
        else -> if (info.absDays > 9999L) "−9999" else "−${info.absDays}"
    }

    fun headline(info: CountdownInfo): String =
        if (info.mode == EventMode.ELAPSED) bigNumber(info) else signedNumber(info)

    fun prefix(info: CountdownInfo): String = when {
        info.mode == EventMode.ELAPSED -> "已走过"
        info.isToday -> "就是今天"
        info.isFuture -> "还有"
        else -> "已过"
    }

    fun suffix(info: CountdownInfo): String =
        if (info.mode == EventMode.COUNTDOWN && info.isToday) "" else "天"

    fun stateWord(info: CountdownInfo): String? = when {
        info.isToday -> null
        info.isFuture -> "还有"
        else -> "已经"
    }

    fun caption(info: CountdownInfo): String =
        if (info.mode == EventMode.COUNTDOWN && info.isToday) {
            "就是今天"
        } else {
            "${prefix(info)} ${bigNumber(info)} 天"
        }

    fun cardNumber(info: CountdownInfo, mode: CardNumberMode): CardNumber = when (mode) {
        CardNumberMode.DAY -> CardNumber(listOf(NumberPart(signedNumber(info), "天")))

        CardNumberMode.WEEK -> CardNumber(listOf(NumberPart((info.days / 7L).toString(), "周")))

        CardNumberMode.MD -> span(info, withYears = false)

        CardNumberMode.YMD -> span(info, withYears = true)
    }

    private fun span(info: CountdownInfo, withYears: Boolean): CardNumber {
        val from: LocalDate
        val to: LocalDate
        val lead: String
        when {
            info.isElapsed -> {
                from = info.cycleStart; to = info.today; lead = "已经"
            }
            info.days > 0L -> {
                from = info.today; to = info.target; lead = "还剩"
            }
            info.days < 0L -> {
                from = info.target; to = info.today; lead = "已过"
            }
            else -> {
                from = info.today; to = info.today; lead = ""
            }
        }
        return CardNumber(spanParts(from, to, withYears, info.includeStart), lead)
    }

    private fun spanParts(
        from: LocalDate,
        to: LocalDate,
        withYears: Boolean,
        extraDay: Boolean,
    ): List<NumberPart> {
        val end = if (extraDay) to.plusDays(1) else to
        val p = Period.between(from, end)
        val months = if (withYears) p.months else p.years * 12 + p.months

        val parts = mutableListOf<NumberPart>()
        if (withYears && p.years > 0) parts += NumberPart(p.years.toString(), "年")
        if (months > 0) parts += NumberPart(months.toString(), "月")
        if (p.days > 0) parts += NumberPart(p.days.toString(), "日")

// 全零（同一天）时给一个"0 日"，不然这一行会是空的
        return parts.ifEmpty { listOf(NumberPart("0", "日")) }
    }

    fun occurrence(event: CountdownEvent, today: LocalDate = LocalDate.now()): CountdownInfo =
        when (event.mode) {            EventMode.ELAPSED -> elapsedInfo(event, today)
            EventMode.COUNTDOWN -> countdownInfo(event, today)
        }

    private fun shouldCountUp(event: CountdownEvent, today: LocalDate): Boolean =
        event.mode == EventMode.COUNTDOWN &&
            event.repeat == RepeatRule.NONE &&
            event.anchor.isBefore(today)

    private fun countdownInfo(event: CountdownEvent, today: LocalDate): CountdownInfo {
        val target = nextDate(event.anchor, event.repeat, today)
        val start = previousDate(event.anchor, event.repeat, target)
        val raw = ChronoUnit.DAYS.between(today, target)
        val days = if (event.includeStartDay && raw < 0L) raw - 1L else raw

        if (shouldCountUp(event, today)) {
            return CountdownInfo(
                mode = EventMode.ELAPSED,
// 计时模式没有终点，`target` 退化成今天（见 CountdownInfo 的注释）。
                target = today,
                days = -days,
                cycleStart = target,
                today = today,
                hasEnd = false,
                totalDays = 0L,
                includeStart = event.includeStartDay,
            )
        }

        return CountdownInfo(
            mode = EventMode.COUNTDOWN,
            target = target,
            days = days,
            cycleStart = start,
            today = today,
            includeStart = event.includeStartDay && raw < 0L,
        )
    }

    private fun elapsedInfo(event: CountdownEvent, today: LocalDate): CountdownInfo {
        val end = event.endDate
        val bonus = if (event.includeStartDay) 1L else 0L
        val elapsed = ChronoUnit.DAYS.between(event.anchor, today) + bonus
        val total = if (end != null) {
            (ChronoUnit.DAYS.between(event.anchor, end) + bonus).coerceAtLeast(0L)
        } else {
            0L
        }
        return CountdownInfo(
            mode = EventMode.ELAPSED,
            target = end ?: today,
            days = elapsed.coerceAtLeast(0L),
            cycleStart = event.anchor,
            today = today,
            hasEnd = end != null,
            totalDays = total,
            includeStart = event.includeStartDay,
        )
    }

    fun precise(event: CountdownEvent, now: LocalDateTime): Precise {
        val from: LocalDateTime
        val to: LocalDateTime
        val bonus = if (event.includeStartDay) 1L else 0L
        val today = now.toLocalDate()
        if (event.mode == EventMode.ELAPSED || shouldCountUp(event, today)) {
// 计时永远是"已经走过"，所以 bonus 总是生效
            from = event.anchor.minusDays(bonus).atStartOfDay()
            to = now
        } else {
            val target = nextDate(event.anchor, event.repeat, today)
            from = now
            to = if (target.isBefore(today)) {
// 已过期：往回多算一天，和大数字的 −1 口径对齐
                target.minusDays(bonus).atStartOfDay()
            } else {
                target.plusDays(1).atStartOfDay()
            }
        }
        var secs = ChronoUnit.SECONDS.between(from, to)
        val negative = secs < 0L
        if (negative) secs = -secs
        return Precise(
            days = secs / 86400L,
            hours = (secs % 86400L) / 3600L,
            minutes = (secs % 3600L) / 60L,
            seconds = secs % 60L,
            negative = negative,
        )
    }

    fun nextDate(anchor: LocalDate, rule: RepeatRule, today: LocalDate): LocalDate {
        if (rule == RepeatRule.NONE) return anchor
        if (!anchor.isBefore(today)) return anchor

        return when (rule) {
            RepeatRule.DAILY -> today
            RepeatRule.WEEKLY -> nextWeekly(anchor, today)
            RepeatRule.MONTHLY -> nextMonthly(anchor, today)
            RepeatRule.YEARLY -> nextYearly(anchor, today)
            RepeatRule.NONE -> anchor
        }
    }

    private fun nextWeekly(anchor: LocalDate, today: LocalDate): LocalDate {
        val diff = ChronoUnit.DAYS.between(anchor, today)
        val steps = diff / 7 + if (diff % 7 == 0L) 0L else 1L
        return anchor.plusDays(steps * 7)
    }

    private fun nextMonthly(anchor: LocalDate, today: LocalDate): LocalDate {
        var ym = YearMonth.from(today)
        var guard = 0
        while (guard < 36) {
            val candidate = clampToMonth(ym.year, ym.monthValue, anchor.dayOfMonth)
            if (!candidate.isBefore(today)) return candidate
            ym = ym.plusMonths(1)
            guard++
        }
        return clampToMonth(ym.year, ym.monthValue, anchor.dayOfMonth)
    }

    private fun nextYearly(anchor: LocalDate, today: LocalDate): LocalDate {
        var year = today.year
        var guard = 0
        while (guard < 12) {
            val candidate = clampToMonth(year, anchor.monthValue, anchor.dayOfMonth)
            if (!candidate.isBefore(today)) return candidate
            year++
            guard++
        }
        return clampToMonth(year, anchor.monthValue, anchor.dayOfMonth)
    }

    fun previousDate(anchor: LocalDate, rule: RepeatRule, target: LocalDate): LocalDate =
        when (rule) {
            RepeatRule.NONE -> anchor
            RepeatRule.DAILY -> target.minusDays(1)
            RepeatRule.WEEKLY -> target.minusWeeks(1)
            RepeatRule.MONTHLY -> {
                val ym = YearMonth.from(target).minusMonths(1)
                clampToMonth(ym.year, ym.monthValue, anchor.dayOfMonth)
            }
            RepeatRule.YEARLY -> clampToMonth(target.year - 1, anchor.monthValue, anchor.dayOfMonth)
        }

    fun clampToMonth(year: Int, month: Int, day: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        return ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
    }

    fun yearProgress(today: LocalDate = LocalDate.now()): Float {
        val start = LocalDate.of(today.year, 1, 1)
        val end = LocalDate.of(today.year + 1, 1, 1)
        val total = ChronoUnit.DAYS.between(start, end).toFloat()
        val done = ChronoUnit.DAYS.between(start, today).toFloat()
        return (done / total).coerceIn(0f, 1f)
    }

    fun daysLeftInYear(today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(today, LocalDate.of(today.year + 1, 1, 1))

    fun displayList(
        list: List<CountdownEvent>,
        today: LocalDate = LocalDate.now(),
    ): List<Pair<CountdownEvent, CountdownInfo>> =
        list.map { it to occurrence(it, today) }
            .sortedWith(
                compareByDescending<Pair<CountdownEvent, CountdownInfo>> { it.first.pinned }
                    .thenBy { if (it.second.days >= 0L) 0 else 1 }
                    .thenBy { if (it.second.days >= 0L) it.second.days else -it.second.days }
                    .thenBy { it.first.title }
            )

    fun nearest(
        list: List<CountdownEvent>,
        today: LocalDate = LocalDate.now(),
    ): Pair<CountdownEvent, CountdownInfo>? = displayList(list, today).firstOrNull()

    fun sortForDisplay(
        list: List<CountdownEvent>,
        today: LocalDate = LocalDate.now(),
    ): List<CountdownEvent> = displayList(list, today).map { it.first }

    fun occursOn(event: CountdownEvent, date: LocalDate): Boolean {
        if (event.mode == EventMode.ELAPSED) {
// 计时事件只在自己的起点那天打一个点；有终点时终点也打一个。
            return date == event.anchor || date == event.endDate
        }
        if (date.isBefore(event.anchor)) return false
        return when (event.repeat) {
            RepeatRule.NONE -> event.anchor == date
            RepeatRule.DAILY -> true
            RepeatRule.WEEKLY -> ChronoUnit.DAYS.between(event.anchor, date) % 7 == 0L
            RepeatRule.MONTHLY -> date.dayOfMonth ==
                clampToMonth(date.year, date.monthValue, event.anchor.dayOfMonth).dayOfMonth
            RepeatRule.YEARLY -> date.monthValue == event.anchor.monthValue &&
                date.dayOfMonth ==
                clampToMonth(date.year, date.monthValue, event.anchor.dayOfMonth).dayOfMonth
        }
    }

    fun eventsOn(events: List<CountdownEvent>, date: LocalDate): List<CountdownEvent> =
        events.filter { occursOn(it, date) }
}

