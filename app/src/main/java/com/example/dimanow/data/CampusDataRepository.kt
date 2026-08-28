package com.example.dimanow.data

import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.domain.CampusZone
import com.example.dimanow.domain.Course
import com.example.dimanow.domain.GuidancePause
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface CampusDataRepository {
    val schedule: Flow<TermSchedule>
    val zones: Flow<List<CampusZone>>

    suspend fun ensureSeeded()
    suspend fun saveCourse(course: Course): Long
    suspend fun deleteCourse(id: Long)
    suspend fun setTerm(start: LocalDate, end: LocalDate)
    suspend fun addNoClassDate(date: LocalDate)
    suspend fun removeNoClassDate(date: LocalDate)
    suspend fun setGuidancePause(pause: GuidancePause)
    suspend fun clearGuidancePause()
    suspend fun installBundledCampusZones()
}
