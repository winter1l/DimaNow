package com.example.dimanow.calendar

import android.content.Context
import com.example.dimanow.sync.ServiceCalendar
import com.example.dimanow.sync.ServiceCalendarPayload
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Process-wide service calendar (D-099 clause 7): public holidays, campus-shuttle suspensions and DIMA terms.
 *
 * Starts with the calendar bundled at build time ([BuiltInServiceCalendar], a copy of `data-source/calendar.json`
 * guarded by a unit test), is replaced by the last synchronized `calendar` dataset at startup, and again whenever
 * a newer dataset is imported. Readers use [current] so pure code (GuidanceEngine, 4402 schedule) needs no Context.
 */
object ServiceCalendarStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(BuiltInServiceCalendar.calendar)
    val calendar: StateFlow<ServiceCalendar> = state.asStateFlow()
    val current: ServiceCalendar get() = state.value

    /** Loads the cached synchronized calendar, keeping the bundled one when the cache is missing or invalid. */
    fun loadCached(context: Context) {
        val file = cacheFile(context)
        if (!file.isFile) return
        runCatching { decode(file.readBytes()) }.onSuccess { state.value = it }
    }

    /** Validates [bytes], stores them atomically and makes them current. Throws when the payload is invalid. */
    fun importSynchronized(context: Context, bytes: ByteArray) {
        val calendar = decode(bytes)
        val file = cacheFile(context)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "달력을 저장하지 못했어요." }
        }
        state.value = calendar
    }

    internal fun decode(bytes: ByteArray): ServiceCalendar =
        ServiceCalendar.from(json.decodeFromString<ServiceCalendarPayload>(bytes.decodeToString()))

    /** Test hook: replaces the current calendar. */
    fun replaceForTest(calendar: ServiceCalendar) {
        state.value = calendar
    }

    private fun cacheFile(context: Context) = File(context.filesDir, "service-calendar.json")
}

