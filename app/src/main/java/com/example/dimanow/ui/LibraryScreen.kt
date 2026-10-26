package com.example.dimanow.ui

import androidx.compose.material.icons.filled.Campaign
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dimanow.school.LIBRARY_HOME_URL
import com.example.dimanow.school.LIBRARY_LOANS_SCRIPT
import com.example.dimanow.school.LIBRARY_LOANS_URL
import com.example.dimanow.school.LIBRARY_MY_LIBRARY_URL
import com.example.dimanow.school.LIBRARY_SEARCH_URL
import com.example.dimanow.school.LibraryBook
import com.example.dimanow.school.LibraryHours
import com.example.dimanow.school.LibraryStatus
import com.example.dimanow.school.LocalSchoolServiceBridge
import com.example.dimanow.school.SchoolReadCache
import com.example.dimanow.school.SchoolService
import com.example.dimanow.school.SchoolServiceResult
import com.example.dimanow.school.libraryAutoReadDue
import com.example.dimanow.school.libraryDueLabel
import com.example.dimanow.school.parseLibraryStatus
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.time.MinuteTicker
import com.example.dimanow.ui.motion.dimaContentReveal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * 도서관 (D-131), opened from 전체. The student's loans are read through the portal account when the page opens
 * (again after half an hour, from pull-to-refresh and the bar's refresh action): how many are out and overdue, then
 * each book nearest due first with `D-n · M/d 반납`, overdue and due-today books in the warning tone. Below, the
 * library's 이용 시간 as its front page shows them, and the ways on: the library app's 모바일 열람증, 대출·연기 on
 * the library's own page signed in inside the app (the app never renews by itself; the loans are read again when it
 * closes) and 자료 검색. The read stays in memory; only titles and due dates are kept on the device, for the
 * morning digest ([SchoolReadCache.setLibrary]).
 */
