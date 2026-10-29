package com.liquidglass.tvlauncher.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Reads the next events out of an iCalendar (.ics) file, which is what a Google calendar's secret
 * iCal address serves. Understood: all-day and timed events, time zones, titles, cancelled events,
 * changed single occurrences, EXDATE, and repeating events (RRULE) that repeat daily, weekly (with
 * BYDAY), monthly (same day, BYMONTHDAY, or "the 2nd Tuesday") or yearly, with INTERVAL, COUNT and
 * UNTIL. Anything fancier is ignored instead of guessed at.
 */
object IcalParser {

    private class Property(val name: String, val params: Map<String, String>, val value: String)

    private class Start(val millis: Long, val allDay: Boolean, val zone: TimeZone)

    private const val DAY = 24L * 3_600_000L

    /** Events starting (or still running) between [from] and [until], soonest first, not yet over at [now]. */
    fun upcoming(text: String, from: Long, until: Long, now: Long): List<CalendarEvent> {
        val blocks = ArrayList<Map<String, List<Property>>>()
        var current: HashMap<String, MutableList<Property>>? = null
        for (line in unfold(text)) {
            if (line == "BEGIN:VEVENT") {
                current = HashMap()
            } else if (line == "END:VEVENT") {
                current?.let { blocks.add(it) }
                current = null
            } else {
                val block = current ?: continue
                val property = parseLine(line) ?: continue
                block.getOrPut(property.name) { ArrayList() }.add(property)
            }
        }

        // A changed single occurrence of a repeating event is its own VEVENT with RECURRENCE-ID; the
        // regular occurrence it replaces must not show up as well.
        val replaced = HashSet<String>()
        for (block in blocks) {
            val uid = block["UID"]?.firstOrNull()?.value ?: continue
            val original = block["RECURRENCE-ID"]?.firstOrNull()?.let { parseTime(it) } ?: continue
            replaced += "$uid#${original.millis}"
        }

        val events = ArrayList<CalendarEvent>()
        for (block in blocks) {
            if (block["STATUS"]?.firstOrNull()?.value?.trim().equals("CANCELLED", ignoreCase = true)) continue
            val start = block["DTSTART"]?.firstOrNull()?.let { parseTime(it) } ?: continue
            val end = block["DTEND"]?.firstOrNull()?.let { parseTime(it) }
            val duration = if (end != null) (end.millis - start.millis).coerceAtLeast(0) else if (start.allDay) DAY else 0L
            val title = unescape(block["SUMMARY"]?.firstOrNull()?.value ?: "").ifBlank { "—" }
            val uid = block["UID"]?.firstOrNull()?.value

            val rule = block["RRULE"]?.firstOrNull()?.value
            val starts: List<Long> = if (rule == null || block["RECURRENCE-ID"] != null) {
                listOf(start.millis)
            } else {
                val excluded = HashSet<Long>()
                block["EXDATE"]?.forEach { property ->
                    property.value.split(',').forEach { part ->
                        parseTime(Property(property.name, property.params, part))?.let { excluded += it.millis }
                    }
                }
                occurrences(start, parseRule(rule), from - duration, until, excluded)
            }
            for (millis in starts) {
                if (uid != null && "$uid#$millis" in replaced) continue
                if (millis >= until) continue
                // All-day events count for the whole day; timed ones until they end.
                val over = if (start.allDay) millis + duration <= from else millis + duration < now
                if (over || millis + duration < from) continue
                events += CalendarEvent(millis, start.allDay, title)
            }
        }
        return events.sortedBy { it.startMillis }
    }

    // ---- lines and properties ----------------------------------------------------------------

