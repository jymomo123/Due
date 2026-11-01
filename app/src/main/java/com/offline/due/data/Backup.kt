package com.offline.due.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Backup {

    private val DT_START: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DT_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun exportJson(events: List<CountdownEvent>): String = EventCodec.encode(events)

    fun exportIcs(events: List<CountdownEvent>): String {
        val stamp = DT_STAMP.format(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//CountdownOffline//Countdown Calendar//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.append("X-WR-CALNAME:如期\r\n")

        events.forEach { e ->
            sb.append("BEGIN:VEVENT\r\n")
            sb.append("UID:").append(e.id).append("@due.local\r\n")
            sb.append("DTSTAMP:").append(stamp).append("\r\n")
            sb.append("DTSTART;VALUE=DATE:").append(e.anchor.format(DT_START)).append("\r\n")
            sb.append(fold("SUMMARY:" + escape(e.title))).append("\r\n")
            if (e.note.isNotBlank()) {
                sb.append(fold("DESCRIPTION:" + escape(e.note))).append("\r\n")
            }
            rruleOf(e)?.let { sb.append("RRULE:").append(it).append("\r\n") }
            sb.append("CATEGORIES:").append(escape(e.category)).append("\r\n")
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    private fun rruleOf(e: CountdownEvent): String? = when (e.repeat) {
        RepeatRule.NONE -> null
        RepeatRule.DAILY -> "FREQ=DAILY"
        RepeatRule.WEEKLY -> "FREQ=WEEKLY"
        RepeatRule.MONTHLY -> "FREQ=MONTHLY"
        RepeatRule.YEARLY -> "FREQ=YEARLY"
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    private fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val out = StringBuilder()
        var used = 0
        var started = false
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val piece = String(Character.toChars(cp))
            val len = piece.toByteArray(Charsets.UTF_8).size
            if (started && used + len > 74) {
                out.append("\r\n ")
                used = 1
            }
            out.append(piece)
            used += len
            started = true
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    fun suggestedFileName(prefix: String, today: LocalDate = LocalDate.now()): String =
        prefix + "_" + today.format(DT_START)
}