@Composable
internal fun LibraryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bridge = LocalSchoolServiceBridge.current
    val scope = rememberCoroutineScope()
    val read by SchoolReadCache.library.collectAsStateWithLifecycle()
    // D-132: 이용 시간 and the notices that change it, read without signing in, shown from the start.
    val publicInfo by com.example.dimanow.school.LibraryPublicSource.info.collectAsStateWithLifecycle()
    var publicLoading by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now(MinuteTicker.CAMPUS_ZONE)

    // D-135: the read on opening the page is the app's own, so it never signs another device out.
    fun readLoans(automatic: Boolean) {
        val service = bridge ?: return
        if (loading) return
        loading = true
        error = null
        scope.launch {
            when (val result = service.read(SchoolService.LIBRARY, LIBRARY_LOANS_URL, LIBRARY_LOANS_SCRIPT, "대출 현황", automatic)) {
                is SchoolServiceResult.Success -> runCatching { parseLibraryStatus(result.json) }
                    .onSuccess { SchoolReadCache.setLibrary(SchoolReadCache.LibraryRead(it, Instant.now())) }
                    .onFailure { error = "도서관의 대출 목록을 읽지 못했어요. 대출·연기에서 바로 확인할 수 있어요." }
                is SchoolServiceResult.Failure -> error = result.message
                SchoolServiceResult.Cancelled -> Unit
            }
            loading = false
        }
    }

    fun load() = readLoans(automatic = false)

    // The library's pages open signed in inside the app (D-108's school pages); in the browser they would ask again.
    fun openOnLibrary(url: String, title: String, readAfter: Boolean) {
        val service = bridge ?: return openUrl(context, url)
        openError = null
        scope.launch {
            when (val result = service.open(SchoolService.LIBRARY, url, title)) {
                is SchoolServiceResult.Failure -> openError = result.message
                // The student may have renewed there: read the due dates again, which needs no sign-in while the
                // session that read them lasts.
                SchoolServiceResult.Cancelled -> if (readAfter && SchoolReadCache.library.value != null) load()
                is SchoolServiceResult.Success -> Unit
            }
        }
    }

    fun loadPublic(force: Boolean) {
        if (publicLoading || !(force || com.example.dimanow.school.LibraryPublicSource.isDue(Instant.now()))) return
        publicLoading = true
        scope.launch {
            com.example.dimanow.school.LibraryPublicSource.refresh()
            publicLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPublic(force = false)
        if (libraryAutoReadDue(SchoolReadCache.library.value?.readAt, Instant.now())) readLoans(automatic = true)
    }

    ScreenScaffold(
        title = AppPage.LIBRARY.title,
        modifier = modifier,
        navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack, modifier = Modifier.testTag("close_library")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        listTag = "library_list",
        onRefresh = {
            loadPublic(force = true)
            if (bridge != null) load()
        },
        // D-134: the first read of 이용 시간 has its own loading line, so the pull indicator joins only a reload.
        refreshing = (loading && read != null) || (publicLoading && publicInfo != null),
        refreshLabel = "대출 현황 새로고침",
    ) {
        val shown = read
        // One item whose content crossfades and resizes when the first read finishes (as 장비 대여 does).
        val view = LibraryLoansView(
            phase = libraryLoansPhase(shown != null, loading, error != null, bridge != null),
            error = error,
            read = shown,
        )
        item(key = "library_loans") {
            AnimatedContent(
                targetState = view,
                transitionSpec = { dimaContentReveal() },
                contentKey = { it.phase },
                label = "library_loans",
            ) { state ->
                when (state.phase) {
                    LibraryLoansPhase.LOADING -> LoadingLine("도서관에서 대출 현황을 불러오는 중이에요", slowHint = SCHOOL_READ_SLOW_HINT)
                    LibraryLoansPhase.ERROR -> ErrorState(state.error.orEmpty(), onRetry = if (bridge != null) ::load else null)
                    // After the sign-in was closed: nothing is read until the student asks again.
                    LibraryLoansPhase.START -> EmptyState(
                        message = "포털 계정으로 로그인하면 대출 중인 책과 반납일을 볼 수 있어요",
                        supporting = "수업 탭과 같은 학교 포털 계정을 써요.",
                        actionLabel = "대출 현황 불러오기",
                        onAction = ::load,
                        modifier = Modifier.testTag("library_start"),
                    )
                    LibraryLoansPhase.READY -> state.read?.let { loaded ->
                        LibraryLoans(loaded, state.error, today, onRetry = ::load)
                    }
                    LibraryLoansPhase.NONE -> Spacer(Modifier.fillMaxWidth())
                }
            }
        }
        // D-132: always shown — the front page's hours (without signing in), or those the signed-in read found.
        item(key = "library_hours_header") { SettingsSectionHeader("이용 시간") }
        item(key = "library_hours") {
            val hours = publicInfo?.hours.orEmpty().ifEmpty { shown?.status?.hours.orEmpty() }
            if (hours.isEmpty() && publicLoading) {
                LoadingLine("도서관 이용 시간을 불러오는 중이에요")
            } else {
                LibraryHoursList(hours, onOpenHome = { openUrl(context, LIBRARY_HOME_URL) })
            }
        }
        // Vacations, exam weeks and holidays change the hours by notice, not on the front page.
        val notices = publicInfo?.notices.orEmpty()
        if (notices.isNotEmpty()) {
            item(key = "library_hours_notices_header") { SettingsSectionHeader("이용 시간 공지") }
            item(key = "library_hours_notices") {
                DimaSegmentedList(Modifier.testTag("library_hours_notices")) {
                    notices.forEachIndexed { index, notice ->
                        LibraryLinkRow(
                            icon = Icons.Default.Campaign,
                            title = notice.title,
                            supporting = notice.postedOn?.let { "${it.monthValue}월 ${it.dayOfMonth}일 게시" } ?: "도서관 공지",
                            index = index, count = notices.size, tag = "library_notice_$index",
                            external = true, externalLabel = "공지 열기",
                            onClick = { openUrl(context, notice.url) },
                        )
                    }
                }
            }
        }
        item(key = "library_links_header") { SettingsSectionHeader("바로가기") }
        item(key = "library_links") {
            DimaSegmentedList {
                // D-126(6): the library gate takes the library app's 모바일 열람증.
                LibraryLinkRow(
                    icon = Icons.Default.Badge,
                    title = "모바일 열람증",
                    supporting = "도서관 앱을 열어요. 열람증 탭을 눌러 게이트에 대요.",
                    index = 0, count = 3, tag = "library_card",
                    external = true, externalLabel = "도서관 앱 열기",
                    onClick = { openApp(context, LIBRARY_APP_PACKAGE) },
                )
                LibraryLinkRow(
                    icon = Icons.Default.EventRepeat,
                    title = "대출·연기",
                    supporting = "도서관 페이지에서 반납일을 연기하거나 예약을 확인해요.",
                    index = 1, count = 3, tag = "library_renew",
                    onClick = { openOnLibrary(LIBRARY_MY_LIBRARY_URL, "대출·연기", readAfter = true) },
                )
                LibraryLinkRow(
                    icon = Icons.Default.Search,
                    title = "자료 검색",
                    supporting = "도서관에 있는 책과 자료를 찾아요.",
                    index = 2, count = 3, tag = "library_search",
                    onClick = { openOnLibrary(LIBRARY_SEARCH_URL, "자료 검색", readAfter = false) },
                )
            }
        }
        openError?.let { message -> item(key = "library_open_error") { ErrorState(message) } }
    }
}

