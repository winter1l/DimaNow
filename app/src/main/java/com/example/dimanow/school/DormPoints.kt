package com.example.dimanow.school

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** The intranet's 예인관 상벌점조회 list and outing application (D-107). */
const val INTRANET_DORM_POINTS_URL = "https://intranet.dima.ac.kr/dormi/P_C_List.aspx"
const val INTRANET_DORM_OUTING_URL = "https://intranet.dima.ac.kr/dormi/outingApply.aspx"

/**
 * Reads the 상벌점 table by its header names: year, term, date, merit, demerit and content. The student number
 * and name columns are left on the page.
 */
const val INTRANET_DORM_POINTS_SCRIPT = """
    var table=Array.from(document.querySelectorAll('table')).find(function(t){
      var head=t.rows.length?t.rows[0].innerText:'';
      return /상\s*점/.test(head)&&/벌\s*점/.test(head);
    });
    if(!table){
      var text=((document.body&&document.body.innerText)||'').slice(0,400);
      if(/WrongAccess/i.test(location.pathname)||/세션|로그인/.test(text))return {state:'signin'};
      return {state:'error',message:'인트라넷에서 상벌점 표를 찾지 못했어요.'};
    }
    var rows=Array.from(table.rows);
    var head=Array.from(rows[0].cells).map(function(c){return c.innerText.replace(/\s/g,'');});
    function at(name){return head.findIndex(function(h){return h.indexOf(name)>=0;});}
    var iYear=at('연도'),iTerm=at('학기'),iDate=at('날짜'),iMerit=at('상점'),iDemerit=at('벌점'),iContent=at('내용');
    var items=rows.slice(1).map(function(r){
      var c=Array.from(r.cells).map(function(x){return x.innerText.trim();});
      function v(i){return i>=0&&i<c.length?c[i]:'';}
      return {year:v(iYear),term:v(iTerm),date:v(iDate),merit:v(iMerit),demerit:v(iDemerit),content:v(iContent)};
    }).filter(function(x){return x.date||x.content;});
    return {state:'ok',data:{items:items}};
"""

data class DormPointEntry(
    val year: Int?,
    val term: Int?,
    val date: LocalDate?,
    val merit: Int,
    val demerit: Int,
    val content: String,
)

/** Totals for the latest term on the list, with that term's newest entries first. */
data class DormPointSummary(
    val year: Int?,
    val term: Int?,
    val merit: Int,
    val demerit: Int,
    val entries: List<DormPointEntry>,
)

fun parseDormPoints(json: String): List<DormPointEntry> =
    Json.parseToJsonElement(json).let { it as? JsonObject }?.get("items")?.jsonArray.orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        fun text(name: String) = row[name]?.jsonPrimitive?.content?.trim().orEmpty()
        DormPointEntry(
            year = text("year").toIntOrNull(),
            term = text("term").filter(Char::isDigit).toIntOrNull(),
            date = parseDormDate(text("date")),
            merit = text("merit").points(),
            demerit = text("demerit").points(),
            content = text("content"),
        )
    }

fun summarizeDormPoints(entries: List<DormPointEntry>): DormPointSummary? {
    if (entries.isEmpty()) return null
    val latest = entries.maxWith(compareBy<DormPointEntry>({ it.year ?: 0 }, { it.term ?: 0 }))
    val term = entries.filter { it.year == latest.year && it.term == latest.term }
    return DormPointSummary(
        year = latest.year,
        term = latest.term,
        merit = term.sumOf { it.merit },
        demerit = term.sumOf { it.demerit },
        entries = term.sortedByDescending { it.date ?: LocalDate.MIN },
    )
}

private fun String.points(): Int = filter { it.isDigit() || it == '-' }.toIntOrNull()?.let(Math::abs) ?: 0

private fun parseDormDate(value: String): LocalDate? {
    val digits = value.filter(Char::isDigit)
    if (digits.length != 8) return null
    return runCatching { LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt()) }.getOrNull()
}

/**
 * What the school screens read in this run of the app (D-107). Kept in memory only: it is gone when the app
 * process ends, and the student reads it again with one press.
 */
object SchoolReadCache {
    /** The last 상벌점 read; [summary] is null when the list was empty. */
    data class DormPointsRead(val summary: DormPointSummary?)

    private val mutableDormPoints = MutableStateFlow<DormPointsRead?>(null)
    val dormPoints: StateFlow<DormPointsRead?> = mutableDormPoints.asStateFlow()

    fun setDormPoints(read: DormPointsRead) {
        mutableDormPoints.value = read
    }

    private val mutableEquipmentCatalog = MutableStateFlow<List<EquipmentGroup>?>(null)
    val equipmentCatalog: StateFlow<List<EquipmentGroup>?> = mutableEquipmentCatalog.asStateFlow()

    fun setEquipmentCatalog(groups: List<EquipmentGroup>) {
        mutableEquipmentCatalog.value = groups
    }

    fun clear() {
        mutableDormPoints.value = null
        mutableEquipmentCatalog.value = null
    }
}
