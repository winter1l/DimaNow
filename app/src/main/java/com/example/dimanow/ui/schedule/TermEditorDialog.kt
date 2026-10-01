package com.example.dimanow.ui.schedule

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.ui.koreanDateLabel
import com.example.dimanow.ui.koreanDateLabelWithYear
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private enum class TermField { START, END }

/**
 * Term start/end editor (D-094(14)). Each date is chosen with the Material date picker, formatted
 * in Korean, instead of typed as `YYYY-MM-DD`. A reversed range keeps its explanation and cannot be
 * saved (gemini-install-20260917 behavior).
 *
 * [pickerDisplayMode] lets tests open the picker in its text-input mode.
 */
@Composable
internal fun TermEditorDialog(
    start: LocalDate,
    end: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate) -> Unit,
    pickerDisplayMode: DisplayMode = DisplayMode.Picker,
) {
    var startDate by rememberSaveable(start) { mutableStateOf(start) }
    var endDate by rememberSaveable(end) { mutableStateOf(end) }
    var picking by rememberSaveable { mutableStateOf<TermField?>(null) }
    val reversed = endDate.isBefore(startDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("학기 기간 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TermDateField(
                    label = "시작일",
                    date = startDate,
                    onClick = { picking = TermField.START },
                    modifier = Modifier.testTag("term_start"),
                )
                TermDateField(
                    label = "종료일",
                    date = endDate,
                    isError = reversed,
                    onClick = { picking = TermField.END },
                    modifier = Modifier.testTag("term_end"),
                )
                if (reversed) {
                    Text(
                        "종료일은 시작일과 같거나 늦어야 해요",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = !reversed, onClick = { onSave(startDate, endDate) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    picking?.let { field ->
        TermDatePickerDialog(
            title = if (field == TermField.START) "학기 시작일" else "학기 종료일",
            initial = if (field == TermField.START) startDate else endDate,
            displayMode = pickerDisplayMode,
            onDismiss = { picking = null },
            onConfirm = { date ->
                if (field == TermField.START) startDate = date else endDate = date
                picking = null
            },
        )
    }
}

/** A read-only date value that opens the picker; reads as "시작일, 2026년 8월 24일 (월)". */
@Composable
private fun TermDateField(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { if (isError) error("종료일이 시작일보다 빨라요") },
        shape = DimaShapes.Tile,
        border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
                Text(koreanDateLabelWithYear(date), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermDatePickerDialog(
    title: String,
    initial: LocalDate,
    displayMode: DisplayMode,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    KoreanLocale {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.toUtcMillis(),
            initialDisplayMode = displayMode,
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = { state.selectedDateMillis?.let { onConfirm(it.toUtcLocalDate()) } },
                    modifier = Modifier.testTag("term_picker_confirm"),
                ) { Text("선택") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        ) {
            DatePicker(
                state = state,
                modifier = Modifier.testTag("term_date_picker"),
                title = {
                    Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
                },
                headline = { KoreanDatePickerHeadline(state.selectedDateMillis) },
            )
        }
    }
}

/**
 * Picker headline in the app's date style (`8월 24일 (월)`); the stock headline formats with the
 * device language even when the calendar grid is Korean.
 */
@Composable
internal fun KoreanDatePickerHeadline(selectedDateMillis: Long?, modifier: Modifier = Modifier) {
    Text(
        text = selectedDateMillis?.let { koreanDateLabel(it.toUtcLocalDate()) } ?: "날짜를 선택해 주세요",
        modifier = modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
        maxLines = 1,
    )
}

/** Range headline for the pause picker: `10월 5일 (월) ~ 10월 9일 (금)`. */
@Composable
internal fun KoreanDateRangePickerHeadline(startMillis: Long?, endMillis: Long?, modifier: Modifier = Modifier) {
    val start = startMillis?.let { koreanDateLabel(it.toUtcLocalDate()) } ?: "시작일"
    val end = endMillis?.let { koreanDateLabel(it.toUtcLocalDate()) } ?: "종료일"
    Text(
        text = "$start ~ $end",
        modifier = modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
    )
}

/**
 * Formats Material date pickers in Korean (month names, weekday initials, headline and input
 * pattern) regardless of the device language; the app itself is Korean-only (D-094(14)).
 */
// Releases ship as a single APK from GitHub (no Play language splits), so Korean resources are
// always present for the locale override.
@SuppressLint("AppBundleLocaleChanges")
@Composable
internal fun KoreanLocale(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val configuration = remember(base) { Configuration(base).apply { setLocale(Locale.KOREA) } }
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        LocalContext provides localized,
        content = content,
    )
}

/** Material pickers speak UTC midnight milliseconds. */
internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