/** What the loans part of the page shows; the content changes with an animation only when this changes. */
internal enum class LibraryLoansPhase { LOADING, ERROR, START, READY, NONE }

/**
 * Pure choice of [LibraryLoansPhase]: a read in hand stays shown through a refresh and its failure; before one, a
 * read in progress, its failure, the sign-in prompt once the sign-in was closed ([canSignIn]: the school bridge
 * exists), or nothing without a bridge (previews).
 */
internal fun libraryLoansPhase(hasRead: Boolean, loading: Boolean, failed: Boolean, canSignIn: Boolean): LibraryLoansPhase = when {
    hasRead -> LibraryLoansPhase.READY
    loading -> LibraryLoansPhase.LOADING
    failed -> LibraryLoansPhase.ERROR
    canSignIn -> LibraryLoansPhase.START
    else -> LibraryLoansPhase.NONE
}

/** The loans part's content, carried whole so the content fading out keeps showing what it showed. */
private data class LibraryLoansView(
    val phase: LibraryLoansPhase,
    val error: String?,
    val read: SchoolReadCache.LibraryRead?,
)

@Composable
private fun LibraryLoans(read: SchoolReadCache.LibraryRead, error: String?, today: LocalDate, onRetry: () -> Unit) {
    val books = read.status.books
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A failed refresh keeps the list and opens its message above it.
        AnimatedContent(targetState = error, transitionSpec = { dimaContentReveal() }, label = "library_refresh_error") { message ->
            if (message != null) ErrorState(message, onRetry = onRetry) else Spacer(Modifier.fillMaxWidth())
        }
        LibraryLoanSummary(read.status, read.readAt, today)
        if (books.isNotEmpty()) {
            SettingsSectionHeader("반납할 책")
            DimaSegmentedList(Modifier.testTag("library_books")) {
                books.forEachIndexed { index, book -> LibraryBookRow(book, today, index, books.size) }
            }
        }
    }
}

/** How many books are out and overdue as two number tiles (as 기숙사's 상벌점, D-122), and when they were read. */
@Composable
private fun LibraryLoanSummary(status: LibraryStatus, readAt: Instant, today: LocalDate) {
    val overdue = status.overdue(today)
    val dueToday = status.books.count { it.dueOn == today }
    HomeContainerCard(Modifier.testTag("library_summary")) {
        HomeCardHeader(icon = Icons.Default.LocalLibrary, title = "대출 현황", showChevron = false)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("library_counts").semantics(mergeDescendants = true) {},
        ) {
            LibraryCountTile(
                "대출 중",
                status.loans,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.onSecondaryContainer,
                Modifier.weight(1f),
            )
            LibraryCountTile(
                "연체",
                overdue,
                if (overdue > 0) DimaTheme.colors.warningContainer else DimaTheme.colors.cardInner,
                if (overdue > 0) DimaTheme.colors.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f),
            )
        }
        when {
            overdue > 0 -> LibraryNotice("반납 예정일이 지난 책이 ${overdue}권 있어요. 도서관에 반납해 주세요.")
            dueToday > 0 -> LibraryNotice("오늘 반납할 책이 ${dueToday}권 있어요.")
            status.loans == 0 -> Text(
                "지금 대출 중인 책이 없어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("library_no_loans"),
            )
        }
        // The page named more loans than its rows gave: say so rather than show a short list as the whole.
        status.incompleteNote?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("library_incomplete"),
            )
        }
        Text(
            dormPointsReadAtLabel(readAt, Instant.now()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("library_read_at"),
        )
    }
}

