package com.example.dimanow.school

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The library's front page, which shows its 이용시간 block to everyone (D-131). */
const val LIBRARY_HOME_URL = "https://lib.dima.ac.kr/"

/** 대출/연기/예약조회 with the loan list sorted by 반납예정일, nearest first (the page's own sort link, D-131). */
const val LIBRARY_LOANS_URL = "https://lib.dima.ac.kr/MyLibrary/?sort=retur_n"

/** The page the signed-in header's 대출중 link opens, where the student renews (연기신청) on the library's own page. */
const val LIBRARY_MY_LIBRARY_URL = "https://lib.dima.ac.kr/MyLibrary"

/** The library's 자료 검색. */
const val LIBRARY_SEARCH_URL = "https://lib.dima.ac.kr/Search"

/**
 * Reads `/MyLibrary` signed in (D-131). The page itself is server-rendered, so the script reads its DOM and hands
 * the loan table over raw, its header texts and each row's cells, for [parseLibraryStatus] to map by header name
 * (no real loan row has been seen yet: on 2026-10-25/26 the student had none).
 *
 * - Signed in means the header's `대출중 N건` (beside `연체중 N건` and the student's name) or a 로그아웃 link; else
 *   `signin`. Only the two numbers are taken from the header, never the name.
 * - The loan table is looked for in the 대출정보 tab (`#mylibrary_tab_01`), else on the whole page outside the
 *   예약정보 and 무인예약대출 tabs: the first table whose header row names 대출 and 반납 (else 반납 and a title such
 *   as 서명/자료명, and not 예약). A cell is its text, and it counts as an action when it holds an enabled link,
 *   button or checkbox (the 연기신청 column).
 * - `noData` says the 대출정보 tab reads "…없습니다" (an empty list may have no table at all).
 * - 이용 시간: the text pieces of the block headed 이용시간, on this page or else on the front page fetched with the
 *   same session; [pairLibraryHours] pairs them into places and times.
 */
const val LIBRARY_LOANS_SCRIPT = """
    function clean(s){return String(s||'').replace(/ /g,' ').replace(/[ \t]+/g,' ').replace(/ *\n */g,'\n').replace(/\n+/g,'\n').trim();}
    var text=(document.body&&document.body.innerText)||'';
    var loans=text.match(/대출\s*중\s*(\d+)\s*건/),overdue=text.match(/연체\s*중\s*(\d+)\s*건/);
    var signedIn=!!loans||Array.from(document.querySelectorAll('a')).some(function(a){
      return /로그아웃|log\s*out|log\s*off/i.test((a.textContent||'')+' '+(a.getAttribute('href')||''));
    });
    if(!signedIn)return {state:'signin'};
    var tab=document.querySelector('#mylibrary_tab_01');
    var scope=tab&&tab.querySelector('table')?tab:document;
    var tables=Array.from(scope.querySelectorAll('table')).filter(function(t){return !t.closest('#mylibrary_tab_02,#mylibrary_tab_03');});
    function headRow(t){return t.tHead&&t.tHead.rows.length?t.tHead.rows[t.tHead.rows.length-1]:t.rows[0];}
    function headText(t){var r=headRow(t);return r?clean(r.innerText).replace(/\s/g,''):'';}
    var table=tables.find(function(t){var h=headText(t);return /반납/.test(h)&&/대출/.test(h);})||
      tables.find(function(t){var h=headText(t);return /반납/.test(h)&&/서명|자료명|도서명|제목/.test(h)&&!/예약/.test(h);});
    var data={overdue:overdue?Number(overdue[1]):null,loans:loans?Number(loans[1]):null,table:null,noData:false,hours:[]};
    if(table){
      var head=headRow(table);
      var rows=Array.from(table.rows).filter(function(r){return r!==head&&!(table.tHead&&table.tHead.contains(r));});
      data.table={
        headers:Array.from(head.cells).map(function(c){return clean(c.innerText).slice(0,40);}),
        rows:rows.slice(0,100).map(function(r){
          var cells=Array.from(r.cells);
          return {
            cells:cells.map(function(c){return clean(c.innerText).slice(0,300);}),
            actions:cells.map(function(c){
              return Array.from(c.querySelectorAll('a[href],button,input[type=button],input[type=submit],input[type=checkbox]')).some(function(e){
                return !e.disabled&&!/disabled/i.test(String(e.className||''))&&e.getAttribute('aria-disabled')!=='true';
              });
            })
          };
        })
      };
    }
    data.noData=!!tab&&/없습니다/.test(clean(tab.innerText));
    function hoursIn(doc){
      var marks=Array.from(doc.querySelectorAll('h1,h2,h3,h4,h5,h6,dt,th,strong,b,em,p,span,div,li,a')).filter(function(e){
        var t=clean(e.textContent);return t.length<=20&&/이용\s*시간/.test(t);
      });
      for(var i=0;i<marks.length;i++){
        var box=marks[i];
        for(var up=0;up<4&&box;up++){
          var t=clean(box.textContent);
          if(t.length>600)break;
          if(/\d{1,2}\s*:\s*\d{2}|24\s*시간/.test(t)){
            var walker=doc.createTreeWalker(box,NodeFilter.SHOW_TEXT),tokens=[],node;
            while((node=walker.nextNode())&&tokens.length<40){
              if(node.parentNode&&/^(SCRIPT|STYLE|NOSCRIPT)$/.test(node.parentNode.nodeName))continue;
              var v=clean(node.nodeValue);
              if(v&&!/^이용\s*시간$/.test(v))tokens.push(v.slice(0,80));
            }
            return tokens;
          }
          box=box.parentElement;
        }
      }
      return [];
    }
    data.hours=hoursIn(document);
    if(!data.hours.length){
      try{
        var res=await fetch('/',{credentials:'same-origin'});
        if(res.ok)data.hours=hoursIn(new DOMParser().parseFromString(await res.text(),'text/html'));
      }catch(e){}
    }
    return {state:'ok',data:data};
"""

