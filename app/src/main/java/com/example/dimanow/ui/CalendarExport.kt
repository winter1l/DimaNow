package com.example.dimanow.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `캘린더로 내보내기` on the 시간표 page (D-128(4)): picks 시간표 and/or 학사 일정 for one `.ics` file that the share
 * sheet hands to a calendar app. The app itself never writes to a calendar and asks no calendar permission (D-125(6)).
 * A part with nothing to export is shown unchecked and disabled.
 */
@Composable
internal fun CalendarExportDialog(
    courseCount: Int,
    academicEventCount: Int,
    onDismiss: () -> Unit,
    onExport: (includeTimetable: Boolean, includeAcademic: Boolean) -> Unit,
) {
    var timetable by rememberSaveable { mutableStateOf(courseCount > 0) }
    var academic by rememberSaveable { mutableStateOf(academicEventCount > 0) }
    val chosenTimetable = timetable && courseCount > 0
    val chosenAcademic = academic && academicEventCount > 0
    DimaAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Default.EventAvailable,
        tone = DialogTone.INFO,
        title = "캘린더로 내보내기",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("고른 일정을 파일로 만들어 공유해요. 캘린더 앱에서 열면 일정이 추가돼요.")
                ExportChoice(
                    title = "시간표",
                    supporting = if (courseCount > 0) "수업 ${courseCount}개 · 학기 끝까지 매주" else "등록된 수업이 없어요",
                    checked = chosenTimetable,
                    enabled = courseCount > 0,
                    onChange = { timetable = it },
                    tag = "export_timetable",
                )
                ExportChoice(
                    title = "학사 일정",
                    supporting = if (academicEventCount > 0) "앞으로 있을 일정 ${academicEventCount}개" else "앞으로 있을 일정이 없어요",
                    checked = chosenAcademic,
                    enabled = academicEventCount > 0,
                    onChange = { academic = it },
                    tag = "export_academic",
                )
            }
        },
        confirmLabel = "내보내기",
        onConfirm = { onExport(chosenTimetable, chosenAcademic) },
        confirmEnabled = chosenTimetable || chosenAcademic,
        confirmTag = "export_calendar_confirm",
        dismissLabel = "취소",
    )
}

@Composable
private fun ExportChoice(title: String, supporting: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Writes [ics] to `cache/exports/dima-now.ics` and opens the share sheet for it as `text/calendar` with a read grant
 * (D-128(4)). Returns false when the file could not be written or no app takes it.
 */
internal suspend fun shareIcsFile(context: Context, ics: String): Boolean {
    val file = withContext(Dispatchers.IO) {
        runCatching {
            File(context.cacheDir, "exports").apply { mkdirs() }.resolve("dima-now.ics").apply { writeText(ics, Charsets.UTF_8) }
        }.getOrNull()
    } ?: return false
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/calendar"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "캘린더로 내보내기")
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(chooser)
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    }
}
