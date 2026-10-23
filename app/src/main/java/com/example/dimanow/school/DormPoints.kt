package com.example.dimanow.school

import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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
 * What the school screens read (D-107). The last 상벌점 read is also kept in the app's no-backup storage, so the
 * card shows it after a restart until the student refreshes it (D-108); equipment availability lives in memory
 * only. Deleting the school account or all app data removes both.
 */
object SchoolReadCache {
    /** The last 상벌점 read and when it was made; [summary] is null when the list was empty. */
    data class DormPointsRead(val summary: DormPointSummary?, val readAt: Instant)

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dormPointsFile: File? = null

    private val mutableDormPoints = MutableStateFlow<DormPointsRead?>(null)
    val dormPoints: StateFlow<DormPointsRead?> = mutableDormPoints.asStateFlow()

    /** Keeps 상벌점 reads in [file] and shows the saved one as soon as it is loaded. */
    fun persistDormPointsIn(file: File) {
        dormPointsFile = file
        io.launch {
            val saved = runCatching { decodeDormPointsRead(file.readText()) }.getOrNull() ?: return@launch
            mutableDormPoints.compareAndSet(null, saved)
        }
    }

    fun setDormPoints(read: DormPointsRead) {
        mutableDormPoints.value = read
        val file = dormPointsFile ?: return
        io.launch {
            runCatching {
                file.parentFile?.mkdirs()
                val partial = File(file.parentFile, file.name + ".tmp")
                partial.writeText(encodeDormPointsRead(read))
                if (!partial.renameTo(file)) {
                    file.writeText(partial.readText())
                    partial.delete()
                }
            }
        }
    }

    private val mutableEquipmentCatalog = MutableStateFlow<List<EquipmentGroup>?>(null)
    val equipmentCatalog: StateFlow<List<EquipmentGroup>?> = mutableEquipmentCatalog.asStateFlow()

    fun setEquipmentCatalog(groups: List<EquipmentGroup>) {
        mutableEquipmentCatalog.value = groups
        // A new catalog makes earlier day counts stale; an open model reads its days again.
        mutableEquipmentAvailability.value = emptyMap()
    }

    /** Each opened model's days, kept with the catalog so the model reopens filled after leaving the page. */
    private val mutableEquipmentAvailability = MutableStateFlow<Map<Long, List<EquipmentDay>>>(emptyMap())
    val equipmentAvailability: StateFlow<Map<Long, List<EquipmentDay>>> = mutableEquipmentAvailability.asStateFlow()

    fun setEquipmentAvailability(modelNo: Long, days: List<EquipmentDay>) {
        mutableEquipmentAvailability.value = mutableEquipmentAvailability.value + (modelNo to days)
    }

    fun clear() {
        mutableDormPoints.value = null
        mutableEquipmentCatalog.value = null
        mutableEquipmentAvailability.value = emptyMap()
        dormPointsFile?.let { file -> io.launch { file.delete() } }
    }
}

/** The saved form of a 상벌점 read: the latest term's totals and entries, nothing else from the page. */
internal fun encodeDormPointsRead(read: SchoolReadCache.DormPointsRead): String = buildJsonObject {
    put("version", 1)
    put("readAt", read.readAt.toEpochMilli())
    read.summary?.let { summary ->
        putJsonObject("summary") {
            summary.year?.let { put("year", it) }
            summary.term?.let { put("term", it) }
            put("merit", summary.merit)
            put("demerit", summary.demerit)
            putJsonArray("entries") {
                summary.entries.forEach { entry ->
                    addJsonObject {
                        entry.year?.let { put("year", it) }
                        entry.term?.let { put("term", it) }
                        entry.date?.let { put("date", it.toString()) }
                        put("merit", entry.merit)
                        put("demerit", entry.demerit)
                        put("content", entry.content)
                    }
                }
            }
        }
    }
}.toString()

internal fun decodeDormPointsRead(text: String): SchoolReadCache.DormPointsRead? {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return null
    if (root["version"]?.jsonPrimitive?.intOrNull != 1) return null
    val readAt = root["readAt"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochMilli) ?: return null
    fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull
    val summary = (root["summary"] as? JsonObject)?.let { summary ->
        DormPointSummary(
            year = summary.int("year"),
            term = summary.int("term"),
            merit = summary.int("merit") ?: 0,
            demerit = summary.int("demerit") ?: 0,
            entries = summary["entries"]?.jsonArray.orEmpty().mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                DormPointEntry(
                    year = entry.int("year"),
                    term = entry.int("term"),
                    date = entry["date"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    merit = entry.int("merit") ?: 0,
                    demerit = entry.int("demerit") ?: 0,
                    content = entry["content"]?.jsonPrimitive?.content.orEmpty(),
                )
            },
        )
    }
    return SchoolReadCache.DormPointsRead(summary, readAt)
}
