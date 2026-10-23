package com.example.dimanow.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.dimanow.R
import com.example.dimanow.feedback.FEEDBACK_MAX_CHARS
import com.example.dimanow.feedback.FEEDBACK_MAX_SCREENSHOTS
import com.example.dimanow.feedback.FEEDBACK_MIN_CHARS
import com.example.dimanow.feedback.FeedbackApi
import com.example.dimanow.feedback.FeedbackCategory
import com.example.dimanow.feedback.FeedbackResult
import com.example.dimanow.feedback.FeedbackScreenshot
import com.example.dimanow.feedback.collectFeedbackLog
import com.example.dimanow.feedback.prepareFeedbackScreenshot
import com.example.dimanow.theme.DimaTheme
import kotlinx.coroutines.launch

/** What the form sends (D-119, D-120); the log and screenshots only ever go with an 오류 report. */
internal data class FeedbackDraft(
    val category: FeedbackCategory,
    val message: String,
    val includeVersions: Boolean,
    val log: String?,
    val screenshots: List<FeedbackScreenshot>,
)

/**
 * 문의·오류 제보 inside the app (D-119): a full-screen form (close X, `보내기`, a discard check for written text)
 * that the gateway posts as a public GitHub issue, so a student needs no GitHub account. D-120: an 오류 report can
 * add up to three screenshots (re-encoded without photo metadata) and the app's own warning log with personal values
 * masked, which the student can read before sending; after sending, the issue address is shown and can be copied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedbackDialog(
    onDismiss: () -> Unit,
    send: suspend (FeedbackDraft) -> FeedbackResult = rememberFeedbackSender(),
    loadLog: suspend () -> String = { collectFeedbackLog() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf(FeedbackCategory.BUG) }
    var message by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    var includeVersions by rememberSaveable { mutableStateOf(true) }
    var includeLog by rememberSaveable { mutableStateOf(true) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var log by remember { mutableStateOf<String?>(null) }
    val screenshots = remember { mutableStateListOf<FeedbackScreenshot>() }
    var screenshotError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FeedbackResult?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val bug = category == FeedbackCategory.BUG
    val length = message.trim().codePointCount(0, message.trim().length)
    val valid = length in FEEDBACK_MIN_CHARS..FEEDBACK_MAX_CHARS
    val posted = result as? FeedbackResult.Posted
    val edited = message.isNotBlank() || screenshots.isNotEmpty()
    val close = { if (edited && posted == null && !sending) confirmDiscard = true else onDismiss() }
    LaunchedEffect(bug) { if (bug && log == null) log = loadLog() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(FEEDBACK_MAX_SCREENSHOTS)) { uris ->
        scope.launch {
            screenshotError = null
            uris.take(FEEDBACK_MAX_SCREENSHOTS - screenshots.size).forEach { uri ->
                prepareFeedbackScreenshot(context, uri)?.let(screenshots::add) ?: run { screenshotError = "읽을 수 없는 사진은 빼고 붙였어요." }
            }
        }
    }
    val submit = {
        touched = true
        if (valid && !sending) {
            sending = true
            result = null
            scope.launch {
                result = send(
                    FeedbackDraft(
                        category = category,
                        message = message,
                        includeVersions = includeVersions,
                        log = log.takeIf { bug && includeLog },
                        screenshots = if (bug) screenshots.toList() else emptyList(),
                    ),
                )
                sending = false
            }
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            dialogWindow?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding().testTag("feedback_form"),
            topBar = {
                TopAppBar(
                    title = { Text("문의·오류 제보") },
                    navigationIcon = {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = close, modifier = Modifier.testTag("feedback_close")) {
                            Icon(Icons.Default.Close, contentDescription = "닫기")
                        }
                    },
                    actions = {
                        if (posted == null) {
                            TextButton(
                                shapes = ButtonDefaults.shapes(),
                                onClick = submit,
                                enabled = !sending,
                                modifier = Modifier.testTag("feedback_send"),
                            ) { Text(if (sending) "보내는 중" else "보내기") }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (posted != null) {
                    FeedbackPosted(posted, onClose = onDismiss)
                    return@Column
                }
                Text(
                    "보낸 내용은 GitHub에 공개 이슈로 올라가요. 이름·학번·연락처 같은 개인정보는 적지 마세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("종류", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DimaConnectedChoice(
                        options = FeedbackCategory.entries.map { it to it.label },
                        selected = category,
                        onSelect = { category = it },
                        enabled = !sending,
                        itemTag = { "feedback_category_${it.wire}" },
                    )
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= FEEDBACK_MAX_CHARS + 200) message = it },
                    label = { Text("내용") },
                    placeholder = { Text(feedbackPlaceholder(category)) },
                    minLines = 6,
                    enabled = !sending,
                    isError = touched && !valid,
                    supportingText = {
                        Text(
                            when {
                                touched && length < FEEDBACK_MIN_CHARS -> "${FEEDBACK_MIN_CHARS}자 이상 적어 주세요"
                                length > FEEDBACK_MAX_CHARS -> "${FEEDBACK_MAX_CHARS}자까지 보낼 수 있어요 · $length/$FEEDBACK_MAX_CHARS"
                                else -> "$length/$FEEDBACK_MAX_CHARS"
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("feedback_message"),
                )
                if (bug) {
                    FeedbackScreenshots(
                        screenshots = screenshots,
                        error = screenshotError,
                        enabled = !sending,
                        onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRemove = { screenshots.remove(it) },
                    )
                    FeedbackSwitch(
                        title = "앱 로그 함께 보내기",
                        supporting = "이 앱의 최근 경고·오류 기록이에요. 이메일·전화번호·학번·주소·토큰은 가리고 보내요.",
                        checked = includeLog,
                        enabled = !sending,
                        onChange = { includeLog = it },
                        tag = "feedback_log",
                    )
                    if (includeLog) {
                        TextButton(shapes = ButtonDefaults.shapes(), onClick = { showLog = !showLog }, modifier = Modifier.testTag("feedback_log_preview")) {
                            Text(if (showLog) "보낼 로그 접기" else "보낼 로그 보기")
                        }
                        if (showLog) {
                            Surface(shape = RoundedCornerShape(12.dp), color = DimaTheme.colors.cardInner, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                SelectionContainer {
                                    Text(
                                        log ?: "로그를 읽는 중이에요",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                FeedbackSwitch(
                    title = "앱·Android 버전 함께 보내기",
                    supporting = "문제를 다시 확인하는 데 써요. 기기 이름이나 계정은 보내지 않아요.",
                    checked = includeVersions,
                    enabled = !sending,
                    onChange = { includeVersions = it },
                    tag = "feedback_versions",
                )
                when (result) {
                    FeedbackResult.TooOften -> ErrorState(
                        message = "잠시 뒤에 다시 보내 주세요",
                        supporting = "제보는 10분에 한 번 보낼 수 있고, 하루에 받는 수도 정해져 있어요.",
                        modifier = Modifier.testTag("feedback_error"),
                    )
                    FeedbackResult.Failed -> ErrorState(
                        message = "제보를 보내지 못했어요",
                        supporting = "인터넷 연결을 확인하고 다시 보내 주세요. 쓴 내용은 그대로 있어요.",
                        onRetry = submit,
                        retryLabel = "다시 보내기",
                        modifier = Modifier.testTag("feedback_error"),
                    )
                    else -> Unit
                }
            }
        }
    }
    if (confirmDiscard) {
        DimaAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = Icons.Default.DeleteOutline,
            tone = DialogTone.DANGER,
            title = "쓴 제보를 버릴까요?",
            text = { Text("닫으면 쓴 내용과 붙인 스크린샷이 사라져요.") },
            confirmLabel = "버리기",
            onConfirm = { confirmDiscard = false; onDismiss() },
            dismissLabel = "계속 쓰기",
        )
    }
}

@Composable
private fun FeedbackSwitch(title: String, supporting: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Up to three screenshots as thumbnails, each with a remove button, and a note that they become public too. */
@Composable
private fun FeedbackScreenshots(
    screenshots: List<FeedbackScreenshot>,
    error: String?,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (FeedbackScreenshot) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("feedback_screenshots")) {
        Text("스크린샷 · 최대 ${FEEDBACK_MAX_SCREENSHOTS}장", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (screenshots.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                screenshots.forEachIndexed { index, shot ->
                    val image = remember(shot) { BitmapFactory.decodeByteArray(shot.bytes, 0, shot.bytes.size)?.asImageBitmap() }
                    Box(Modifier.size(96.dp)) {
                        image?.let {
                            Image(
                                it,
                                contentDescription = "스크린샷 ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onRemove(shot) },
                            enabled = enabled,
                            modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                        ) { Icon(Icons.Default.Close, contentDescription = "스크린샷 ${index + 1} 빼기", modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        if (screenshots.size < FEEDBACK_MAX_SCREENSHOTS) {
            OutlinedButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onAdd,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag("feedback_add_screenshot"),
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("스크린샷 붙이기", modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "스크린샷도 이슈에 공개돼요. 이름·학번·시간표가 보이면 가리고 붙여 주세요. 사진의 위치·기기 정보는 지우고 보내요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** After sending: what happened, the issue's address as text (it can be selected or copied), and a way to open it. */
@Composable
private fun FeedbackPosted(posted: FeedbackResult.Posted, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("feedback_posted"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DimaDialogIcon(Icons.Default.CheckCircle, DialogTone.INFO)
        Text("제보를 올렸어요", style = MaterialTheme.typography.headlineSmall)
        Text(
            "GitHub 이슈 #${posted.number}로 올라갔어요. 답변과 진행 상황은 이슈에서 볼 수 있어요.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Surface(shape = RoundedCornerShape(12.dp), color = DimaTheme.colors.cardInner, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        posted.url.removePrefix("https://"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("feedback_issue_url"),
                    )
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { clipboard.setText(AnnotatedString(posted.url)); copied = true }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "이슈 링크 복사")
                }
            }
        }
        if (copied) Text("링크를 복사했어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            shapes = ButtonDefaults.shapes(),
            onClick = { openUrl(context, posted.url) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("feedback_open_issue"),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("이슈 열기", modifier = Modifier.padding(start = 8.dp))
        }
        TextButton(shapes = ButtonDefaults.shapes(), onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("닫기") }
    }
}

private fun feedbackPlaceholder(category: FeedbackCategory): String = when (category) {
    FeedbackCategory.BUG -> "어느 화면에서 무엇을 했을 때 어떻게 됐는지 적어 주세요."
    FeedbackCategory.QUESTION -> "궁금한 점을 적어 주세요."
    FeedbackCategory.IDEA -> "있으면 좋을 기능이나 바꾸고 싶은 점을 적어 주세요."
}

/** Sends through the gateway with this build's version name and the phone's Android SDK level. */
@Composable
private fun rememberFeedbackSender(): suspend (FeedbackDraft) -> FeedbackResult {
    val context = LocalContext.current
    return remember(context) {
        val api = FeedbackApi(context.getString(R.string.dormitory_meal_upload_url))
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val sender: suspend (FeedbackDraft) -> FeedbackResult = { draft ->
            api.send(
                draft.category,
                draft.message,
                version.takeIf { draft.includeVersions },
                Build.VERSION.SDK_INT.takeIf { draft.includeVersions },
                draft.log,
                draft.screenshots,
            )
        }
        sender
    }
}
