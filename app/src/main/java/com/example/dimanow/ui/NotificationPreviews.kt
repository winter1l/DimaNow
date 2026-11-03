package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dimanow.R
import com.example.dimanow.domain.ClassContent
import com.example.dimanow.domain.CountdownMeaning
import com.example.dimanow.domain.GuidanceKind
import com.example.dimanow.domain.GuidancePhase
import com.example.dimanow.domain.GuidanceSnapshot
import com.example.dimanow.domain.ShuttleLine
import com.example.dimanow.live.LiveDisplayOptions
import com.example.dimanow.live.LiveSurfaceController
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NowBarSupport
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.dimaTextRoll
import java.time.Instant

/*
 * D-140: the picture at the top of 설정's 수업·셔틀 안내 card. 나우바 and 일반 알림 sit side by side as two small phone
 * screens drawn with the guidance of the row last tapped (수업, 교내 셔틀 or 4402); the one that row is set to is framed
 * and checked, the other dimmed, and 끔 dims both. Their words come from the function the notification itself uses
 * ([LiveSurfaceController.notificationText]) on one example per kind, so a picture cannot drift from the phone. The
 * shapes follow One UI as seen on the user's phone on 2026-10-29 (a chip beside the clock, a dark pill on the lock
 * screen, a card in the notification list), drawn with theme roles only.
 */

private val ExampleClass = GuidanceSnapshot(
    classContent = ClassContent(
        title = "13:00 · 방송시스템전기",
        detail = "시작까지 12분 · 덕성관 510-1",
        startTime = "13:00",
        courseName = "방송시스템전기",
        room = "덕성관 510-1",
        remainingText = "시작까지 12분",
        minutesUntilStart = 12,
    ),
    shuttleLines = emptyList(),
    phase = GuidancePhase.BEFORE_CLASS,
    countdownMeaning = CountdownMeaning.CLASS_START,
    kind = GuidanceKind.CLASS,
)

private val ExampleCampusShuttle = GuidanceSnapshot(
    classContent = null,
    shuttleLines = listOf(ShuttleLine(text = "본관  8분, 23분", destination = "엔터관행", minutes = 8, origin = "본관", followingMinutes = 23)),
    phase = GuidancePhase.RETURN,
    // 12:56 at the campus, eight minutes after the pictures' 12:48
    countdownTarget = Instant.parse("2026-10-29T03:56:00Z"),
    countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
    kind = GuidanceKind.CAMPUS_SHUTTLE,
)

private val ExampleBus4402 = GuidanceSnapshot(
    classContent = null,
    shuttleLines = listOf(ShuttleLine(text = "4402 · 13:05 출발", destination = "강남행", minutes = 17, origin = "대학 셔틀 정류장")),
    phase = GuidancePhase.TRANSIT,
    countdownMeaning = CountdownMeaning.SHUTTLE_DEPARTURE,
    kind = GuidanceKind.BUS_4402,
)

private const val ExampleClock = "12:48"

private fun exampleFor(kind: GuidanceKind) = when (kind) {
    GuidanceKind.CLASS -> ExampleClass
    GuidanceKind.CAMPUS_SHUTTLE -> ExampleCampusShuttle
    GuidanceKind.BUS_4402 -> ExampleBus4402
}

/** The example of [kind] as the notification would word it under [options]: chip, first line, the line under it. */
internal data class GuidancePreviewText(val chip: String, val title: String, val detail: String)

internal fun guidancePreviewText(kind: GuidanceKind, options: LiveDisplayOptions): GuidancePreviewText {
    val text = LiveSurfaceController.notificationText(exampleFor(kind), options, appName = "DIMA Now")
    return GuidancePreviewText(text.chip.orEmpty(), text.title, text.detail.lineSequence().firstOrNull().orEmpty())
}

/**
 * 나우바 beside 일반 알림 for [kind], with [mode] (what the row is set to, already 일반 알림 when the phone cannot show
 * the Now Bar) framed. For 수업 the numbered marks tie the chip (①, also the pill's second line) and the pill's first
 * line (②) to the two choices under the rows.
 */
