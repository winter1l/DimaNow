package com.example.dimanow.lms

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.SafeBrowsingResponse
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.ui.ScreenColumn
import com.example.dimanow.ui.ScreenLazyColumn
import com.example.dimanow.ui.motion.expressiveBounceClick
import com.example.dimanow.ui.motion.pulseBreath
import com.example.dimanow.ui.motion.staggeredEntrance
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class LmsPresentedDetail(
    val detail: LmsItemDetail,
    val cached: Boolean,
    val attachmentsChanged: Boolean,
)

@Composable
fun LmsRoute(
    credentialStore: LmsCredentialStore,
    sessionController: LmsSessionController,
    loginBridge: LmsLoginBridge,
    autoLoginCoordinator: LmsAutoLoginCoordinator,
    source: LmsSource,
    now: Instant,
    modifier: Modifier = Modifier,
    onFullScreenChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val sessionState by sessionController.state.collectAsStateWithLifecycle()
    val credentialState by credentialStore.state.collectAsStateWithLifecycle()
    val snapshot by source.snapshot.collectAsStateWithLifecycle(initialValue = LmsSnapshot())
    val loginRequest by loginBridge.request.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selectedCourse by remember { mutableStateOf<String?>(null) }
    var selectedKind by remember { mutableStateOf<LmsItemKind?>(null) }
    var selectedRead by remember { mutableStateOf<Boolean?>(null) }
    var selectedDetail by remember { mutableStateOf<LmsPresentedDetail?>(null) }
    BackHandler(enabled = selectedDetail != null) { selectedDetail = null }
    BackHandler(enabled = loginRequest != null) { loginBridge.cancel() }
    // 로그인 WebView·글 상세가 떠 있는 동안 상위 셸이 하단 내비를 숨기게 알린다 (D-044)
    val fullScreen = loginRequest != null || selectedDetail != null
    LaunchedEffect(fullScreen) { onFullScreenChange(fullScreen) }
    DisposableEffect(Unit) {
        onDispose { onFullScreenChange(false) }
    }

    suspend fun loginAndRefresh(force: Boolean) {
        // `force` bypasses only the local data TTL. An already-active LMS session must not be
        // logged in again, because the official LMS allows only one concurrent session.
        val state = autoLoginCoordinator.ensureActive(force = false)
        if (state == LmsSessionState.ACTIVE) {
            when (val result = source.refresh(force = force)) {
                LmsRefreshResult.SessionExpired -> {
                    autoLoginCoordinator.markExpired()
                    if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) source.refresh(force = true)
                }
                LmsRefreshResult.CourseCatalogRequired -> {
                    sessionController.transition(LmsSessionState.EXPIRED)
                    if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                        when (val retried = source.refresh(force = true)) {
                            is LmsRefreshResult.Failure -> snackbar.showSnackbar(retried.message)
                            LmsRefreshResult.CourseCatalogRequired -> snackbar.showSnackbar("수업 목록을 확인하지 못했습니다")
                            else -> Unit
                        }
                    }
                }
                is LmsRefreshResult.Failure -> snackbar.showSnackbar(result.message)
                else -> Unit
            }
        }
    }

    // force 없이 시작하면 5분 캐시 TTL 내 재방문은 네트워크를 건드리지 않는다 (#26)
    LaunchedEffect(Unit) {
        if (credentialState == CredentialState.SAVED) {
            when (source.refresh(force = false)) {
                LmsRefreshResult.SessionExpired -> loginAndRefresh(force = false)
                LmsRefreshResult.CourseCatalogRequired -> loginAndRefresh(force = false)
                is LmsRefreshResult.Failure -> Unit
                else -> sessionController.transition(LmsSessionState.ACTIVE)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            loginRequest != null -> LmsAuthenticationWebView(
                request = requireNotNull(loginRequest),
                onComplete = loginBridge::complete,
                onAuthenticated = { courses ->
                    scope.launch {
                        runCatching { source.storeRenderedCourses(courses) }
                            .onSuccess { loginBridge.complete(LmsLoginResult.Success) }
                            .onFailure { loginBridge.complete(LmsLoginResult.Failure("수업 목록을 저장하지 못했습니다")) }
                    }
                },
                onCancel = loginBridge::cancel,
                modifier = Modifier.fillMaxSize(),
            )
            selectedDetail != null -> LmsDetailScreen(
                presented = requireNotNull(selectedDetail),
                source = source,
                sessionController = sessionController,
                autoLoginCoordinator = autoLoginCoordinator,
                onBack = { selectedDetail = null },
                onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                modifier = Modifier.fillMaxSize(),
            )
            sessionState != LmsSessionState.ACTIVE && (
                credentialState == CredentialState.EMPTY || credentialState == CredentialState.INVALIDATED ||
                    sessionState == LmsSessionState.CREDENTIALS_NEED_REVIEW
                ) -> LmsLoginScreen(
                    needsReview = sessionState == LmsSessionState.CREDENTIALS_NEED_REVIEW || credentialState == CredentialState.INVALIDATED,
                    onLogin = { username, password, automatic ->
                        scope.launch {
                            val credentials = SavedLmsCredentials(username.trim(), password, automatic)
                            if (automatic) {
                                credentialStore.save(credentials)
                                loginAndRefresh(force = true)
                            } else {
                                credentialStore.delete()
                                sessionController.transition(LmsSessionState.AUTHENTICATING)
                                val result = loginBridge.authenticate(credentials)
                                val next = when (result) {
                                    LmsLoginResult.Success -> LmsSessionState.ACTIVE
                                    LmsLoginResult.CredentialsRejected -> LmsSessionState.CREDENTIALS_NEED_REVIEW
                                    LmsLoginResult.InteractiveAuthenticationRequired -> LmsSessionState.INTERACTIVE_AUTH_REQUIRED
                                    else -> LmsSessionState.ERROR
                                }
                                sessionController.transition(next)
                                if (next == LmsSessionState.ACTIVE) source.refresh(force = true)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            else -> LmsItemsScreen(
                snapshot = snapshot,
                sessionState = sessionState,
                selectedCourse = selectedCourse,
                selectedKind = selectedKind,
                selectedRead = selectedRead,
                onCourseChange = { selectedCourse = it },
                onKindChange = { selectedKind = it },
                onReadChange = { selectedRead = it },
                onRefresh = { scope.launch { loginAndRefresh(force = true) } },
                onOpenItem = { item ->
                    scope.launch {
                        suspend fun present(result: LmsDetailLoadResult): Boolean = when (result) {
                            is LmsDetailLoadResult.Fresh -> {
                                selectedDetail = LmsPresentedDetail(
                                    result.detail,
                                    cached = false,
                                    attachmentsChanged = result.attachmentsChanged,
                                )
                                true
                            }
                            is LmsDetailLoadResult.Cached -> {
                                selectedDetail = LmsPresentedDetail(result.detail, cached = true, attachmentsChanged = false)
                                true
                            }
                            is LmsDetailLoadResult.Failure -> {
                                snackbar.showSnackbar(result.message)
                                false
                            }
                            LmsDetailLoadResult.SessionExpired -> false
                        }
                        val first = source.loadDetail(item)
                        if (!present(first) && first == LmsDetailLoadResult.SessionExpired) {
                            autoLoginCoordinator.markExpired()
                            if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                                val retried = source.loadDetail(item)
                                if (!present(retried)) {
                                    snackbar.showSnackbar(
                                        if (retried == LmsDetailLoadResult.SessionExpired) "로그인이 필요합니다" else "글을 불러오지 못했습니다",
                                    )
                                }
                            }
                        }
                    }
                },
                now = now,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun LmsLoginScreen(
    needsReview: Boolean,
    onLogin: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var automatic by remember { mutableStateOf(true) }
    ScreenColumn(title = "수업", modifier = modifier) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(0),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("LMS 계정으로 로그인", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (needsReview) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = "계정 정보를 다시 확인해 주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("학번") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("lms_username"),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("lms_password"),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("자동 로그인", fontWeight = FontWeight.Bold)
                        Text("이 기기에 암호화해 저장합니다", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = automatic, onCheckedChange = { automatic = it })
                }
                Button(
                    onClick = { onLogin(username, password, automatic) },
                    enabled = username.isNotBlank() && password.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("lms_login"),
                ) { Text("로그인", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun LmsItemsScreen(
    snapshot: LmsSnapshot,
    sessionState: LmsSessionState,
    selectedCourse: String?,
    selectedKind: LmsItemKind?,
    selectedRead: Boolean?,
    onCourseChange: (String?) -> Unit,
    onKindChange: (LmsItemKind?) -> Unit,
    onReadChange: (Boolean?) -> Unit,
    onRefresh: () -> Unit,
    onOpenItem: (LmsItem) -> Unit,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    val filtered = filterLmsItems(snapshot.items, selectedCourse, selectedKind, selectedRead)
    val filterActive = selectedCourse != null || selectedKind != null || selectedRead != null
    val syncing = snapshot.syncState == LmsSyncState.SYNCING
    val hasError = sessionState == LmsSessionState.ERROR || snapshot.syncState == LmsSyncState.ERROR
    var todayMode by rememberSaveable { mutableStateOf(true) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    val agenda = remember(snapshot.items, now) {
        LmsAgendaPlanner(Clock.fixed(now, SEOUL)).plan(snapshot.items, now)
    }
    val visibleEmpty = if (todayMode) agenda.groups.isEmpty() else filtered.isEmpty()

    ScreenLazyColumn(
        title = "수업",
        modifier = modifier,
        listTag = "lms_history",
        topAction = {
            IconButton(onClick = onRefresh, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(
                        Modifier.size(22.dp).pulseBreath(),
                        strokeWidth = 2.5.dp,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                }
            }
        },
    ) {
        item {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().staggeredEntrance(0),
            ) {
                listOf(true to "오늘", false to "전체").forEachIndexed { index, (isToday, label) ->
                    SegmentedButton(
                        selected = todayMode == isToday,
                        onClick = { todayMode = isToday },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f).testTag(if (isToday) "lms_mode_today" else "lms_mode_all"),
                    )
                }
            }
        }

        if (!todayMode) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.staggeredEntrance(1)) {
                    item { FilterChip(selected = selectedCourse == null, onClick = { onCourseChange(null) }, label = { Text("전체") }, shape = RoundedCornerShape(10.dp)) }
                    items(snapshot.courses, key = { it.id }) { course ->
                        FilterChip(selected = selectedCourse == course.id, onClick = { onCourseChange(course.id) }, label = { Text(course.name) }, shape = RoundedCornerShape(10.dp))
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.staggeredEntrance(2)) {
                    item { FilterChip(selected = selectedKind == null, onClick = { onKindChange(null) }, label = { Text("전체") }, shape = RoundedCornerShape(10.dp)) }
                    items(LmsItemKind.entries.filter { kind -> snapshot.items.any { it.kind == kind } }) { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { onKindChange(kind) },
                            label = { Text(kindLabel(kind)) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("lms_kind_${kind.name}"),
                        )
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.staggeredEntrance(3)) {
                    item { FilterChip(selected = selectedRead == null, onClick = { onReadChange(null) }, label = { Text("모두") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("lms_read_all")) }
                    item { FilterChip(selected = selectedRead == false, onClick = { onReadChange(false) }, label = { Text("안읽음") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("lms_read_unread")) }
                    item { FilterChip(selected = selectedRead == true, onClick = { onReadChange(true) }, label = { Text("읽음") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("lms_read_read")) }
                }
            }
        }

        when {
            visibleEmpty && syncing -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                            .staggeredEntrance(3),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 3.dp, strokeCap = StrokeCap.Round)
                        Text("수업 정보를 불러오는 중", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            visibleEmpty && hasError -> {
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(3),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = snapshot.errorMessage ?: "수업 정보를 불러오지 못했습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FilledTonalButton(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
                                Text("다시 시도", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            visibleEmpty -> {
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(3),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (todayMode) "오늘 확인할 학습이 없습니다" else if (filterActive) "필터에 해당하는 항목이 없습니다" else "표시할 항목이 없습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!todayMode && filterActive) {
                                TextButton(onClick = { onCourseChange(null); onKindChange(null); onReadChange(null) }) {
                                    Text("필터 해제", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (todayMode) {
                    agenda.groups.forEach { group ->
                        item(key = "agenda-${group.key}-${group.date}") {
                            if (group.key == LmsAgendaGroupKey.COMPLETED) {
                                TextButton(
                                    onClick = { completedExpanded = !completedExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        group.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${group.items.size}개")
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (completedExpanded) "접기" else "보기")
                                }
                            } else {
                                Text(
                                    text = agendaGroupTitle(group),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        if (group.key != LmsAgendaGroupKey.COMPLETED || completedExpanded) {
                            items(group.items, key = { item -> "today-${item.kind}-${item.courseId}-${item.id}" }) { item ->
                                LmsItemCard(item, onOpenItem)
                            }
                        }
                    }
                } else {
                    items(filtered, key = { item -> "${item.kind}:${item.courseId}:${item.id}" }) { item ->
                        LmsItemCard(item, onOpenItem)
                    }
                }
            }
        }
    }
}

@Composable
private fun LmsItemCard(item: LmsItem, onOpenItem: (LmsItem) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().staggeredEntrance(4).expressiveBounceClick { onOpenItem(item) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LmsStatusBadge(kindLabel(item.kind), prominent = false)
                Text(
                    item.courseName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LmsStatusBadge(if (item.isRead) "읽음" else "안읽음", prominent = !item.isRead)
            }
            val badges = buildList {
                when (item.changeState) {
                    LmsChangeState.NEW -> add("새 항목")
                    LmsChangeState.UPDATED -> add("변경됨")
                    LmsChangeState.NONE -> Unit
                }
                when (item.completionState) {
                    LmsCompletionState.COMPLETE -> add("완료")
                    LmsCompletionState.INCOMPLETE -> add("미완료")
                    LmsCompletionState.NOT_TRACKED, LmsCompletionState.UNKNOWN -> Unit
                }
            }
            if (badges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { badge -> LmsStatusBadge(badge, prominent = true) }
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            item.dueAt?.let {
                Text("마감 ${LMS_TIME.format(it.atZone(SEOUL))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } ?: item.registeredAt?.let {
                Text("등록 ${LMS_TIME.format(it.atZone(SEOUL))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LmsStatusBadge(label: String, prominent: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (prominent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (prominent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

private fun agendaGroupTitle(group: LmsAgendaGroup): String = when (group.key) {
    LmsAgendaGroupKey.DATE -> if (group.title == "오늘") {
        "오늘"
    } else {
        group.date?.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)) ?: group.title
    }
    else -> group.title
}

internal fun filterLmsItems(
    items: List<LmsItem>,
    courseId: String?,
    kind: LmsItemKind?,
    isRead: Boolean?,
): List<LmsItem> = items.filter { item ->
    (courseId == null || item.courseId == courseId) &&
        (kind == null || item.kind == kind) &&
        (isRead == null || item.isRead == isRead)
}

@Composable
private fun LmsDetailScreen(
    presented: LmsPresentedDetail,
    source: LmsSource,
    sessionController: LmsSessionController,
    autoLoginCoordinator: LmsAutoLoginCoordinator,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = presented.detail
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<LmsAttachment?>(null) }
    var downloading by remember { mutableStateOf(false) }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val attachment = pending
        pending = null
        if (uri != null && attachment != null) {
            scope.launch {
                downloading = true
                val cache = File(context.cacheDir, "lms-attachments/${System.nanoTime()}-${attachment.fileName}").apply { parentFile?.mkdirs() }
                var result = source.downloadAttachment(attachment, cache)
                if (result is LmsRefreshResult.Failure && result.message.contains("로그인 세션")) {
                    sessionController.transition(LmsSessionState.EXPIRED)
                    if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                        result = source.downloadAttachment(attachment, cache)
                    }
                }
                if (result is LmsRefreshResult.Success) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output -> cache.inputStream().use { it.copyTo(output) } }
                        cache.delete()
                    }
                    onMessage("저장했습니다")
                } else onMessage((result as? LmsRefreshResult.Failure)?.message ?: "저장하지 못했습니다")
                downloading = false
            }
        }
    }
    Column(modifier.statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                text = detail.item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (presented.attachmentsChanged) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("첨부파일이 변경됐어요", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            }
        } else if (presented.cached) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("저장된 내용", fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
            }
        }
        AndroidView(
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
                    }
                    loadDataWithBaseURL("https://lms.dima.ac.kr", detail.sanitizedHtml, "text/html", "UTF-8", null)
                }
            },
            update = { it.loadDataWithBaseURL("https://lms.dima.ac.kr", detail.sanitizedHtml, "text/html", "UTF-8", null) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (detail.attachments.isNotEmpty()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.attachments.forEach { attachment ->
                    OutlinedButton(
                        onClick = { pending = attachment; createDocument.launch(attachment.fileName) },
                        enabled = !downloading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Default.Download, null); Spacer(Modifier.size(8.dp)); Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun LmsAuthenticationWebView(
    request: LmsLoginRequest,
    onComplete: (LmsLoginResult) -> Unit,
    onAuthenticated: (List<LmsCourse>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val parser = remember { LmsHtmlParser() }
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Column(modifier.statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "로그인 취소")
            }
            Text(
                text = "공식 포털에서 로그인 중",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CircularProgressIndicator(
                Modifier.size(20.dp).pulseBreath(),
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round,
            )
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= 26) WebView.startSafeBrowsing(context, null)
                    var injected = false
                    var catalogRequested = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, webRequest: WebResourceRequest): Boolean {
                            val uri = webRequest.url
                            val allowed = LmsUrlPolicy.isAllowedLoginNavigation(uri.toString())
                            if (!allowed) {
                                val upgraded = LmsUrlPolicy.upgradeOfficialHttp(uri.toString())
                                if (upgraded != null) view.loadUrl(upgraded)
                                else {
                                    Log.w(
                                        "DimaNowLms",
                                        "External navigation scheme=${uri.scheme} host=${uri.host} path=${uri.path} " +
                                            "port=${uri.port} userInfoPresent=${uri.userInfo != null}",
                                    )
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                            return !allowed
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = Unit

                        override fun onPageFinished(view: WebView, url: String) {
                            val path = Uri.parse(url).path.orEmpty()
                            if (path == MAIN_PATH) {
                                view.evaluateJavascript(
                                    "Boolean(document.querySelector(\"a[href*='/lms/myLecture/doListView']\"))",
                                ) { authenticated ->
                                    if (
                                        lmsLoginPageAction(url, pageFinished = true, authenticatedMain = authenticated == "true") ==
                                        LmsLoginPageAction.LOAD_DASHBOARD
                                    ) {
                                        view.loadUrl(LMS_DASHBOARD_URL)
                                    }
                                }
                            } else if (
                                lmsLoginPageAction(url, pageFinished = true, authenticatedMain = false) ==
                                LmsLoginPageAction.EXTRACT_COURSES && !catalogRequested
                            ) {
                                catalogRequested = true
                                view.evaluateJavascript(EXTRACT_RENDERED_COURSES_SCRIPT) { value ->
                                    val courses = parser.parseRenderedCourses(value)
                                    if (courses.isEmpty()) {
                                        onComplete(LmsLoginResult.Failure("수업 목록을 확인하지 못했습니다"))
                                    } else {
                                        CookieManager.getInstance().flush()
                                        onAuthenticated(courses)
                                    }
                                }
                            } else if (isOfficialLmsCredentialPage(url) && !injected) {
                                injected = true
                                val user = JSONObject.quote(request.credentials.username)
                                val password = JSONObject.quote(request.credentials.password)
                                val portal = Uri.parse(url).host == PORTAL_HOST
                                val submission = if (portal) {
                                    "var i=document.querySelector('#txtID'),p=document.querySelector('#txtPwd');" +
                                        "if(!i||!p||typeof Login!=='function')return 'interactive';" +
                                        "i.value=$user;p.value=$password;Login('N');return 'submitted';"
                                } else {
                                    "var i=document.querySelector('#id'),p=document.querySelector('#pass');" +
                                        "if(!i||!p||typeof login_proc!=='function')return 'interactive';" +
                                        "i.value=$user;p.value=$password;login_proc();return 'submitted';"
                                }
                                view.evaluateJavascript(
                                    "(function(){$submission})()",
                                ) { result ->
                                    if (result == "\"submitted\"") {
                                        view.postDelayed(
                                            {
                                                if (
                                                    !request.result.isCompleted &&
                                                    shouldReviewStoredLmsCredentials(
                                                        view.url.orEmpty(),
                                                        submitted = true,
                                                        elapsedMillis = LOGIN_RESULT_TIMEOUT_MILLIS,
                                                    )
                                                ) {
                                                    onComplete(LmsLoginResult.CredentialsRejected)
                                                }
                                            },
                                            LOGIN_RESULT_TIMEOUT_MILLIS,
                                        )
                                    }
                                }
                            }
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) {
                                onComplete(LmsLoginResult.NetworkError(error.description.toString()))
                            }
                        }

                        override fun onSafeBrowsingHit(view: WebView, request: WebResourceRequest, threatType: Int, callback: SafeBrowsingResponse) {
                            callback.backToSafety(true)
                            onComplete(LmsLoginResult.Failure("안전하지 않은 페이지가 차단되었습니다"))
                        }
                    }
                    loadUrl(LOGIN_URL)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

private fun kindLabel(kind: LmsItemKind): String = when (kind) {
    LmsItemKind.NOTICE -> "공지"
    LmsItemKind.ASSIGNMENT -> "과제"
    LmsItemKind.CONTENT -> "콘텐츠"
    LmsItemKind.MATERIAL -> "자료"
    LmsItemKind.QUESTION -> "질문"
    LmsItemKind.DISCUSSION -> "토론"
    LmsItemKind.TEAM_PROJECT -> "팀프로젝트"
    LmsItemKind.QUIZ -> "퀴즈"
    LmsItemKind.EXAM -> "시험"
    LmsItemKind.OTHER -> "기타"
}

private val SEOUL = ZoneId.of("Asia/Seoul")
private val LMS_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
internal const val OFFICIAL_LMS_LOGIN_URL =
    "https://portal.dima.ac.kr/?r=https://lms.dima.ac.kr/sso/index.jsp"
private const val LOGIN_URL = OFFICIAL_LMS_LOGIN_URL
private const val LMS_DASHBOARD_URL =
    "https://lms.dima.ac.kr/lms/myLecture/doListView.dunet?to_do_type=all"
private val EXTRACT_RENDERED_COURSES_SCRIPT = """
    (function(){
      return Array.from(document.querySelectorAll("[href*='fncGoClassroom'],[onclick*='fncGoClassroom']"))
        .map(function(link){
          var action=(link.getAttribute('href')||'')+' '+(link.getAttribute('onclick')||'');
          var match=action.match(/fncGoClassroom\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]*)['"]/);
          if(!match)return null;
          var root=link.closest('li.box,.lecture_info,.lecture-card,.course-card')||link.parentElement||link;
          var nameNode=link.querySelector('.title,.lecture_title')||root.querySelector('.title,.lecture_title')||link;
          var text=root.innerText||'';
          var professor=text.match(/교수(?:명)?\s*[:：]\s*([^\n·|]+)/);
          return {id:match[1],classNo:match[2],name:(nameNode.textContent||'').trim(),professor:professor?professor[1].trim():null};
        }).filter(Boolean);
    })()
""".trimIndent()
private const val PORTAL_HOST = "portal.dima.ac.kr"
private const val MAIN_PATH = "/main/MainView.dunet"
private const val LOGIN_RESULT_TIMEOUT_MILLIS = 10_000L
