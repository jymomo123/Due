package com.offline.due.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class AlmanacMark(
    val name: String,
    val date: LocalDate,

    val solarTerm: Boolean,
)

object Almanac {

    private val CST: ZoneOffset = ZoneOffset.ofHours(8)

    private val TERM_NAMES = listOf(
        "春分", "清明", "谷雨", "立夏", "小满", "芒种",
        "夏至", "小暑", "大暑", "立秋", "处暑", "白露",
        "秋分", "寒露", "霜降", "立冬", "小雪", "大雪",
        "冬至", "小寒", "大寒", "立春", "雨水", "惊蛰",
    )

    private val FIXED_FESTIVALS = listOf(
        Triple(1, 1, "元旦"),
        Triple(2, 14, "情人节"),
        Triple(3, 8, "妇女节"),
        Triple(3, 12, "植树节"),
        Triple(4, 1, "愚人节"),
        Triple(5, 1, "劳动节"),
        Triple(5, 4, "青年节"),
        Triple(6, 1, "儿童节"),
        Triple(7, 1, "建党节"),
        Triple(8, 1, "建军节"),
        Triple(9, 10, "教师节"),
        Triple(10, 1, "国庆节"),
        Triple(12, 24, "平安夜"),
        Triple(12, 25, "圣诞节"),
    )

    private data class NthWeekday(val month: Int, val n: Int, val day: DayOfWeek, val name: String)

    private val NTH_FESTIVALS = listOf(
        NthWeekday(5, 2, DayOfWeek.SUNDAY, "母亲节"),
        NthWeekday(6, 3, DayOfWeek.SUNDAY, "父亲节"),
        NthWeekday(11, 4, DayOfWeek.THURSDAY, "感恩节"),
    )

// ── 太阳视黄经 ────────────────────────────────────────────────

    private fun apparentSolarLongitude(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) * PI / 180.0
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)
        val omega = (125.04 - 1934.136 * t) * PI / 180.0
// 减去光行差与章动，得到视黄经
        return ((l0 + c - 0.00569 - 0.00478 * sin(omega)) % 360.0 + 360.0) % 360.0
    }

    private fun solveTerm(year: Int, targetLon: Double): Double {
// 锚点：2000 年春分 ≈ 3 月 20 日 07:12 UTC
        val equinox = 2451623.8 + (year - 2000) * 365.2422
// 黄经 > 270° 的五个节气（小寒 285 … 惊蛰 345）在春分之前，偏移取负。
        val deg = if (targetLon > 270.0) targetLon - 360.0 else targetLon
        var jd = equinox + deg / 360.0 * 365.2422
        repeat(12) {
            val lon = apparentSolarLongitude(jd)
            val diff = (lon - targetLon + 180.0) % 360.0 - 180.0
            if (abs(diff) < 1e-7) return jd
            jd -= diff / 0.9856473
        }
        return jd
    }

    private fun jdToDate(jd: Double): LocalDate {
        val millis = ((jd - 2440587.5) * 86400000.0).toLong()
        return Instant.ofEpochMilli(millis).atOffset(CST).toLocalDate()
    }

// ── 对外 ─────────────────────────────────────────────────────

    fun solarTerms(year: Int): List<AlmanacMark> = TERM_NAMES.mapIndexed { i, name ->
        AlmanacMark(
            name = name,
            date = jdToDate(solveTerm(year, ((i * 15) % 360).toDouble())),
            solarTerm = true,
        )
    }

    fun festivals(year: Int): List<AlmanacMark> {
        val fixed = FIXED_FESTIVALS.map { (month, day, name) ->
            AlmanacMark(name, LocalDate.of(year, month, day), false)
        }
        val nth = NTH_FESTIVALS.mapNotNull { f ->
            nthWeekday(year, f.month, f.day, f.n)?.let { AlmanacMark(f.name, it, false) }
        }
        return fixed + nth
    }

    fun marksOf(year: Int): List<AlmanacMark> =
        cache.getOrPut(year) { (solarTerms(year) + festivals(year)).sortedBy { it.date } }

    private val cache = java.util.concurrent.ConcurrentHashMap<Int, List<AlmanacMark>>()

    fun todayInfo(today: LocalDate = LocalDate.now()): AlmanacToday {
// 同时看今年和明年：12 月 31 日之后元旦属于下一年
        val all = marksOf(today.year) + marksOf(today.year + 1)
        val next = all.filter { it.date.isAfter(today) }.minByOrNull { it.date }
        return AlmanacToday(
            mark = all.firstOrNull { it.date == today }?.name,
            next = next,
            daysToNext = next?.let { ChronoUnit.DAYS.between(today, it.date) } ?: 0L,
        )
    }

    private fun nthWeekday(year: Int, month: Int, day: DayOfWeek, n: Int): LocalDate? {
        val first = LocalDate.of(year, month, 1)
        val shift = (day.value - first.dayOfWeek.value + 7) % 7
        val date = first.plusDays(shift.toLong() + (n - 1) * 7L)
        return if (date.monthValue == month) date else null
    }
}

data class AlmanacToday(

    val mark: String?,

    val next: AlmanacMark?,

    val daysToNext: Long,
)

