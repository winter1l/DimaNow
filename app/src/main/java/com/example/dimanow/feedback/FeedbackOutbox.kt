package com.example.dimanow.feedback

import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The report form's send, its result and its screenshots, kept outside the composition (Q-032 (a)). A theme, font
 * size, split-screen or rotation change recreates the activity while the POST still posts the issue; the form now
 * re-attaches to the same report instead of coming back empty and inviting a second send into the Worker's lease.
 * A report is named by an id the form keeps in saved state: the same id re-attaches, any other id starts afresh.
 */
internal class FeedbackOutbox(private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State
        data object Sending : State
        data class Done(val result: FeedbackResult) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _screenshots = MutableStateFlow<List<FeedbackScreenshot>>(emptyList())
    val screenshots: StateFlow<List<FeedbackScreenshot>> = _screenshots.asStateFlow()

    /** The last pick had an image that could not be read and was left out. */
    private val _skippedUnreadable = MutableStateFlow(false)
    val skippedUnreadable: StateFlow<Boolean> = _skippedUnreadable.asStateFlow()

    private var report: String? = null
    private var work = SupervisorJob(scope.coroutineContext[Job])

    /** Re-attaches to report [id] when it is the current one; otherwise (null, or a report of an earlier process) starts afresh. */
    @Synchronized
    fun open(id: String?): String {
        if (id != null && id == report) return id
        reset()
        return (id ?: UUID.randomUUID().toString()).also { report = it }
    }

    /** The form was closed: forget report [id], its screenshots and result. A send already on the wire is not recalled. */
    @Synchronized
    fun close(id: String) {
        if (id != report) return
        reset()
        report = null
    }

    /** Sends report [id] once at a time; false when another send is in flight or the report is no longer open. */
    @Synchronized
    fun send(id: String, send: suspend () -> FeedbackResult): Boolean {
        if (id != report || _state.value == State.Sending) return false
        _state.value = State.Sending
        scope.launch(work) {
            val result = try {
                send()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FeedbackResult.Failed
            }
            finish(id) { _state.value = State.Done(result) }
        }
        return true
    }

    /** Prepares picked images in order and adds them up to [FEEDBACK_MAX_SCREENSHOTS]; [prepare] returns null for one it cannot read. */
    @Synchronized
    fun <T> addScreenshots(id: String, picked: List<T>, prepare: suspend (T) -> FeedbackScreenshot?) {
        if (id != report) return
        _skippedUnreadable.value = false
        val room = FEEDBACK_MAX_SCREENSHOTS - _screenshots.value.size
        scope.launch(work) {
            picked.take(room).forEach { item ->
                val shot = prepare(item)
                finish(id) {
                    when {
                        shot == null -> _skippedUnreadable.value = true
                        _screenshots.value.size < FEEDBACK_MAX_SCREENSHOTS -> _screenshots.value += shot
                    }
                }
            }
        }
    }

    @Synchronized
    fun removeScreenshot(id: String, shot: FeedbackScreenshot) {
        if (id == report) _screenshots.value -= shot
    }

    /** Applies [update] only while report [id] is still the open one, so a late answer cannot land in a newer report. */
    @Synchronized
    private fun finish(id: String, update: () -> Unit) {
        if (id == report) update()
    }

    private fun reset() {
        work.cancel()
        work = SupervisorJob(scope.coroutineContext[Job])
        _state.value = State.Idle
        _screenshots.value = emptyList()
        _skippedUnreadable.value = false
    }
}

/**
 * The app's one report outbox. It runs on the main thread like the form's own scope did; the POST and the image
 * decoding move to the IO pool themselves.
 */
internal val FeedbackReports = FeedbackOutbox(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
