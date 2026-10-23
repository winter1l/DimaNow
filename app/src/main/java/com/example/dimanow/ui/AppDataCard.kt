package com.example.dimanow.ui

import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import android.app.ActivityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dimanow.sync.TimetableBackup
import com.example.dimanow.data.TimetableBackupCodec
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opening a timetable file, shared by 데이터 관리 and the empty timetable (D-102): [open] shows the file
 * picker, a readable file waits in [pending] until [TimetableImportConfirmation] confirms the replacement,
 * and every outcome is reported through the message callback given to [rememberTimetableImport].
 */
@Stable
internal class TimetableImport internal constructor() {
    internal var pending by mutableStateOf<TimetableBackup?>(null)
    internal var launch: () -> Unit = {}
    internal var restore: (TimetableBackup) -> Unit = {}

    fun open() = launch()
}

@Composable
internal fun rememberTimetableImport(onMessage: (String) -> Unit): TimetableImport {
    val context = LocalContext.current
    val app = context.applicationContext as? com.example.dimanow.DimaNowApplication
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onMessage)
    val import = remember { TimetableImport() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("파일을 열지 못했어요.")
                }
                TimetableBackupCodec.decode(bytes)
            }.onSuccess { import.pending = it }
                .onFailure { report((it as? IllegalArgumentException)?.message ?: "시간표 파일을 읽지 못했어요.") }
        }
    }
    import.launch = { launcher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
    import.restore = { backup ->
        if (app != null) scope.launch {
            report(
                runCatching { TimetableBackupCodec.restore(app.repository, backup); "시간표를 불러왔어요." }
                    .getOrElse { "시간표를 불러오지 못했어요." },
            )
        }
    }
    return import
}

/** Asks before a picked timetable file replaces the current timetable. */
@Composable
internal fun TimetableImportConfirmation(import: TimetableImport) {
    val backup = import.pending ?: return
    AlertDialog(
        icon = { DimaDialogIcon(Icons.Default.Restore, DialogTone.WARNING) },
        onDismissRequest = { import.pending = null },
        title = { Text("시간표를 바꿀까요?") },
        text = { Text("지금 시간표를 지우고 파일의 과목 ${backup.courses.size}개와 학기 ${backup.termStart} ~ ${backup.termEnd}로 바꿔요.") },
        confirmButton = {
            TextButton(shapes = ButtonDefaults.shapes(), 
                onClick = { import.pending = null; import.restore(backup) },
                modifier = Modifier.testTag("confirm_import_timetable"),
            ) { Text("바꾸기") }
        },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { import.pending = null }) { Text("취소") } },
    )
}

/** 데이터 관리 (D-099 clause 9): timetable export/import and deleting everything the app stored. */
@Composable
internal fun AppDataCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as? com.example.dimanow.DimaNowApplication
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val timetableImport = rememberTimetableImport { message = it }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null || app == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = runCatching {
                val bytes = TimetableBackupCodec.encode(app.repository.schedule.first())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("파일을 열지 못했어요.")
                }
                "시간표를 저장했어요."
            }.getOrElse { "시간표를 저장하지 못했어요." }
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("app_data_card"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("데이터 관리", style = MaterialTheme.typography.titleMedium)
            Text(
                "시간표는 이 기기에만 있어요. 폰을 바꾸기 전에 파일로 저장해 두세요. 학교 계정은 파일에 들어가지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(shapes = ButtonDefaults.shapes(), 
                onClick = { exportLauncher.launch("dima-now-시간표-${LocalDate.now(ZoneId.of("Asia/Seoul"))}.json") },
                modifier = Modifier.fillMaxWidth().testTag("export_timetable"),
            ) { Text("시간표 파일로 저장") }
            OutlinedButton(shapes = ButtonDefaults.shapes(), 
                onClick = timetableImport::open,
                modifier = Modifier.fillMaxWidth().testTag("import_timetable"),
            ) { Text("시간표 파일 불러오기") }
            TextButton(shapes = ButtonDefaults.shapes(), 
                onClick = { confirmDeleteAll = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("delete_all_data"),
            ) { Text("모든 데이터 지우기") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("app_data_message")) }
        }
    }

    TimetableImportConfirmation(timetableImport)
    if (confirmDeleteAll) {
        AlertDialog(
            icon = { DimaDialogIcon(Icons.Default.DeleteForever, DialogTone.DANGER) },
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("모든 데이터를 지울까요?") },
            text = { Text("시간표, 설정, 저장된 학교 계정과 받아 둔 정보가 모두 지워지고 앱이 닫혀요. 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), 
                    onClick = {
                        confirmDeleteAll = false
                        // Wipes databases, DataStore, WebView cookies and caches; Android then stops the app.
                        context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_all_data"),
                ) { Text("모두 지우기") }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDeleteAll = false }) { Text("취소") } },
        )
    }
}
