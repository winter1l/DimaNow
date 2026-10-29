package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.example.dimanow.dorm.DormHours
import com.example.dimanow.dorm.OutingRules
import com.example.dimanow.dorm.OutingWarning
import com.example.dimanow.dorm.RollCallNotice
import com.example.dimanow.dorm.RollCallRules
import com.example.dimanow.dorm.RollCallStatus
import com.example.dimanow.school.INTRANET_DORM_OUTING_URL
import com.example.dimanow.school.LocalSchoolServiceBridge
import com.example.dimanow.school.SchoolService
import com.example.dimanow.school.SchoolServiceResult
import com.example.dimanow.sync.ServiceCalendar
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.dimaCollapseOut
import com.example.dimanow.ui.motion.dimaContentReveal
import com.example.dimanow.ui.motion.dimaExpandIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/** Today's 점호 for the 기숙사 page and Home (D-112, D-122): announcements first, then the usual Tuesday rule. */
internal fun todayRollCall(date: LocalDate, notices: List<RollCallNotice>, calendar: ServiceCalendar): RollCallStatus =
    RollCallRules.status(date, notices, calendar.holidays[date], calendar.examOn(date)?.let(::examName))

internal fun examName(exam: ServiceCalendar.Exam): String = when (exam) {
    ServiceCalendar.Exam.MIDTERM -> "중간고사"
    ServiceCalendar.Exam.FINAL -> "기말고사"
}

/** True when 점호 is (or is expected to be) held today. */
internal fun RollCallStatus.isRollCallDay(): Boolean =
    this is RollCallStatus.Scheduled || (this is RollCallStatus.Announced && notice.held)

/** Home shows the 점호 card to students who return to 엔터관 (예인관 residents) on a day the rule or a notice names (D-122). */
internal fun showsHomeRollCall(homeBaseIsDorm: Boolean, rollCall: RollCallStatus): Boolean =
    homeBaseIsDorm && (rollCall is RollCallStatus.Scheduled || rollCall is RollCallStatus.Announced)

/**
 * Opens 외출·외박 신청 inside the app (D-108), asking first when it may not count (D-119). Shared by the 기숙사 page
 * and Home's 점호 card; [request] reads the clock when tapped, and the warning dialog is drawn where this is
 * remembered.
 */
internal class OutingLauncher {
    internal var warning by mutableStateOf<Pair<OutingWarning, LocalTime>?>(null)
    var error by mutableStateOf<String?>(null)
        internal set
    internal var onRequest: () -> Unit = {}
    fun request() = onRequest()
}

@Composable
internal fun rememberOutingLauncher(rollCall: RollCallStatus): OutingLauncher {
    val context = LocalContext.current
    val bridge = LocalSchoolServiceBridge.current
    val scope = rememberCoroutineScope()
    val launcher = remember { OutingLauncher() }
    val currentRollCall by rememberUpdatedState(rollCall)
    fun open() {
        val service = bridge ?: return openUrl(context, INTRANET_DORM_OUTING_URL)
        launcher.error = null
        scope.launch {
            val result = service.open(SchoolService.INTRANET, INTRANET_DORM_OUTING_URL, "외출·외박 신청")
            if (result is SchoolServiceResult.Failure) launcher.error = result.message
        }
    }
    launcher.onRequest = {
        val time = ZonedDateTime.now(DormHours.CAMPUS_ZONE).toLocalTime()
        val warning = OutingRules.warning(time, currentRollCall)
        if (warning == null) open() else launcher.warning = warning to time
    }
    launcher.warning?.let { (warning, time) ->
        OutingWarningDialog(
            warning = warning,
            now = time,
            onOpen = { launcher.warning = null; open() },
            onDismiss = { launcher.warning = null },
        )
    }
    return launcher
}

/** A decorative Material shape with an icon, the 기숙사 cards' mark (D-122). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DormMark(icon: ImageVector, polygon: RoundedPolygon, container: Color, content: Color, size: Dp = 52.dp) {
    val shape = polygon.toShape()
    Box(
        modifier = Modifier.size(size).clip(shape).background(container).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.48f))
    }
}

/**
 * A small status pill: quiet by default, amber when it warns. D-137: when the door closes or opens on a minute tick
 * the tone eases on the default effects spring instead of switching in one frame.
 */
@Composable
internal fun DormChip(icon: ImageVector, text: String, warning: Boolean = false, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        if (warning) DimaTheme.colors.warningContainer else DimaTheme.colors.cardInner,
        DimaMotion.effectsDefault(),
        label = "dorm_chip_container",
    )
    val content by animateColorAsState(
        if (warning) DimaTheme.colors.onWarningContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        DimaMotion.effectsDefault(),
        label = "dorm_chip_content",
    )
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The front door's state as a chip line (D-112). */
internal fun doorText(time: LocalTime): String =
    if (DormHours.doorOpen(time)) "출입문 열림 · 02:00 닫힘" else "출입문 닫힘 · 06:00 열림"

