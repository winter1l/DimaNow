package com.example.dimanow.school

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.Course
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The intranet's 시간표 조회 page (D-107). */
const val INTRANET_TIMETABLE_URL = "https://intranet.dima.ac.kr/su/timeTable_Stnt.aspx"

/**
 * Reads the period × weekday table: the header row names the days, each later row starts with `N교시` and
 * holds one cell per day whose lines are course, room and professor. Nothing else on the page is read.
 */
const val INTRANET_TIMETABLE_SCRIPT = """
    var table=Array.from(document.querySelectorAll('table')).find(function(t){
      var head=t.rows.length?t.rows[0].innerText:'';
      return /월/.test(head)&&/금/.test(head)&&/교시/.test(t.innerText);
    });
    if(!table){
      var text=((document.body&&document.body.innerText)||'').slice(0,400);
      if(/WrongAccess/i.test(location.pathname)||/세션|로그인/.test(text))return {state:'signin'};
      return {state:'error',message:'인트라넷에서 시간표 표를 찾지 못했어요.'};
    }
    var rows=Array.from(table.rows);
    var days=Array.from(rows[0].cells).slice(1).map(function(c){return c.innerText.trim();});
    var periods=rows.slice(1).map(function(r){
      var cells=Array.from(r.cells);
      return {
        period:((cells[0]&&cells[0].innerText)||'').trim(),
        cells:cells.slice(1).map(function(c){
          return c.innerText.split('\n').map(function(s){return s.trim();}).filter(Boolean);
        })
      };
    });
    return {state:'ok',data:{days:days,periods:periods}};
"""

/** One course block read from the intranet, consecutive periods already merged. */
data class ImportedCourse(
    val weekday: DayOfWeek,
    val firstPeriod: Int,
    val lastPeriod: Int,
    val name: String,
    val room: String,
    val professor: String,
) {
    /** D-107: period n runs from n+8:00 for 60 minutes (1교시 09:00–10:00), periods back to back. */
    val start: LocalTime get() = LocalTime.of((8 + firstPeriod).coerceAtMost(23), 0)
    val end: LocalTime get() = if (9 + lastPeriod >= 24) LocalTime.of(23, 59) else LocalTime.of(9 + lastPeriod, 0)

    /** The intranet does not say which shuttle side a room is on; rooms named for 엔터관 start there, others at 본관. */
    val suggestedZone: CampusZoneId get() = if ("엔터" in room) CampusZoneId.YEIN else CampusZoneId.MAIN

    fun toCourse(zone: CampusZoneId = suggestedZone) = Course(
        weekday = weekday,
        start = start,
        end = end,
        name = name,
        room = room,
        professor = professor,
        zone = zone,
    )
}

private val WEEKDAYS = mapOf(
    "월" to DayOfWeek.MONDAY, "화" to DayOfWeek.TUESDAY, "수" to DayOfWeek.WEDNESDAY,
    "목" to DayOfWeek.THURSDAY, "금" to DayOfWeek.FRIDAY, "토" to DayOfWeek.SATURDAY, "일" to DayOfWeek.SUNDAY,
)

/** Turns the page script's `data` into course blocks, ordered by weekday then start. */
fun parseIntranetTimetable(json: String): List<ImportedCourse> {
    val root = Json.parseToJsonElement(json).jsonObject
    val days = root["days"]?.jsonArray.orEmpty().map { day -> WEEKDAYS[day.jsonPrimitive.content.trim().take(1)] }
    // Per weekday, the course in each period (null where the cell is empty).
    val slots = mutableMapOf<DayOfWeek, MutableMap<Int, Triple<String, String, String>>>()
    root["periods"]?.jsonArray.orEmpty().forEach { element ->
        val row = element as? JsonObject ?: return@forEach
        val period = row["period"]?.jsonPrimitive?.content?.let { PERIOD.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: return@forEach
        (row["cells"] as? JsonArray).orEmpty().forEachIndexed { index, cell ->
            val day = days.getOrNull(index) ?: return@forEachIndexed
            val lines = (cell as? JsonArray).orEmpty().map { it.jsonPrimitive.content.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return@forEachIndexed
            slots.getOrPut(day) { sortedMapOf() }[period] = Triple(lines[0], lines.getOrElse(1) { "" }, lines.getOrElse(2) { "" })
        }
    }
    val courses = mutableListOf<ImportedCourse>()
    slots.toSortedMap().forEach { (day, byPeriod) ->
        var current: ImportedCourse? = null
        byPeriod.toSortedMap().forEach { (period, value) ->
            val (name, room, professor) = value
            val open = current
            current = if (open != null && open.lastPeriod == period - 1 &&
                open.name == name && open.room == room && open.professor == professor
            ) {
                open.copy(lastPeriod = period)
            } else {
                open?.let(courses::add)
                ImportedCourse(day, period, period, name, room, professor)
            }
        }
        current?.let(courses::add)
    }
    return courses
}

private val PERIOD = Regex("(\\d{1,2})\\s*교시")
