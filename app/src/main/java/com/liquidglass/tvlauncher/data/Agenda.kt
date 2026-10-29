package com.liquidglass.tvlauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** One calendar entry to show: when it starts, whether it lasts all day, and its title. */
class CalendarEvent(val startMillis: Long, val allDay: Boolean, val title: String)

enum class AgendaSource { NONE, ICAL, SYSTEM }

/**
 * The next few days of the user's calendar. [needsPermission] and [failed] explain an empty list:
 * the box's system calendar isn't readable, or the iCal link couldn't be fetched.
 */
class Agenda(
    val source: AgendaSource,
    val events: List<CalendarEvent>,
    val failed: Boolean = false,
    val needsPermission: Boolean = false,
) {
    companion object {
        val EMPTY = Agenda(AgendaSource.NONE, emptyList())
    }
}

/**
 * Where the calendar comes from. Android TV usually has no calendar of its own, so the reliable
 * way is the private iCal address every Google calendar has (Settings of that calendar >
 * "Secret address in iCal format"): the launcher downloads it and reads the next two weeks.
 * Without a link it falls back to the system calendar, if the box has one and the user allowed it.
 */
object AgendaRepository {
    private const val DAYS = 14
    private const val MAX_EVENTS = 12

    suspend fun load(context: Context, icalUrl: String?): Agenda = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val until = today + DAYS * 24L * 3_600_000L
        if (!icalUrl.isNullOrBlank()) {
            val bytes = Net.download(icalUrl.trim().replaceFirst("webcal://", "https://"), 12_000_000)
                ?: return@withContext Agenda(AgendaSource.ICAL, emptyList(), failed = true)
            try {
                val events = IcalParser.upcoming(bytes.toString(Charsets.UTF_8), today, until, System.currentTimeMillis())
                Agenda(AgendaSource.ICAL, events.take(MAX_EVENTS))
            } catch (_: Throwable) {
                Agenda(AgendaSource.ICAL, emptyList(), failed = true)
            }
        } else {
            system(context, today, until)
        }
    }

    private fun system(context: Context, from: Long, until: Long): Agenda {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return Agenda(AgendaSource.NONE, emptyList(), needsPermission = true)
        }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, until)
        }.build()
        val events = ArrayList<CalendarEvent>()
        try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY),
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC",
            ) ?: return Agenda(AgendaSource.NONE, emptyList(), failed = true)
            cursor.use {
                while (it.moveToNext() && events.size < MAX_EVENTS) {
                    events += CalendarEvent(it.getLong(0), it.getInt(2) == 1, it.getString(1).orEmpty().ifBlank { "—" })
                }
            }
        } catch (_: Exception) {
            return Agenda(AgendaSource.NONE, emptyList(), failed = true)
        }
        return Agenda(AgendaSource.SYSTEM, events)
    }
}
