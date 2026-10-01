package com.example.dimanow.lms

import android.net.Uri
import android.provider.DocumentsContract
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import com.example.dimanow.theme.emphasized
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.dimanow.ui.DimaLayout
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun LmsDetailScreen(
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
    var pendingDocument by remember { mutableStateOf<LmsPendingDocument?>(null) }
    // 어떤 첨부가 내려받는 중인지 추적해, 한 항목을 눌러도 나머지가 함께 비활성화되지 않게 한다 (D-056)
    var downloadingAttachment by remember { mutableStateOf<String?>(null) }
    var activeDownloadCache by remember { mutableStateOf<File?>(null) }
    val activeDownloadCacheOnDispose by rememberUpdatedState(activeDownloadCache)
    DisposableEffect(Unit) {
        onDispose {
            activeDownloadCacheOnDispose?.delete()
        }
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val verified = pendingDocument
        pendingDocument = null
        if (uri == null || verified == null) {
            verified?.cache?.delete()
            activeDownloadCache = null
            downloadingAttachment = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LmsDocumentWriter(
                    openOutputStream = { destination: Uri ->
                        context.contentResolver.openOutputStream(destination, "w")
                    },
                    deleteDocument = { destination: Uri ->
                        runCatching {
                            DocumentsContract.deleteDocument(context.contentResolver, destination)
                        }.getOrDefault(false)
                    },
                ).write(verified.cache, uri, verified.expectedBytes)
            }
            when (result) {
                is LmsDocumentWriteResult.Success -> onMessage("첨부파일을 저장했어요")
                is LmsDocumentWriteResult.Failure -> onMessage(LmsUserMessages.ATTACHMENT_SAVE_FAILED)
            }
            activeDownloadCache = null
            downloadingAttachment = null
        }
    }
    LmsFullScreenPane(
        title = kindLabel(detail.item.kind),
        onBack = onBack,
        modifier = modifier,
        titleMaxLines = 2,
    ) {
        if (presented.attachmentsChanged) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("첨부파일이 변경됐어요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
            }
        } else if (presented.cached) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("저장된 내용", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
                .widthIn(max = DimaLayout.readingWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("lms_native_detail_body"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    detail.item.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(detail.item.title, style = MaterialTheme.typography.headlineSmall.emphasized())
            val registeredAt = detail.metadata.registeredAt ?: detail.item.registeredAt
            if (!detail.metadata.author.isNullOrBlank() || registeredAt != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.metadata.author?.takeIf { it.isNotBlank() }?.let { author ->
                        LmsDetailMetadataRow("작성자", author)
                    }
                    registeredAt?.let { value ->
                        LmsDetailMetadataRow("등록일", LMS_DETAIL_TIME.format(value.atZone(SEOUL)))
                    }
                }
            }
            val body = AnnotatedString.fromHtml(
                htmlString = detail.sanitizedHtml,
                linkInteractionListener = {},
            )
            SelectionContainer {
                Text(
                    text = body.takeUnless { it.isBlank() } ?: AnnotatedString("표시할 본문이 없어요"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (detail.item.kind == LmsItemKind.ASSIGNMENT) {
                assignmentPeriod(detail)?.let { period ->
                    LmsDetailMetadataRow("제출기간", period)
                }
                detail.metadata.maxScore?.takeIf { it.isNotBlank() }?.let { score ->
                    LmsDetailMetadataRow("만점", score)
                }
            }
            if (detail.attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("첨부파일", style = MaterialTheme.typography.titleSmall)
                    detail.attachments.forEach { attachment ->
                        val attachmentKey = "${attachment.id}|${attachment.fileName}"
                        OutlinedButton(
                            onClick = {
                                if (downloadingAttachment != null) return@OutlinedButton
                                downloadingAttachment = attachmentKey
                                scope.launch {
                                    val cache = runCatching {
                                        createLmsAttachmentCacheFile(context.cacheDir)
                                    }.getOrElse {
                                        downloadingAttachment = null
                                        onMessage("첨부파일을 저장할 준비를 하지 못했어요. 다시 시도해 주세요.")
                                        return@launch
                                    }
                                    activeDownloadCache = cache
                                    var result = source.downloadAttachment(attachment, cache)
                                    if (result == LmsAttachmentDownloadResult.SessionExpired) {
                                        sessionController.transition(LmsSessionState.EXPIRED)
                                        if (autoLoginCoordinator.ensureActive(force = true) == LmsSessionState.ACTIVE) {
                                            result = source.downloadAttachment(attachment, cache)
                                        }
                                    }
                                    when (result) {
                                        is LmsAttachmentDownloadResult.Success -> {
                                            if (result.bytesWritten <= 0L || cache.length() != result.bytesWritten) {
                                                cache.delete()
                                                activeDownloadCache = null
                                                downloadingAttachment = null
                                                onMessage("첨부파일 크기를 확인하지 못했어요. 다시 시도해 주세요")
                                            } else {
                                                pendingDocument = LmsPendingDocument(cache, result.bytesWritten)
                                                createDocument.launch(result.fileName)
                                            }
                                        }
                                        LmsAttachmentDownloadResult.SessionExpired -> {
                                            cache.delete()
                                            activeDownloadCache = null
                                            downloadingAttachment = null
                                            onMessage(LmsUserMessages.SIGN_IN_REQUIRED)
                                        }
                                        is LmsAttachmentDownloadResult.Failure -> {
                                            cache.delete()
                                            activeDownloadCache = null
                                            downloadingAttachment = null
                                            onMessage(LmsUserMessages.ATTACHMENT_FAILED)
                                        }
                                    }
                                }
                            },
                            enabled = downloadingAttachment == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (downloadingAttachment == attachmentKey) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, strokeCap = StrokeCap.Round)
                            } else {
                                Icon(Icons.Default.Download, null)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(attachment.fileName, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LmsDetailMetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun assignmentPeriod(detail: LmsItemDetail): String? {
    val start = detail.metadata.submissionStartsAt
    val end = detail.metadata.submissionEndsAt ?: detail.item.dueAt
    return when {
        start != null && end != null ->
            "${LMS_DETAIL_TIME.format(start.atZone(SEOUL))} ~ ${LMS_DETAIL_TIME.format(end.atZone(SEOUL))}"
        end != null -> "마감 · ${LMS_DETAIL_TIME.format(end.atZone(SEOUL))}"
        start != null -> "시작 · ${LMS_DETAIL_TIME.format(start.atZone(SEOUL))}"
        else -> null
    }
}

@Composable
internal fun LmsRenderedPageWebView(
    request: LmsRenderedPageRequest,
    onComplete: (LmsRenderedPageResult) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val parser = remember { LmsHtmlParser() }
    LmsFullScreenPane(
        title = "글 불러오는 중",
        onBack = onCancel,
        modifier = modifier,
        backDescription = "불러오기 취소",
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                Modifier.size(36.dp),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round,
            )
            AndroidView(
                factory = {
                    WebView(context).apply {
                        visibility = View.INVISIBLE
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        var submitted = false
                        var assignmentActionSubmitted = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): WebResourceResponse? = if (
                                shouldBlockLmsWebResource(webRequest.url.toString(), loginFlow = false)
                            ) {
                                blockedLmsWebResourceResponse()
                            } else {
                                super.shouldInterceptRequest(view, webRequest)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                webRequest: WebResourceRequest,
                            ): Boolean {
                                if (LmsUrlPolicy.isAllowed(webRequest.url.toString())) return false
                                onComplete(LmsRenderedPageResult.Failure(LmsUserMessages.UNSAFE_PAGE_BLOCKED))
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                val uri = Uri.parse(url)
                                val path = uri.path.orEmpty()
                                if (isOfficialLmsCredentialPage(url) || path == MAIN_PATH) {
                                    onComplete(LmsRenderedPageResult.SessionExpired)
                                    return
                                }
                                if (path == "/lms/myLecture/doListView.dunet" && !submitted) {
                                    val type = request.item.kind.officialContentType()
                                    if (type == null) {
                                        onComplete(LmsRenderedPageResult.Failure("이 글 형식은 아직 지원하지 않아요"))
                                        return
                                    }
                                    submitted = true
                                    val script =
                                        "(function(){if(typeof fnGoContent!=='function')return 'missing';" +
                                            "fnGoContent(${JSONObject.quote(type)}," +
                                            "${JSONObject.quote(request.course.id)}," +
                                            "${JSONObject.quote(request.course.classNo)}," +
                                            "${JSONObject.quote(request.item.id)},'S');return 'submitted';})()"
                                    view.evaluateJavascript(script) { result ->
                                        if (result == "\"missing\"") {
                                            onComplete(LmsRenderedPageResult.Failure("공식 LMS에서 글을 열지 못했어요"))
                                        }
                                    }
                                    return
                                }
                                if (
                                    submitted &&
                                    request.item.kind == LmsItemKind.ASSIGNMENT &&
                                    path == "/lms/class/report/stud/doListView.dunet" &&
                                    !assignmentActionSubmitted
                                ) {
                                    assignmentActionSubmitted = true
                                    view.evaluateJavascript("document.documentElement.outerHTML") { value ->
                                        val html = runCatching {
                                            JSONObject("{\"value\":$value}").getString("value")
                                        }.getOrNull()
                                        val action = html?.let {
                                            parser.resolveAssignmentListOnClick(request.item, it)
                                        }
                                        if (action == null) {
                                            onComplete(LmsRenderedPageResult.OfficialCoursePage)
                                            return@evaluateJavascript
                                        }
                                        val script =
                                            "(function(){if(typeof fncModifyReport!=='function')return 'missing';" +
                                                "$action;return 'submitted';})()"
                                        view.evaluateJavascript(script) { result ->
                                            if (result == "\"missing\"") {
                                                onComplete(LmsRenderedPageResult.OfficialCoursePage)
                                            }
                                        }
                                    }
                                    return
                                }
                                if (
                                    submitted &&
                                    request.item.kind == LmsItemKind.ASSIGNMENT &&
                                    path != "/lms/class/report/stud/doFormReport.dunet"
                                ) {
                                    return
                                }
                                if (submitted && path.startsWith("/lms/class/")) {
                                    view.evaluateJavascript("document.documentElement.outerHTML") { value ->
                                        val html = runCatching {
                                            JSONObject("{\"value\":$value}").getString("value")
                                        }.getOrNull()
                                        if (html.isNullOrBlank()) {
                                            onComplete(LmsRenderedPageResult.Failure("글 내용을 확인하지 못했어요"))
                                        } else {
                                            CookieManager.getInstance().flush()
                                            onComplete(LmsRenderedPageResult.Success(url, html))
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                webRequest: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (webRequest.isForMainFrame) {
                                    onComplete(LmsRenderedPageResult.Failure(error.description.toString()))
                                }
                            }
                        }
                        loadUrl(LMS_NATIVE_DETAIL_DASHBOARD_URL)
                    }
                },
                modifier = Modifier.size(1.dp),
                onRelease = { webView ->
                    webView.stopLoading()
                    webView.destroy()
                },
            )
        }
    }
}

internal fun LmsItemKind.officialContentType(): String? = when (this) {
    LmsItemKind.NOTICE -> "1"
    LmsItemKind.QUESTION -> "2"
    LmsItemKind.ASSIGNMENT -> "3"
    LmsItemKind.DISCUSSION -> "4"
    LmsItemKind.TEAM_PROJECT -> "5"
    LmsItemKind.QUIZ -> "6"
    LmsItemKind.EXAM -> "7"
    LmsItemKind.CONTENT -> "8"
    LmsItemKind.MATERIAL -> "9"
    LmsItemKind.OTHER -> null
}
