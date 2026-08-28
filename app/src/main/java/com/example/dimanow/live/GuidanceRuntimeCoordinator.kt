package com.example.dimanow.live

import com.example.dimanow.domain.CampusZoneId
import com.example.dimanow.domain.TermSchedule
import com.example.dimanow.guidance.HomeBase
import com.example.dimanow.guidance.ShuttleScheduleIndex
import com.example.dimanow.shuttle.ShuttleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class GuidanceRuntimeSnapshot(
    val schedule: TermSchedule,
    val shuttle: ShuttleData,
    val shuttleIndex: ShuttleScheduleIndex,
    val resolvedZone: CampusZoneId,
    val displayOptions: LiveDisplayOptions,
    val homeBase: HomeBase,
)

@OptIn(FlowPreview::class)
class GuidanceRuntimeCoordinator<T : Any>(
    scope: CoroutineScope,
    coalesceMillis: Long = 250L,
    private val refresh: suspend (T) -> Unit,
) {
    private val requests = Channel<T>(Channel.CONFLATED)
    private val latest = MutableStateFlow<T?>(null)

    init {
        scope.launch {
            requests.receiveAsFlow()
                .debounce(coalesceMillis)
                .collect { refresh(it) }
        }
    }

    fun update(snapshot: T) {
        latest.value = snapshot
        requests.trySend(snapshot)
    }

    fun requestRefresh() {
        latest.value?.let(requests::trySend)
    }

    suspend fun awaitSnapshot(): T = latest.filterNotNull().first()
}