@Composable
private fun LibraryCountTile(label: String, count: Int, container: Color, content: Color, modifier: Modifier) {
    Surface(modifier = modifier, shape = DimaShapes.Tile, color = container, contentColor = content) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("${count}권", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

/** A short warning inside the summary card, in the app's warning container (not the error roles, D-104). */
@Composable
private fun LibraryNotice(text: String) {
    Surface(
        shape = DimaShapes.Tile,
        color = DimaTheme.colors.warningContainer,
        contentColor = DimaTheme.colors.onWarningContainer,
        modifier = Modifier.fillMaxWidth().testTag("library_notice"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            KoreanText(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** One book: its title, `D-n · M/d 반납` (warning tone when overdue or due today) and when it was borrowed. */
@Composable
private fun LibraryBookRow(book: LibraryBook, today: LocalDate, index: Int, count: Int) {
    val urgent = book.needsReturn(today)
    val due = libraryDueLabel(book.dueOn, today)
    val details = listOfNotNull(
        book.loanedOn?.let { "${koreanDateLabel(it, today)} 대출" },
        when (book.renewable) {
            true -> "연기 신청 가능"
            false -> "연기 불가"
            null -> null
        },
    ).joinToString(" · ")
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape,
        color = DimaTheme.colors.card,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_book_$index")
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(book.title, due, details.ifBlank { null }).joinToString(", ")
            },
    ) {
        Column(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                due,
                style = MaterialTheme.typography.labelLarge,
                color = if (urgent) DimaTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (urgent) FontWeight.ExtraBold else null,
            )
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The library's 이용 시간 as read from its front page, or the way to its home page when none were found. */
@Composable
private fun LibraryHoursList(hours: List<LibraryHours>, onOpenHome: () -> Unit) {
    if (hours.isEmpty()) {
        EmptyState(
            message = "도서관 홈페이지에서 이용 시간을 확인할 수 있어요",
            actionLabel = "홈페이지 열기",
            onAction = onOpenHome,
            modifier = Modifier.testTag("library_hours_empty"),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DimaSegmentedList(Modifier.testTag("library_hours")) {
            hours.forEachIndexed { index, line ->
                Surface(
                    shape = ListItemDefaults.segmentedShapes(index = index, count = hours.size).shape,
                    color = DimaTheme.colors.card,
                    modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        contentDescription = "${line.place}, ${spokenHours(line.text)}"
                    },
                ) {
                    Row(
                        modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(line.place, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(line.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Text(
            "도서관 홈페이지에서 읽은 시간이에요. 방학·시험 기간과 연휴에는 아래 공지의 시간이 먼저예요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** `09:00 ~ 20:00` read aloud as `09:00부터 20:00까지`. */
internal fun spokenHours(text: String): String =
    if (text.contains(" ~ ")) text.replace(" ~ ", "부터 ") + "까지" else text

@Composable
private fun LibraryLinkRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    index: Int,
    count: Int,
    tag: String,
    onClick: () -> Unit,
    external: Boolean = false,
    externalLabel: String = "열기",
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        // The row says where it goes, which SegmentedListItem has no parameter for.
        modifier = Modifier.testTag(tag).semantics { onClick(label = if (external) externalLabel else "열기") { onClick(); true } },
        leadingContent = { DimaIconBadge(icon) },
        trailingContent = {
            Icon(
                if (external) Icons.AutoMirrored.Filled.OpenInNew else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        supportingContent = { KoreanText(supporting) },
        colors = dimaSegmentedListColors(),
    ) {
        Text(title)
    }
}
