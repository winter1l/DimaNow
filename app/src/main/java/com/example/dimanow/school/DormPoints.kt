package com.example.dimanow.school

import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * What the school screens read (D-107). The last 상벌점 read (D-108) and the last 도서관 read (D-136) are also kept in
 * the app's no-backup storage, so their screens show them at once after a restart and read again only when they
 * are due; the library's titles and due dates are saved once more on their own for the morning digest (D-131).
 * Equipment lives in memory only. Deleting the school account or all app data removes all of them.
 */
object SchoolReadCache {
    /** The last 상벌점 read and when it was made; [summary] is null when the list was empty. */
    data class DormPointsRead(val summary: DormPointSummary?, val readAt: Instant)

    // The load, writes and the delete run in launch order on one IO lane, so 계정 삭제 cannot be undone by a write or
    // the startup load that was still pending (a cleared generation is never loaded or written again).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val lane = Dispatchers.IO.limitedParallelism(1)
    private val io = CoroutineScope(SupervisorJob() + lane)
    @Volatile private var generation = 0
    private var dormPointsFile: File? = null
    // Set at startup and read by the morning digest's worker thread.
    @Volatile private var libraryDueFile: File? = null
    private var libraryReadFile: File? = null

    private val mutableDormPoints = MutableStateFlow<DormPointsRead?>(null)
    val dormPoints: StateFlow<DormPointsRead?> = mutableDormPoints.asStateFlow()

    /**
     * Keeps the 상벌점 and 도서관 reads in [directory] (no-backup storage) and shows the saved ones as soon as they are
     * loaded. The library's due list for the morning digest (D-131) is kept in the same folder.
     */
    fun persistIn(directory: File) {
        val dormFile = File(directory, DORM_POINTS_FILE_NAME).also { dormPointsFile = it }
        val libraryFile = File(directory, LIBRARY_READ_FILE_NAME).also { libraryReadFile = it }
        libraryDueFile = File(directory, LIBRARY_DUE_FILE_NAME)
        val loading = generation
        io.launch {
            runCatching { decodeDormPointsRead(dormFile.readText()) }.getOrNull()?.let { saved ->
                if (generation == loading) mutableDormPoints.compareAndSet(null, saved)
            }
            runCatching { decodeLibraryRead(libraryFile.readText()) }.getOrNull()?.let { saved ->
                if (generation == loading) mutableLibrary.compareAndSet(null, saved)
            }
        }
    }

    fun setDormPoints(read: DormPointsRead) {
        mutableDormPoints.value = read
        val file = dormPointsFile ?: return
        val writing = generation
        io.launch {
            if (generation != writing) return@launch
            runCatching { writeReplacing(file, encodeDormPointsRead(read)) }
        }
    }

    /** The last 도서관 read (D-131): the loans and opening hours and when they were read. */
    data class LibraryRead(val status: LibraryStatus, val readAt: Instant)

    private val mutableLibrary = MutableStateFlow<LibraryRead?>(null)
    val library: StateFlow<LibraryRead?> = mutableLibrary.asStateFlow()

    /** Shows [read] and saves it, and its titles and due dates once more for the morning digest. */
    fun setLibrary(read: LibraryRead) {
        mutableLibrary.value = read
        val readFile = libraryReadFile
        val dueFile = libraryDueFile
        val writing = generation
        io.launch {
            if (generation != writing) return@launch
            readFile?.let { runCatching { writeReplacing(it, encodeLibraryRead(read)) } }
            dueFile?.let { runCatching { writeReplacing(it, encodeLibraryDue(read.status.dueList(read.readAt))) } }
        }
    }

    /**
     * The saved due list for the morning digest (D-131), or null when none was saved. It is read on the IO lane, so
     * a write or a delete started before it has finished first.
     */
    suspend fun savedLibraryDue(): LibraryDueList? {
        val file = libraryDueFile ?: return null
        return withContext(lane) {
            runCatching { if (file.exists()) decodeLibraryDue(file.readText()) else null }.getOrNull()
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

    /** Why the last catalog read failed and when; cleared by a successful read. */
    data class EquipmentCatalogFailure(val message: String, val at: Instant)

    private val mutableEquipmentCatalogFailure = MutableStateFlow<EquipmentCatalogFailure?>(null)
    val equipmentCatalogFailure: StateFlow<EquipmentCatalogFailure?> = mutableEquipmentCatalogFailure.asStateFlow()

    fun setEquipmentCatalogFailure(failure: EquipmentCatalogFailure?) {
        mutableEquipmentCatalogFailure.value = failure
    }

    fun clear() {
        generation++
        mutableDormPoints.value = null
        mutableEquipmentCatalog.value = null
        mutableEquipmentAvailability.value = emptyMap()
        mutableEquipmentCatalogFailure.value = null
        mutableLibrary.value = null
        dormPointsFile?.let { file -> io.launch { file.delete() } }
        libraryDueFile?.let { file -> io.launch { file.delete() } }
        libraryReadFile?.let { file -> io.launch { file.delete() } }
    }
}

/** The file name of the saved 상벌점 read, in the school folder of the no-backup storage. */
internal const val DORM_POINTS_FILE_NAME = "dorm-points.json"

/** Writes [text] to a temporary file beside [file] and moves it into place, so a crash never leaves half a file. */
internal fun writeReplacing(file: File, text: String) {
    file.parentFile?.mkdirs()
    val partial = File(file.parentFile, file.name + ".tmp")
    partial.writeText(text)
    if (!partial.renameTo(file)) {
        file.writeText(partial.readText())
        partial.delete()
    }
}

/** How long a failed catalog read keeps the page from reading DIMA C again by itself (Q-031). */
internal val EQUIPMENT_CATALOG_RETRY_PAUSE: Duration = Duration.ofMinutes(5)

/**
 * Whether opening 장비 대여 may read the catalog by itself: not within [EQUIPMENT_CATALOG_RETRY_PAUSE] of a failed
 * read, so leaving and reopening the page while DIMA C refuses the phone (Q-029) does not read it again each time.
 * 다시 시도 and pull-to-refresh still read at once.
 */
internal fun equipmentCatalogAutoReadAllowed(failedAt: Instant?, now: Instant): Boolean =
    failedAt == null || Duration.between(failedAt, now) >= EQUIPMENT_CATALOG_RETRY_PAUSE

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