/** The calendar compiled into the APK; must equal `data-source/calendar.json` (see ServiceCalendarBundleTest). */
object BuiltInServiceCalendar {
    internal const val JSON = """
{
  "schemaVersion": 1,
  "terms": [
    {
      "id": "2026-1", "start": "2026-03-03", "end": "2026-06-22",
      "midterm": { "start": "2026-04-21", "end": "2026-04-27" },
      "final": { "start": "2026-06-16", "end": "2026-06-22" }
    },
    {
      "id": "2026-2", "start": "2026-08-24", "end": "2026-12-18",
      "midterm": { "start": "2026-10-19", "end": "2026-10-23" },
      "final": { "start": "2026-12-14", "end": "2026-12-18" }
    }
  ],
  "holidays": [
    { "date": "2026-01-01", "name": "1월 1일" },
    { "date": "2026-02-16", "name": "설날" },
    { "date": "2026-02-17", "name": "설날" },
    { "date": "2026-02-18", "name": "설날" },
    { "date": "2026-03-01", "name": "3·1절" },
    { "date": "2026-03-02", "name": "대체공휴일(3·1절)" },
    { "date": "2026-05-05", "name": "어린이날" },
    { "date": "2026-05-24", "name": "부처님오신날" },
    { "date": "2026-05-25", "name": "대체공휴일(부처님오신날)" },
    { "date": "2026-06-03", "name": "전국동시지방선거" },
    { "date": "2026-06-06", "name": "현충일" },
    { "date": "2026-08-15", "name": "광복절" },
    { "date": "2026-08-17", "name": "대체공휴일(광복절)" },
    { "date": "2026-09-24", "name": "추석" },
    { "date": "2026-09-25", "name": "추석" },
    { "date": "2026-09-26", "name": "추석" },
    { "date": "2026-10-03", "name": "개천절" },
    { "date": "2026-10-05", "name": "대체공휴일(개천절)" },
    { "date": "2026-10-09", "name": "한글날" },
    { "date": "2026-12-25", "name": "기독탄신일" },
    { "date": "2027-01-01", "name": "1월 1일" },
    { "date": "2027-02-06", "name": "설날" },
    { "date": "2027-02-07", "name": "설날" },
    { "date": "2027-02-08", "name": "설날" },
    { "date": "2027-02-09", "name": "대체공휴일(설날)" },
    { "date": "2027-03-01", "name": "3·1절" },
    { "date": "2027-05-01", "name": "노동절" },
    { "date": "2027-05-03", "name": "대체공휴일(노동절)" },
    { "date": "2027-05-05", "name": "어린이날" },
    { "date": "2027-05-13", "name": "부처님오신날" },
    { "date": "2027-06-06", "name": "현충일" },
    { "date": "2027-07-17", "name": "제헌절" },
    { "date": "2027-07-19", "name": "대체공휴일(제헌절)" },
    { "date": "2027-08-15", "name": "광복절" },
    { "date": "2027-08-16", "name": "대체공휴일(광복절)" },
    { "date": "2027-09-14", "name": "추석" },
    { "date": "2027-09-15", "name": "추석" },
    { "date": "2027-09-16", "name": "추석" },
    { "date": "2027-10-03", "name": "개천절" },
    { "date": "2027-10-04", "name": "대체공휴일(개천절)" },
    { "date": "2027-10-09", "name": "한글날" },
    { "date": "2027-10-11", "name": "대체공휴일(한글날)" },
    { "date": "2027-12-25", "name": "기독탄신일" },
    { "date": "2027-12-27", "name": "대체공휴일(기독탄신일)" }
  ],
  "campusShuttleSuspensions": [],
  "academicEvents": [
    { "name": "복학 신청", "start": "2026-02-02", "end": "2026-02-27" },
    { "name": "일반휴학 신청(1차)", "start": "2026-02-02", "end": "2026-02-13" },
    { "name": "등록금 납부", "start": "2026-02-23", "end": "2026-02-27" },
    { "name": "수강신청", "start": "2026-02-23", "end": "2026-02-27" },
    { "name": "일반휴학 신청(2차)", "start": "2026-02-23", "end": "2026-02-27" },
    { "name": "수강신청 정정", "start": "2026-03-03", "end": "2026-03-06" },
    { "name": "학점인정 신청", "start": "2026-03-03", "end": "2026-03-06" },
    { "name": "수강신청 포기", "start": "2026-03-09", "end": "2026-03-13" },
    { "name": "지정보강", "start": "2026-06-09", "end": "2026-06-15" },
    { "name": "수업평가", "start": "2026-06-16", "end": "2026-06-28" },
    { "name": "성적 열람", "start": "2026-07-01", "end": "2026-07-03" },
    { "name": "일반휴학 신청(1차)", "start": "2026-07-27", "end": "2026-08-07" },
    { "name": "복학 신청", "start": "2026-08-03", "end": "2026-08-21" },
    { "name": "등록금 납부", "start": "2026-08-18", "end": "2026-08-21" },
    { "name": "일반휴학 신청(2차)", "start": "2026-08-18", "end": "2026-08-24" },
    { "name": "수강신청 정정", "start": "2026-08-24", "end": "2026-08-28" },
    { "name": "학점인정 신청", "start": "2026-08-24", "end": "2026-08-28" },
    { "name": "수강신청 포기", "start": "2026-08-31", "end": "2026-09-04" },
    { "name": "지정보강", "start": "2026-12-07", "end": "2026-12-11" },
    { "name": "수업평가", "start": "2026-12-14", "end": "2026-12-26" },
    { "name": "성적 열람", "start": "2026-12-28", "end": "2026-12-30" }
  ]
}
"""

    val calendar: ServiceCalendar by lazy {
        ServiceCalendar.from(Json { ignoreUnknownKeys = true }.decodeFromString<ServiceCalendarPayload>(JSON.trim()))
    }
}
