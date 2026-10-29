package com.example.dimanow.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.stringResource
import com.example.dimanow.BuildConfig
import com.example.dimanow.R
import com.example.dimanow.feedback.FEEDBACK_MAX_CHARS
import com.example.dimanow.feedback.FEEDBACK_MAX_SCREENSHOTS
import com.example.dimanow.feedback.FEEDBACK_MIN_CHARS
import com.example.dimanow.feedback.FeedbackApi
import com.example.dimanow.feedback.FeedbackCategory
import com.example.dimanow.feedback.FeedbackOutbox
import com.example.dimanow.feedback.FeedbackReports
import com.example.dimanow.feedback.FeedbackResult
import com.example.dimanow.feedback.FeedbackScreenshot
import com.example.dimanow.feedback.collectFeedbackLog
import com.example.dimanow.feedback.prepareFeedbackScreenshot
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.LocalReducedMotion
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.dimaExpandIn
import com.example.dimanow.ui.motion.rememberShownAfter

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
    val appContext = LocalContext.current.applicationContext
    // A fresh form starts a new report; after an activity recreation the saved id re-attaches to the send, its
    // result and the screenshots, which live in FeedbackReports rather than in this composition (Q-032 (a)).
    val savedReport = rememberSaveable { FeedbackReports.open(null) }
    val report = remember(savedReport) { FeedbackReports.open(savedReport) }
    var category by rememberSaveable { mutableStateOf(FeedbackCategory.BUG) }
    var message by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    var includeVersions by rememberSaveable { mutableStateOf(true) }
    var includeLog by rememberSaveable { mutableStateOf(true) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var log by remember { mutableStateOf<String?>(null) }
    val screenshots by FeedbackReports.screenshots.collectAsState()
    val skippedUnreadable by FeedbackReports.skippedUnreadable.collectAsState()
    val screenshotError = "읽을 수 없는 사진은 빼고 붙였어요.".takeIf { skippedUnreadable }
    val state by FeedbackReports.state.collectAsState()
    val sending = state == FeedbackOutbox.State.Sending
    // D-137: a send answered within 200ms shows no indicator (M3: nothing below 200ms).
    val showSending = rememberShownAfter(sending)
    val result = (state as? FeedbackOutbox.State.Done)?.result
    var confirmDiscard by remember { mutableStateOf(false) }
    val bug = category == FeedbackCategory.BUG
    val length = message.trim().codePointCount(0, message.trim().length)
    val valid = length in FEEDBACK_MIN_CHARS..FEEDBACK_MAX_CHARS
    val posted = result as? FeedbackResult.Posted
    val edited = message.isNotBlank() || screenshots.isNotEmpty()
    val dismiss = { FeedbackReports.close(report); onDismiss() }
    val close = { if (edited && posted == null && !sending) confirmDiscard = true else dismiss() }
    LaunchedEffect(bug) { if (bug && log == null) log = loadLog() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(FEEDBACK_MAX_SCREENSHOTS)) { uris ->
        FeedbackReports.addScreenshots(report, uris) { uri -> prepareFeedbackScreenshot(appContext, uri) }
    }
    val submit = {
        touched = true
        if (valid && !sending && posted == null) {
            val draft = FeedbackDraft(
                category = category,
                message = message,
                includeVersions = includeVersions,
                log = log.takeIf { bug && includeLog },
                screenshots = if (bug) screenshots else emptyList(),
            )
            FeedbackReports.send(report) { send(draft) }
        }
    }

    // Once the issue is posted the action keeps what it showed while it fades away, so one change has one motion.
    val actionWaiting = rememberLastNonNull(showSending.takeIf { posted == null }) ?: false

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
                        // D-137: a send shows the loading indicator in place of 보내기 only once it has taken 200ms;
                        // once the issue is posted the action fades away with the form.
                        AnimatedVisibility(
                            visible = posted == null,
                            enter = fadeIn(DimaMotion.effectsDefault()),
                            exit = fadeOut(DimaMotion.effectsFast()),
                            label = "feedback_action",
                        ) {
                            AnimatedContent(
                                targetState = actionWaiting,
                                transitionSpec = { dimaContentReveal() },
                                contentAlignment = Alignment.Center,
                                label = "feedback_send",
                            ) { waiting ->
                                if (waiting) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        DimaLoadingIndicator(
                                            Modifier.semantics {
                                                contentDescription = "보내는 중"
                                                liveRegion = LiveRegionMode.Polite
                                            },
                                            size = 32.dp,
                                        )
                                    }
                                } else {
                                    TextButton(
                                        shapes = ButtonDefaults.shapes(),
                                        onClick = submit,
                                        enabled = !sending && posted == null,
                                        modifier = Modifier.testTag("feedback_send"),
                                    ) { Text("보내기") }
                                }
                            }
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
            ) {
                // D-137: the posted page fades in over the form while the page's height follows, instead of replacing
                // it in one frame; its check mark is the flow's one hero moment (FeedbackPosted).
                AnimatedContent(
                    targetState = posted,
                    contentKey = { it != null },
                    transitionSpec = { dimaContentReveal() },
                    label = "feedback_page",
                ) { shownPosted ->
                    if (shownPosted != null) {
                        FeedbackPosted(shownPosted, onClose = dismiss)
                        return@AnimatedContent
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                        // D-137: the 오류-only parts fold in and out with the 종류 choice, and the log preview and its
                        // log open and close in place, each with its 20dp gap inside the fold; the send failure below
                        // arrives the same way. The gaps live in the moving parts, so nothing jumps at their ends.
                        Column {
                            AnimatedVisibility(
                                visible = bug,
                                enter = dimaExpandIn(),
                                exit = dimaCollapseOut(),
                                label = "feedback_bug_parts",
                            ) {
                                Column(Modifier.padding(bottom = 20.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        FeedbackScreenshots(
                                            screenshots = screenshots,
                                            error = screenshotError,
                                            enabled = !sending,
                                            onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                            onRemove = { FeedbackReports.removeScreenshot(report, it) },
                                        )
                                        FeedbackSwitch(
                                            title = "앱 로그 함께 보내기",
                                            supporting = "이 앱의 최근 경고·오류 기록이에요. 이메일·전화번호·학번·주소·토큰은 가리고 보내요.",
                                            checked = includeLog,
                                            enabled = !sending,
                                            onChange = { includeLog = it },
                                            tag = "feedback_log",
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = includeLog,
                                        enter = dimaExpandIn(),
                                        exit = dimaCollapseOut(),
                                        label = "feedback_log_preview",
                                    ) {
                                        Column(Modifier.padding(top = 20.dp)) {
                                            val toggleLog = { showLog = !showLog }
                                            TextButton(
                                                shapes = ButtonDefaults.shapes(),
                                                onClick = toggleLog,
                                                modifier = Modifier
                                                    .testTag("feedback_log_preview")
                                                    .expandableSemantics(showLog, toggleLog),
                                            ) {
                                                Text(if (showLog) "보낼 로그 접기" else "보낼 로그 보기")
                                                ExpandChevron(showLog, Modifier.padding(start = 4.dp).size(20.dp), label = "feedback_log_chevron")
                                            }
                                            AnimatedVisibility(
                                                visible = showLog,
                                                enter = dimaExpandIn(),
                                                exit = dimaCollapseOut(),
                                                label = "feedback_log",
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = DimaTheme.colors.cardInner,
                                                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(max = 240.dp),
                                                ) {
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
                            AnimatedContent(
                                targetState = result?.takeIf { it !is FeedbackResult.Posted },
                                contentKey = { it?.let { shown -> shown::class } },
                                transitionSpec = { dimaContentReveal() },
                                label = "feedback_failure",
                            ) { failure ->
                                val failureModifier = Modifier.padding(top = 20.dp).testTag("feedback_error")
                                when (failure) {
                                    FeedbackResult.TooOften -> ErrorState(
                                        message = "잠시 뒤에 다시 보내 주세요",
                                        // The limit is per network address, so on campus Wi-Fi it may be someone else's report (D-122).
                                        supporting = "같은 네트워크에서는 10분에 한 번 보낼 수 있고, 하루에 받는 수도 정해져 있어요.",
                                        modifier = failureModifier,
                                    )
                                    FeedbackResult.NoRoomForScreenshots -> ErrorState(
                                        message = "지금은 스크린샷을 받을 수 없어요",
                                        supporting = "스크린샷을 빼고 다시 보내 주세요. 쓴 내용은 그대로 있어요.",
                                        modifier = failureModifier,
                                    )
                                    FeedbackResult.Rejected -> ErrorState(
                                        message = "이대로는 보낼 수 없어요",
                                        supporting = "내용이나 스크린샷을 줄이거나 다른 스크린샷으로 바꿔 다시 보내 주세요.",
                                        modifier = failureModifier,
                                    )
                                    FeedbackResult.Failed -> ErrorState(
                                        message = "제보를 보내지 못했어요",
                                        supporting = "인터넷 연결을 확인하고 다시 보내 주세요. 쓴 내용은 그대로 있어요.",
                                        onRetry = submit,
                                        retryLabel = "다시 보내기",
                                        modifier = failureModifier,
                                    )
                                    else -> Spacer(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
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
            onConfirm = { confirmDiscard = false; dismiss() },
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
                            shapes = IconButtonDefaults.shapes(),
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

/**
 * After sending: what happened, the issue's address as text (it can be selected or copied), and a way to open it.
 *
 * D-137: the flow's one hero moment. The page fades in (the caller's reveal) while the mark grows from half its size on
 * the default spatial spring, as [DimaDialogIcon] morphs it into its tone shape. It plays once, not again after a
 * rotation, and under [LocalReducedMotion] the mark is simply there.
 */
@Composable
private fun FeedbackPosted(posted: FeedbackResult.Posted, onClose: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    var markPlayed by rememberSaveable { mutableStateOf(false) }
    val markScale = remember { Animatable(if (markPlayed || reduced) 1f else POSTED_MARK_START_SCALE) }
    LaunchedEffect(Unit) {
        markPlayed = true
        if (markScale.value < 1f) markScale.animateTo(1f, DimaMotion.spatialDefault())
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp).testTag("feedback_posted"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = markScale.value
                scaleY = markScale.value
            },
        ) {
            DimaDialogIcon(Icons.Default.CheckCircle, DialogTone.INFO)
        }
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

/** How small the posted page's mark starts (D-137). */
private const val POSTED_MARK_START_SCALE = 0.5f

private fun feedbackPlaceholder(category: FeedbackCategory): String = when (category) {
    FeedbackCategory.BUG -> "어느 화면에서 무엇을 했을 때 어떻게 됐는지 적어 주세요."
    FeedbackCategory.QUESTION -> "궁금한 점을 적어 주세요."
    FeedbackCategory.IDEA -> "있으면 좋을 기능이나 바꾸고 싶은 점을 적어 주세요."
}

/** Sends through the gateway with this build's version name and the phone's Android SDK level. */
@Composable
private fun rememberFeedbackSender(): suspend (FeedbackDraft) -> FeedbackResult {
    val uploadUrl = stringResource(R.string.dormitory_meal_upload_url)
    return remember(uploadUrl) {
        val api = FeedbackApi(uploadUrl)
        // The build's own version name (no versionNameSuffix is configured), without a PackageManager call.
        val version: String? = BuildConfig.VERSION_NAME
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
