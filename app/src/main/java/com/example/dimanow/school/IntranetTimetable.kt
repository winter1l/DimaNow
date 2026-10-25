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
 * Row and column spans are laid out into a full grid first, a spanning cell's text copied into every slot it covers,
 * so a `rowspan` course reads as the same course in each of its periods and later days keep their columns (Q-032).
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
    var grid=rows.map(function(){return [];});
    rows.forEach(function(r,y){
      var x=0;
      Array.from(r.cells).forEach(function(c){
        while(grid[y][x]!==undefined)x++;
        var h=Math.min(c.rowSpan>0?c.rowSpan:rows.length-y,rows.length-y),w=Math.max(1,c.colSpan||1);
        for(var dy=0;dy<h;dy++)for(var dx=0;dx<w;dx++)grid[y+dy][x+dx]=c.innerText;
        x+=w;
      });
    });
    var texts=function(row){var out=[];for(var i=0;i<row.length;i++)out.push(row[i]||'');return out;};
    var days=texts(grid[0]).slice(1).map(function(s){return s.trim();});
    var periods=grid.slice(1).map(function(row){
      var cells=texts(row);
      return {
        period:(cells[0]||'').trim(),
        cells:cells.slice(1).map(function(s){
          return s.split('\n').map(function(s){return s.trim();}).filter(Boolean);
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

    /** The shuttle side of the room's building, or null when the building is not one the app knows. */
    val roomZone: CampusZoneId? get() = campusZoneForRoom(room)

    fun toCourse(zone: CampusZoneId = roomZone ?: CampusZoneId.MAIN) = Course(
        weekday = weekday,
        start = start,
        end = end,
        name = name,
        room = room,
        professor = professor,
        zone = zone,
    )
}

/**
 * Which shuttle side a classroom is on, from the building in its name (the intranet writes a room as the building
 * name followed by its number, such as `덕성관101` or `기예관201-1`; observed 2026-10-23). Buildings by the main gate — 대학본부(본관), 지성관 (학생회관),
 * 기예관, 덕성관, 체육관, 도서관 — are 본관; those over the hill — 엔터테인먼트관, 디자인센터, DIMA 종합촬영소,
 * 예인관 — are 엔터관 (source: namu.wiki 동아방송예술대학교/캠퍼스, read 2026-10-23, at the user's request).
 */
fun campusZoneForRoom(room: String): CampusZoneId? {
    val name = room.filterNot(Char::isWhitespace)
    val main = MAIN_SIDE_BUILDINGS.any { it in name }
    val yein = YEIN_SIDE_BUILDINGS.any { it in name }
    return when {
        main == yein -> null
        main -> CampusZoneId.MAIN
        else -> CampusZoneId.YEIN
    }
}

private val MAIN_SIDE_BUILDINGS = listOf("대학본부", "본관", "지성관", "학생회관", "기예관", "덕성관", "체육관", "도서관")
private val YEIN_SIDE_BUILDINGS = listOf("엔터", "디자인센터", "촬영소", "예인관", "기숙사")

private val WEEKDAYS = mapOf(
    "월" to DayOfWeek.MONDAY, "화" to DayOfWeek.TUESDAY, "수" to DayOfWeek.WEDNESDAY,
    "목" to DayOfWeek.THURSDAY, "금" to DayOfWeek.FRIDAY, "토" to DayOfWeek.SATURDAY, "일" to DayOfWeek.SUNDAY,
)

/**
 * Turns the page script's `data` into course blocks, ordered by weekday then start. Throws when a period row does
 * not have one cell per day: a layout the script did not understand would otherwise import courses on shifted days.
 */
fun parseIntranetTimetable(json: String): List<ImportedCourse> {
    val root = Json.parseToJsonElement(json).jsonObject
    val days = root["days"]?.jsonArray.orEmpty().map { day -> WEEKDAYS[day.jsonPrimitive.content.trim().take(1)] }
    // Per weekday, the course in each period (null where the cell is empty).
    val slots = mutableMapOf<DayOfWeek, MutableMap<Int, Triple<String, String, String>>>()
    root["periods"]?.jsonArray.orEmpty().forEach { element ->
        val row = element as? JsonObject ?: return@forEach
        val period = row["period"]?.jsonPrimitive?.content?.let { PERIOD.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: return@forEach
        val cells = (row["cells"] as? JsonArray).orEmpty()
        require(cells.size == days.size) { "${period}교시 has ${cells.size} cells for ${days.size} days" }
        cells.forEachIndexed { index, cell ->
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