/**
 * 오늘 on the 기숙사 page (D-113, simplified by D-122): a shape mark (a scalloped moon on a 점호 day, a calm square
 * otherwise), the headline, one line of detail, and chips for the door and a passed deadline.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DormTodayCard(rollCall: RollCallStatus, time: LocalTime, kakaoAccess: Boolean, onAskKakao: () -> Unit) {
    val (headline, detail) = rollCallText(rollCall)
    val rollCallDay = rollCall.isRollCallDay()
    val pastDeadline = OutingRules.warning(time, rollCall) is OutingWarning.AfterRollCallDeadline
    HomeContainerCard(Modifier.testTag("dorm_today")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (rollCallDay) {
                DormMark(Icons.Default.Bedtime, MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            } else {
                DormMark(Icons.Default.EventAvailable, MaterialShapes.Cookie4Sided, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f).testTag("dorm_roll_call"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("오늘", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(headline, style = MaterialTheme.typography.titleLarge)
            }
        }
        detail?.let { KoreanText(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // D-137: what comes and goes here folds with its gap inside, so the card grows and shrinks instead of jumping:
        // each chip carries the 8dp under it (the flow row drops the last one), and the 점호 prompt its 12dp above it.
        Column {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.withoutTrailingGap(DORM_CHIP_GAP),
            ) {
                DormChip(
                    Icons.Default.DoorFront,
                    doorText(time),
                    warning = !DormHours.doorOpen(time),
                    modifier = Modifier.padding(bottom = DORM_CHIP_GAP).testTag("dorm_door"),
                )
                // On a phone the chip wraps to a line of its own; it unfolds rather than pops, so the card does not jump.
                AnimatedVisibility(visible = pastDeadline, enter = dimaExpandIn(), exit = dimaCollapseOut(), label = "dorm_past_deadline") {
                    DormChip(Icons.Default.Schedule, "외출·외박 신청 마감 지남", warning = true, modifier = Modifier.padding(bottom = DORM_CHIP_GAP))
                }
            }
            // Granting notification access folds the prompt away when the student comes back from the settings.
            AnimatedVisibility(visible = !kakaoAccess, enter = dimaExpandIn(), exit = dimaCollapseOut(), label = "dorm_allow_kakao") {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onAskKakao,
                    modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp).testTag("dorm_allow_kakao"),
                ) { Text("카톡 공지로 점호 확인하기") }
            }
        }
    }
}

/** Between the 기숙사 chips, across and between lines. */
private val DORM_CHIP_GAP = 8.dp

/**
 * Home's 점호 card for 엔터관 residents (D-122): what tonight's 점호 is, a chip once the outing deadline has passed,
 * and 외출·외박 신청 right there (with the same warning as the 기숙사 page), plus a way into 기숙사.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeRollCallCard(rollCall: RollCallStatus, time: LocalTime, onOpenDorm: () -> Unit) {
    val launcher = rememberOutingLauncher(rollCall)
    val (headline, detail) = rollCallText(rollCall)
    val pastDeadline = OutingRules.warning(time, rollCall) is OutingWarning.AfterRollCallDeadline
    // D-137: the late chip and a failed 외출·외박 신청 open in place with their 12dp gap inside, so the card grows into
    // them instead of the buttons jumping.
    HomeContainerCard(Modifier.testTag("home_roll_call")) {
        HomeCardHeader(icon = Icons.Default.Bedtime, title = "기숙사 점호", showChevron = false)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (rollCall.isRollCallDay()) {
                    DormMark(Icons.Default.Bedtime, MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, size = 44.dp)
                } else {
                    DormMark(Icons.Default.EventAvailable, MaterialShapes.Cookie4Sided, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 44.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(headline, style = MaterialTheme.typography.titleMedium)
                    detail?.let { KoreanText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            AnimatedVisibility(visible = pastDeadline, enter = dimaExpandIn(), exit = dimaCollapseOut(), label = "home_roll_call_late") {
                DormChip(
                    Icons.Default.Schedule,
                    "외출·외박 신청 마감이 지났어요",
                    warning = true,
                    modifier = Modifier.padding(top = 12.dp).testTag("home_roll_call_late"),
                )
            }
        }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = launcher::request,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("home_outing_open"),
                ) {
                    Icon(Icons.Default.Luggage, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("외출·외박 신청", modifier = Modifier.padding(start = 8.dp))
                }
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onOpenDorm, modifier = Modifier.testTag("home_open_dorm")) { Text("기숙사") }
            }
            AnimatedContent(targetState = launcher.error, transitionSpec = { dimaContentReveal() }, label = "home_outing_error") { message ->
                if (message != null) {
                    ErrorState(message, modifier = Modifier.padding(top = 12.dp), contained = false)
                } else {
                    Spacer(Modifier.fillMaxWidth())
                }
            }
        }
    }
}