/** One book on loan, from a row of the 대출정보 table (D-131). */
data class LibraryBook(
    val title: String,
    val loanedOn: LocalDate?,
    val dueOn: LocalDate?,
    /** The 연기 column: true when the row offers 연기신청, false when it says it cannot, null when it does not say. */
    val renewable: Boolean?,
    /** The row itself says 연체 (its status or due-date cell). */
    val markedOverdue: Boolean = false,
) {
    fun isOverdue(today: LocalDate): Boolean = markedOverdue || dueOn?.isBefore(today) == true

    /** Overdue or due today: shown in the warning tone. */
    fun needsReturn(today: LocalDate): Boolean = isOverdue(today) || dueOn == today
}

/** One line of the library's 이용시간 block, e.g. `리더스스테이션` · `09:00 ~ 20:00`. */
data class LibraryHours(val place: String, val text: String)

/** What `/MyLibrary` showed: the header's counts, the loans (nearest due first) and the opening hours. */
data class LibraryStatus(
    /** `연체중 N건` from the header, null when it was not found. */
    val overdueCount: Int?,
    /** `대출중 N건` from the header, null when it was not found. */
    val loanCount: Int?,
    val books: List<LibraryBook>,
    val hours: List<LibraryHours>,
) {
    /** Books out: the header's count, or the rows read when there are more (an overdue book may be counted apart). */
    val loans: Int get() = maxOf(loanCount ?: 0, books.size)

    fun overdue(today: LocalDate): Int = maxOf(overdueCount ?: 0, books.count { it.isOverdue(today) })

    /** The header names more loans than the rows read: part of the list was not understood. */
    val incomplete: Boolean get() = loanCount != null && books.size < loanCount

    /** What the page says when [incomplete], pointing to the library's own list; null otherwise. */
    val incompleteNote: String?
        get() = when {
            !incomplete -> null
            books.isEmpty() -> "대출 중인 책 ${loanCount}권의 목록을 읽지 못했어요. 대출·연기에서 볼 수 있어요."
            else -> "대출 중인 책 ${loanCount}권 중 ${books.size}권만 읽었어요. 대출·연기에서 모두 볼 수 있어요."
        }
}

