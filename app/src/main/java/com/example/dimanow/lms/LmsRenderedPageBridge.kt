package com.example.dimanow.lms

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface LmsRenderedPageResult {
    data class Success(val finalUrl: String, val html: String) : LmsRenderedPageResult
    data object SessionExpired : LmsRenderedPageResult
    data class Failure(val message: String) : LmsRenderedPageResult
}

interface LmsRenderedPageLoader {
    suspend fun load(item: LmsItem, course: LmsCourse): LmsRenderedPageResult
}

class LmsRenderedPageRequest internal constructor(
    val item: LmsItem,
    val course: LmsCourse,
    internal val result: CompletableDeferred<LmsRenderedPageResult>,
)

class LmsRenderedPageBridge : LmsRenderedPageLoader {
    private val mutableRequest = MutableStateFlow<LmsRenderedPageRequest?>(null)
    val request: StateFlow<LmsRenderedPageRequest?> = mutableRequest.asStateFlow()

    override suspend fun load(item: LmsItem, course: LmsCourse): LmsRenderedPageResult {
        val pending = LmsRenderedPageRequest(item, course, CompletableDeferred())
        check(mutableRequest.compareAndSet(null, pending)) { "LMS page rendering is already in progress" }
        return try {
            pending.result.await()
        } finally {
            mutableRequest.compareAndSet(pending, null)
        }
    }

    fun complete(result: LmsRenderedPageResult) {
        mutableRequest.value?.result?.complete(result)
    }

    fun cancel() {
        complete(LmsRenderedPageResult.Failure("글 불러오기가 취소되었습니다"))
    }
}