    /** Long lines are folded: a line starting with a space or tab continues the one before it. */
    private fun unfold(text: String): List<String> {
        val lines = ArrayList<String>()
        for (raw in text.split("\n")) {
            val line = raw.trimEnd('\r')
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines.last() + line.substring(1)
            } else {
                lines += line
            }
        }
        return lines
    }

    /** `NAME;PARAM=VALUE:value`; the first colon outside quotes ends the name and parameters. */
    private fun parseLine(line: String): Property? {
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) {
                colon = i
                break
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = HashMap<String, String>()
        for (part in head.drop(1)) {
            val eq = part.indexOf('=')
            if (eq > 0) params[part.substring(0, eq).uppercase()] = part.substring(eq + 1).trim('"')
        }
        return Property(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun unescape(text: String): String =
        text.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun parseTime(property: Property): Start? {
        val v = property.value.trim()
        return try {
            val allDay = property.params["VALUE"] == "DATE" || v.length == 8
            val year = v.substring(0, 4).toInt()
            val month = v.substring(4, 6).toInt() - 1
            val day = v.substring(6, 8).toInt()
            if (allDay) {
                val zone = TimeZone.getDefault()
                val cal = Calendar.getInstance(zone).apply {
                    clear()
                    set(year, month, day, 0, 0, 0)
                }
                Start(cal.timeInMillis, true, zone)
            } else {
                val zone = when {
                    v.endsWith("Z") -> TimeZone.getTimeZone("UTC")
                    property.params["TZID"] != null -> TimeZone.getTimeZone(property.params["TZID"])
                    else -> TimeZone.getDefault()
                }
                val cal = Calendar.getInstance(zone).apply {
                    clear()
                    set(year, month, day, v.substring(9, 11).toInt(), v.substring(11, 13).toInt(), v.substring(13, 15).toInt())
                }
                Start(cal.timeInMillis, false, zone)
            }
        } catch (_: Exception) {
            null
        }
    }

    // ---- repeating events --------------------------------------------------------------------

    private fun parseRule(rule: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (part in rule.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0) map[part.substring(0, eq).uppercase()] = part.substring(eq + 1)
        }
        return map
    }

    private val WEEKDAYS = mapOf(
        "MO" to Calendar.MONDAY, "TU" to Calendar.TUESDAY, "WE" to Calendar.WEDNESDAY, "TH" to Calendar.THURSDAY,
        "FR" to Calendar.FRIDAY, "SA" to Calendar.SATURDAY, "SU" to Calendar.SUNDAY,
    )

    /** "MO" is (0, Monday); "2TU" is (2, Tuesday); "-1FR" is (-1, Friday). */
    private fun parseByDay(text: String): Pair<Int, Int>? {
        val code = text.takeLast(2).uppercase()
        val weekday = WEEKDAYS[code] ?: return null
        val ordinal = text.dropLast(2).toIntOrNull() ?: 0
        return ordinal to weekday
    }

    /** Monday = 0 ... Sunday = 6. */
    private fun mondayBased(dayOfWeek: Int): Int = (dayOfWeek + 5) % 7

    private fun nthWeekday(zone: TimeZone, year: Int, month: Int, weekday: Int, n: Int, timeOf: Calendar): Long? {
        val cal = Calendar.getInstance(zone).apply {
            clear()
            set(year, month, 1, timeOf.get(Calendar.HOUR_OF_DAY), timeOf.get(Calendar.MINUTE), timeOf.get(Calendar.SECOND))
        }
        if (n > 0) {
            val shift = (weekday - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
            cal.add(Calendar.DAY_OF_MONTH, shift + (n - 1) * 7)
        } else {
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val back = (cal.get(Calendar.DAY_OF_WEEK) - weekday + 7) % 7
            cal.add(Calendar.DAY_OF_MONTH, -back - (-n - 1) * 7)
        }
        return if (cal.get(Calendar.MONTH) == month) cal.timeInMillis else null
    }

    /**
     * The start times of a repeating event that fall before [until], from [from] on, in ascending
     * order. Counting (COUNT) always starts from the first occurrence, so events with a COUNT are
     * walked from the beginning; the others skip straight to the window.
     */
    private fun occurrences(start: Start, rule: Map<String, String>, from: Long, until: Long, excluded: Set<Long>): List<Long> {
        val freq = rule["FREQ"] ?: return listOf(start.millis)
        val interval = rule["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = rule["COUNT"]?.toIntOrNull()
        val limit = rule["UNTIL"]?.let { parseTime(Property("UNTIL", emptyMap(), it)) }?.millis
        val byDay = rule["BYDAY"]?.split(',')?.mapNotNull { parseByDay(it.trim()) }.orEmpty()
        val byMonthDay = rule["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

        val result = ArrayList<Long>()
        var produced = 0

        /** Takes one occurrence; false means "no more are wanted". */
        fun take(millis: Long): Boolean {
            if (millis < start.millis) return true
            if (limit != null && millis > limit) return false
            if (millis >= until) return false
            produced++
            if (count != null && produced > count) return false
            if (millis >= from && millis !in excluded) result.add(millis)
            return true
        }

        val first = Calendar.getInstance(start.zone).apply { timeInMillis = start.millis }
        val skipAhead = count == null

        when (freq) {
            "DAILY" -> {
                val cal = first.clone() as Calendar
                if (skipAhead && from > start.millis) {
                    val steps = ((from - start.millis) / DAY - 2).coerceAtLeast(0) / interval
                    cal.add(Calendar.DAY_OF_MONTH, (steps * interval).toInt())
                }
                var guard = 0
                while (guard++ < 5000 && take(cal.timeInMillis)) cal.add(Calendar.DAY_OF_MONTH, interval)
            }

            "WEEKLY" -> {
                val days = (if (byDay.isEmpty()) listOf(first.get(Calendar.DAY_OF_WEEK)) else byDay.map { it.second })
                    .distinct().sortedBy { mondayBased(it) }
                val week = first.clone() as Calendar // the Monday of the first week, at the event's time of day
                week.add(Calendar.DAY_OF_MONTH, -mondayBased(first.get(Calendar.DAY_OF_WEEK)))
                if (skipAhead && from > start.millis) {
                    val steps = ((from - start.millis) / (7 * DAY) - 2).coerceAtLeast(0) / interval
                    week.add(Calendar.DAY_OF_MONTH, (steps * interval * 7).toInt())
                }
                var guard = 0
                var more = true
                while (more && guard++ < 2000) {
                    for (weekday in days) {
                        val day = week.clone() as Calendar
                        day.add(Calendar.DAY_OF_MONTH, mondayBased(weekday))
                        if (!take(day.timeInMillis)) {
                            more = false
                            break
                        }
                    }
                    week.add(Calendar.DAY_OF_MONTH, 7 * interval)
                }
            }

            "MONTHLY" -> {
                var monthIndex = 0
                if (skipAhead && from > start.millis) {
                    monthIndex = (((from - start.millis) / (28 * DAY)).toInt() - 2).coerceAtLeast(0) / interval * interval
                }
                var guard = 0
                var more = true
                while (more && guard++ < 1500) {
                    val month = first.clone() as Calendar
                    month.set(Calendar.DAY_OF_MONTH, 1)
                    month.add(Calendar.MONTH, monthIndex)
                    val year = month.get(Calendar.YEAR)
                    val monthOfYear = month.get(Calendar.MONTH)
                    val candidates = ArrayList<Long>()
                    when {
                        byDay.isNotEmpty() && byDay.all { it.first != 0 } ->
                            byDay.forEach { (n, weekday) -> nthWeekday(start.zone, year, monthOfYear, weekday, n, first)?.let { candidates += it } }
                        byMonthDay.isNotEmpty() -> byMonthDay.forEach { d ->
                            val cal = month.clone() as Calendar
                            val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                            val dayOfMonth = if (d > 0) d else last + 1 + d
                            if (dayOfMonth in 1..last) {
                                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                candidates += cal.timeInMillis
                            }
                        }
                        else -> {
                            val cal = month.clone() as Calendar
                            val wanted = first.get(Calendar.DAY_OF_MONTH)
                            if (wanted <= cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                                cal.set(Calendar.DAY_OF_MONTH, wanted)
                                candidates += cal.timeInMillis
                            }
                        }
                    }
                    for (millis in candidates.sorted()) {
                        if (!take(millis)) {
                            more = false
                            break
                        }
                    }
                    monthIndex += interval
                    if (month.timeInMillis > until + 40 * DAY) break
                }
            }

            "YEARLY" -> {
                var year = 0
                var guard = 0
                while (guard++ < 200) {
                    val cal = first.clone() as Calendar
                    cal.add(Calendar.YEAR, year)
                    // 29 February in a year without one rolls over to March: skip such years.
                    val valid = cal.get(Calendar.MONTH) == first.get(Calendar.MONTH)
                    if (valid && !take(cal.timeInMillis)) break
                    if (cal.timeInMillis > until) break
                    year += interval
                }
            }

            else -> return listOf(start.millis)
        }
        return result
    }
}