/**
 * Maps the script's answer to [LibraryStatus] by header names, not column positions: the title from 서명/자료명/
 * 도서명/제목 (its first line, without ` / 저자` when the header names the author too), 대출일, 반납예정일 (else 예정일,
 * 반납일), 연기신청/연기 and 상태. Rows that are a "no data" notice, have no title, or say they were returned are
 * left out. Without a table it can map, the header's counts still stand: the books are then empty and
 * [LibraryStatus.incomplete] says the list was not understood when the header names loans. Only when there is
 * neither a count nor a list nor the tab's "없습니다" is the page not understood, and this throws.
 */
fun parseLibraryStatus(json: String): LibraryStatus {
    val root = Json.parseToJsonElement(json) as? JsonObject ?: throw IllegalArgumentException("not an object")
    fun int(name: String) = (root[name] as? JsonPrimitive)?.intOrNull
    val overdue = int("overdue")
    val loans = int("loans")
    val noData = (root["noData"] as? JsonPrimitive)?.booleanOrNull == true
    val books = (root["table"] as? JsonObject)?.let(::readLoanTable)
        ?: if (loans != null || noData) emptyList() else throw IllegalArgumentException("no loan list")
    val hours = (root["hours"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return LibraryStatus(
        overdueCount = overdue,
        loanCount = loans,
        // Nearest due first, so an overdue book leads; a row without a readable date goes last.
        books = books.sortedWith(compareBy<LibraryBook> { it.dueOn ?: LocalDate.MAX }.thenBy { it.title }),
        hours = pairLibraryHours(hours),
    )
}

/** The table's books, or null when its headers name no title or due-date column. */
private fun readLoanTable(table: JsonObject): List<LibraryBook>? {
    val headers = (table["headers"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.contentOrNull.orEmpty().replace(SPACES, "") }
    fun column(vararg names: String): Int =
        names.firstNotNullOfOrNull { name -> headers.indexOfFirst { it.contains(name) }.takeIf { it >= 0 } } ?: -1
    val title = column("서명", "자료명", "도서명", "제목", "자료")
    val loaned = column("대출일")
    val due = column("반납예정", "예정일", "반납일", "반납")
    val renew = column("연기신청", "반납연기", "연기")
    val status = column("상태")
    if (title < 0 || due < 0) return null
    val titleWithAuthor = headers[title].contains("저자")
    return (table["rows"] as? JsonArray).orEmpty().mapNotNull { element ->
        val row = element as? JsonObject ?: return@mapNotNull null
        val cells = (row["cells"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.contentOrNull.orEmpty().trim() }
        val actions = (row["actions"] as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.booleanOrNull == true }
        // A one-cell row spanning the table is the site's "자료가 없습니다" notice, not a book.
        if (cells.size < 2 && headers.size > 1) return@mapNotNull null
        fun cell(index: Int) = if (index >= 0) cells.getOrNull(index).orEmpty() else ""
        val dueText = cell(due)
        val dueOn = parseLibraryDate(dueText)
        if (dueOn == null && cells.any { NO_DATA.containsMatchIn(it) }) return@mapNotNull null
        val name = cell(title).lineSequence().map(String::trim).firstOrNull(String::isNotEmpty).orEmpty()
            .let { if (titleWithAuthor) it.substringBefore(" / ").trim() else it }
        if (name.isEmpty()) return@mapNotNull null
        val state = cell(status)
        // A returned book left on the list is not on loan any more.
        if (state.contains("반납") && !state.contains("예정") && !state.contains("연체")) return@mapNotNull null
        LibraryBook(
            title = name,
            loanedOn = parseLibraryDate(cell(loaned)),
            dueOn = dueOn,
            renewable = if (renew >= 0) renewableFrom(cell(renew), actions.getOrNull(renew) == true) else null,
            markedOverdue = state.contains("연체") || dueText.contains("연체"),
        )
    }
}

private fun renewableFrom(text: String, action: Boolean): Boolean? = when {
    text.contains("불가") || text.contains("완료") || text.contains("초과") || text.contains("연체") -> false
    action || text.contains("가능") -> true
    else -> null
}

private val SPACES = Regex("""\s+""")
private val NO_DATA = Regex("""없습니다|없어요|없음""")
private val DATE = Regex("""(\d{4})\s*[-./년]\s*(\d{1,2})\s*[-./월]\s*(\d{1,2})""")
private val COMPACT_DATE = Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)""")

/** `2026-10-27`, `2026.10.27`, `2026/10/27`, `2026년 10월 27일` or `20261027`; null when none is there. */
internal fun parseLibraryDate(value: String): LocalDate? {
    val match = DATE.find(value) ?: COMPACT_DATE.find(value) ?: return null
    val (year, month, day) = match.destructured
    return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

/** A time, `24시간`, or a closed day: the value half of an 이용시간 line. */
private val HOURS_VALUE = Regex("""\d{1,2}\s*:\s*\d{2}|24\s*시간|휴관|휴실|미운영""")
private val CONNECTOR = Regex("""^[~∼～\-–]$""")
private val TILDE = Regex("""\s*[~∼～]\s*""")

/**
 * Pairs the text pieces of the 이용시간 block into places and times, whether a line came as one piece
 * (`리더스스테이션 09:00 ~ 20:00`) or as a place and its time (`자유열람실`, `24시간 운영중`), or the time itself in
 * pieces (`09:00`, `~`, `20:00`). Pieces with no time after them (`더보기`, a heading) are dropped; at most eight lines.
 */
internal fun pairLibraryHours(tokens: List<String>): List<LibraryHours> {
    val result = mutableListOf<LibraryHours>()
    var place: String? = null
    val value = StringBuilder()
    fun flush() {
        val name = place
        if (name != null && value.isNotBlank() && name.length <= 30) {
            result += LibraryHours(name, value.toString().replace(TILDE, " ~ ").replace(SPACES, " ").trim())
        }
        place = null
        value.setLength(0)
    }
    for (raw in tokens) {
        val token = raw.replace(SPACES, " ").trim()
        if (token.isEmpty()) continue
        val at = HOURS_VALUE.find(token)?.range?.first
        when {
            CONNECTOR.matches(token) -> if (value.isNotEmpty()) value.append(" ~ ")
            at == null -> {
                flush()
                place = token
            }
            at == 0 -> if (place != null) value.append(' ').append(token)
            else -> {
                flush()
                place = token.substring(0, at).trim().trimEnd(':', '·', '-').trim()
                value.append(token.substring(at))
            }
        }
    }
    flush()
    return result.take(8)
}

/** `D-3 · 10/29 반납`, `D-day · 10/26 반납`, `연체 2일 · 10/24 반납` (D-131, in the form of Home's `D-n · M/d 마감`). */
fun libraryDueLabel(dueOn: LocalDate?, today: LocalDate): String {
    if (dueOn == null) return "반납일을 읽지 못했어요"
    val days = ChronoUnit.DAYS.between(today, dueOn)
    val date = "${dueOn.monthValue}/${dueOn.dayOfMonth}"
    return when {
        days > 0 -> "D-$days · $date 반납"
        days == 0L -> "D-day · $date 반납"
        else -> "연체 ${-days}일 · $date 반납"
    }
}

/** How old a read may be before opening 도서관 reads the loans again by itself. */
internal val LIBRARY_AUTO_READ_AFTER: Duration = Duration.ofMinutes(30)

/** Opening 도서관 reads the loans when none is saved, or the saved read is older than half an hour. */
internal fun libraryAutoReadDue(readAt: Instant?, now: Instant): Boolean =
    readAt == null || Duration.between(readAt, now) >= LIBRARY_AUTO_READ_AFTER

/** A loan as saved for the morning digest: its title and due date, nothing else (D-131, D-108(4)). */
data class LibraryDueBook(val title: String, val dueOn: LocalDate)

/** The saved due list and when it was read. */
data class LibraryDueList(val books: List<LibraryDueBook>, val readAt: Instant)

fun LibraryStatus.dueList(readAt: Instant): LibraryDueList =
    LibraryDueList(books.mapNotNull { book -> book.dueOn?.let { LibraryDueBook(book.title, it) } }, readAt)

/** The file name of the saved due list, in the school folder of the no-backup storage beside the 상벌점 file. */
internal const val LIBRARY_DUE_FILE_NAME = "library-due.json"

/** The file name of the saved 도서관 read (D-136), beside the due list. */
internal const val LIBRARY_READ_FILE_NAME = "library-read.json"

/** The saved form of a 도서관 read: what the page showed and when, so it shows at once after a restart (D-136). */
internal fun encodeLibraryRead(read: SchoolReadCache.LibraryRead): String = buildJsonObject {
    put("version", 1)
    put("readAt", read.readAt.toEpochMilli())
    read.status.overdueCount?.let { put("overdueCount", it) }
    read.status.loanCount?.let { put("loanCount", it) }
    putJsonArray("books") {
        read.status.books.forEach { book ->
            addJsonObject {
                put("title", book.title)
                book.loanedOn?.let { put("loanedOn", it.toString()) }
                book.dueOn?.let { put("dueOn", it.toString()) }
                book.renewable?.let { put("renewable", it) }
                if (book.markedOverdue) put("markedOverdue", true)
            }
        }
    }
    putJsonArray("hours") {
        read.status.hours.forEach { hours ->
            addJsonObject {
                put("place", hours.place)
                put("text", hours.text)
            }
        }
    }
}.toString()

internal fun decodeLibraryRead(text: String): SchoolReadCache.LibraryRead? {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return null
    if ((root["version"] as? JsonPrimitive)?.intOrNull != 1) return null
    val readAt = (root["readAt"] as? JsonPrimitive)?.longOrNull?.let(Instant::ofEpochMilli) ?: return null
    fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
    fun JsonObject.date(name: String) = string(name)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val books = (root["books"] as? JsonArray).orEmpty().mapNotNull { element ->
        val book = element as? JsonObject ?: return@mapNotNull null
        LibraryBook(
            title = book.string("title") ?: return@mapNotNull null,
            loanedOn = book.date("loanedOn"),
            dueOn = book.date("dueOn"),
            renewable = (book["renewable"] as? JsonPrimitive)?.booleanOrNull,
            markedOverdue = (book["markedOverdue"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }
    val hours = (root["hours"] as? JsonArray).orEmpty().mapNotNull { element ->
        val line = element as? JsonObject ?: return@mapNotNull null
        LibraryHours(line.string("place") ?: return@mapNotNull null, line.string("text") ?: return@mapNotNull null)
    }
    val status = LibraryStatus(
        overdueCount = (root["overdueCount"] as? JsonPrimitive)?.intOrNull,
        loanCount = (root["loanCount"] as? JsonPrimitive)?.intOrNull,
        books = books,
        hours = hours,
    )
    return SchoolReadCache.LibraryRead(status, readAt)
}

internal fun encodeLibraryDue(list: LibraryDueList): String = buildJsonObject {
    put("version", 1)
    put("readAt", list.readAt.toEpochMilli())
    putJsonArray("books") {
        list.books.forEach { book ->
            addJsonObject {
                put("title", book.title)
                put("dueOn", book.dueOn.toString())
            }
        }
    }
}.toString()

internal fun decodeLibraryDue(text: String): LibraryDueList? {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: return null
    if ((root["version"] as? JsonPrimitive)?.intOrNull != 1) return null
    val readAt = (root["readAt"] as? JsonPrimitive)?.longOrNull?.let(Instant::ofEpochMilli) ?: return null
    val books = (root["books"] as? JsonArray).orEmpty().mapNotNull { element ->
        val book = element as? JsonObject ?: return@mapNotNull null
        val title = (book["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val dueOn = (book["dueOn"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return@mapNotNull null
        LibraryDueBook(title, dueOn)
    }
    return LibraryDueList(books, readAt)
}