@Composable
internal fun GuidanceModePreview(
    kind: GuidanceKind,
    mode: NotificationGuidanceMode,
    options: LiveDisplayOptions,
    nowBar: NowBarSupport,
    modifier: Modifier = Modifier,
) {
    val text = remember(kind, options) { guidancePreviewText(kind, options) }
    val icon = LiveSurfaceController.smallIcon(exampleFor(kind))
    val marks = kind == GuidanceKind.CLASS
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("guidance_mode_preview"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeTile(
            label = "나우바",
            caption = if (nowBar == NowBarSupport.AVAILABLE) "잠금화면·상단 · 1분마다 갱신" else "이 휴대폰은 못 써요",
            selected = mode == NotificationGuidanceMode.LIVE_UPDATE,
            description = "나우바: 상단 알약 ${text.chip}, 잠금화면 나우바 ${text.title}, ${text.chip}",
            modifier = Modifier.weight(1f).testTag("mode_preview_LIVE_UPDATE"),
        ) {
            StatusRow(icon, chip = text.chip, marker = if (marks) 1 else null)
            NowBarPill(text.title, text.chip, titleMarker = if (marks) 2 else null)
        }
        ModeTile(
            label = "일반 알림",
            caption = "알림 목록에만 · 갱신 없음",
            selected = mode == NotificationGuidanceMode.STANDARD,
            description = "일반 알림: 상단에는 아이콘만, 알림 목록에 ${text.title}, ${text.detail}",
            modifier = Modifier.weight(1f).testTag("mode_preview_STANDARD"),
        ) {
            StatusRow(icon, chip = null)
            ListCard(text.title, text.detail)
        }
    }
}

/** A numbered dot that ties a part of the picture to the choice that changes it; screen readers skip it. */
@Composable
internal fun PreviewMarker(number: Int, modifier: Modifier = Modifier, small: Boolean = false) {
    val size = if (small) 14.dp else 18.dp
    Box(
        modifier = modifier
            .sizeIn(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary)
            .padding(horizontal = if (small) 3.dp else 4.dp)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$number",
            style = if (small) MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f)
            else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

/** One mode: its name (checked when chosen), a small phone screen framed when chosen, and one short caption. */
@Composable
private fun ModeTile(
    label: String,
    caption: String,
    selected: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    screen: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val frame by animateColorAsState(if (selected) scheme.primary else Color.Transparent, DimaMotion.effectsDefault(), label = "mode_tile_frame")
    val shown by animateFloatAsState(if (selected) 1f else 0.55f, DimaMotion.effectsDefault(), label = "mode_tile_alpha")
    Column(
        modifier.fillMaxHeight().semantics { contentDescription = if (selected) "$description. 선택됨" else description },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.primary else scheme.onSurfaceVariant)
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(DimaShapes.Tile)
                .border(2.dp, frame, DimaShapes.Tile)
                .background(DimaTheme.colors.cardInner)
                .clearAndSetSemantics {}
                .padding(8.dp)
                .alpha(shown),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { screen() }
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The clock and, for 나우바, the coloured chip beside it; 일반 알림 leaves only the small icon there. */
@Composable
private fun StatusRow(icon: Int, chip: String?, marker: Int? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(ExampleClock, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface)
        if (chip != null) {
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(painterResource(icon), null, Modifier.size(10.dp), tint = scheme.onPrimary)
                RollingLine(chip, MaterialTheme.typography.labelSmall, scheme.onPrimary)
            }
            marker?.let { PreviewMarker(it, small = true) }
        } else {
            Icon(painterResource(icon), null, Modifier.size(12.dp), tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * The lock-screen Now Bar: a dark pill with the app mark, the first line and the chip's words under it. One UI draws it
 * dark in both themes, so the dark theme takes its darkest surface instead of the light inverse surface.
 */
@Composable
private fun NowBarPill(title: String, line: String, titleMarker: Int? = null) {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.surface.luminance() < 0.5f
    val pill = if (darkTheme) scheme.surfaceContainerLowest else scheme.inverseSurface
    val onPill = if (darkTheme) scheme.onSurface else scheme.inverseOnSurface
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(pill).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppMark(22.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                RollingLine(title, MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), onPill, Modifier.weight(1f, fill = false))
                titleMarker?.let { PreviewMarker(it, small = true) }
            }
            RollingLine(line, MaterialTheme.typography.labelSmall, onPill)
        }
    }
}

/** A card in the notification list, which is all 일반 알림 shows. */
@Composable
private fun ListCard(title: String, detail: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(DimaShapes.Small).background(DimaTheme.colors.card).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AppMark(20.dp)
        Column(Modifier.weight(1f)) {
            RollingLine(title, MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), scheme.onSurface)
            RollingLine(detail, MaterialTheme.typography.labelSmall, scheme.onSurfaceVariant)
        }
    }
}

/** The launcher icon as One UI rounds it in the Now Bar and the notification list: its tile fills the circle. */
@Composable
private fun AppMark(size: Dp) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .graphicsLayer { scaleX = AppMarkZoom; scaleY = AppMarkZoom },
    )
}

/** The adaptive foreground draws its tile on about 69% of the canvas; this zoom lets the tile fill the circle. */
private const val AppMarkZoom = 1.45f

/** One line that rolls over when its words change, cut with an ellipsis like the system cuts it. */
@Composable
private fun RollingLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = { dimaTextRoll() },
        label = "notification_preview_line",
    ) { value ->
        Text(value, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
