package com.example.dimanow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.example.dimanow.theme.DimaShapes
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dimanow.dorm.RollCallStore
import com.example.dimanow.live.GuidanceKind
import com.example.dimanow.live.LiveChipContent
import com.example.dimanow.live.LiveClassOrder
import com.example.dimanow.live.LiveDisplayOptions
import com.example.dimanow.live.NowBarAvailability
import com.example.dimanow.live.NowBarSupport
import com.example.dimanow.live.NotificationGuidanceMode
import com.example.dimanow.live.NotificationGuidancePolicy
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.DimaMotion
import com.example.dimanow.ui.motion.dimaContentSwap

/**
 * 설정's notification cards (D-140): 수업·셔틀 안내 — the 나우바 / 일반 알림 picture on top, the three live guidance rows
 * and the two Now Bar choices — and then 다른 알림 for the morning reminders and notices. Tapping a row's choice
 * redraws the picture with that row's guidance; changing ① or ② shows the class guidance, which they apply to.
 */
@Composable
fun NotificationGuidanceSettings(
    policy: NotificationGuidancePolicy,
    onModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
    modifier: Modifier = Modifier,
    academicReminders: Boolean = true,
    onAcademicRemindersChange: (Boolean) -> Unit = {},
    deadlineDigest: Boolean = true,
    onDeadlineDigestChange: (Boolean) -> Unit = {},
    outingReminders: Boolean = true,
    onOutingRemindersChange: (Boolean) -> Unit = {},
    noticeKeywordAlerts: Boolean = false,
    noticeKeywords: List<String> = emptyList(),
    onNoticeKeywordAlertsChange: (Boolean) -> Unit = {},
    onNoticeKeywordsChange: (List<String>) -> Unit = {},
    displayOptions: LiveDisplayOptions = LiveDisplayOptions(),
    onChipContentChange: (LiveChipContent) -> Unit = {},
    onClassOrderChange: (LiveClassOrder) -> Unit = {},
    nowBar: NowBarSupport = NowBarSupport.AVAILABLE,
    showNowBarHelp: Boolean = false,
    onShowNowBarGuide: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DimaLayout.sectionGap)) {
        LiveGuidanceCard(policy, onModeChange, displayOptions, onChipContentChange, onClassOrderChange, nowBar, showNowBarHelp, onShowNowBarGuide)
        SettingsCard {
            Text("다른 알림", style = MaterialTheme.typography.titleMedium)
            // D-107: 학사 일정은 실시간 안내가 아니라 아침 한 번의 일반 알림이라 나우바 선택지가 없다
            ReminderRow(
                title = "학사 일정",
                enabled = academicReminders,
                onChange = onAcademicRemindersChange,
                tag = "notification_academic",
                description = "수강신청 정정·시험 같은 기간이 시작하거나 끝나는 날 아침에 알려 줘요.",
            )
            // D-126(3): 오늘 마감을 아침 8시 30분에 한 번 모아 알린다
            ReminderRow(
                title = "과제 마감",
                enabled = deadlineDigest,
                onChange = onDeadlineDigestChange,
                tag = "notification_deadline",
                description = "오늘 마감인 과제·퀴즈를 아침 8시 30분에 모아 알려 줘요.",
            )
            // D-126(4): 카톡 점호 공지를 읽은 날에만, 엔터관 방향일 때 외출·외박 신청 마감 전에 알린다
            ReminderRow(
                title = "외출·외박 신청",
                enabled = outingReminders,
                onChange = onOutingRemindersChange,
                tag = "notification_outing",
                description = "점호 공지를 읽은 날, 신청 마감 1시간 전에 알려 줘요. 엔터관 방향일 때만 울려요.",
            )
            NoticeKeywordSetting(noticeKeywordAlerts, noticeKeywords, onNoticeKeywordAlertsChange, onNoticeKeywordsChange)
            KakaoRollCallSetting()
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(containerColor = DimaTheme.colors.card),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun LiveGuidanceCard(
    policy: NotificationGuidancePolicy,
    onModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
    displayOptions: LiveDisplayOptions,
    onChipContentChange: (LiveChipContent) -> Unit,
    onClassOrderChange: (LiveClassOrder) -> Unit,
    nowBar: NowBarSupport,
    showNowBarHelp: Boolean,
    onShowNowBarGuide: () -> Unit,
) {
    // The row last tapped is the one the picture shows; it starts at 수업 안내.
    var shownKind by rememberSaveable { mutableStateOf(GuidanceKind.CLASS) }
    SettingsCard {
        Text("수업·셔틀 안내", style = MaterialTheme.typography.titleMedium)
        GuidanceModePreview(
            kind = shownKind,
            mode = NowBarAvailability.effectiveMode(policy.modeFor(shownKind), nowBar),
            options = displayOptions,
            nowBar = nowBar,
        )
        val rows = listOf(
            Triple("수업 안내", GuidanceKind.CLASS, policy.classGuidance),
            Triple("교내 셔틀", GuidanceKind.CAMPUS_SHUTTLE, policy.campusShuttle),
            Triple("4402 강남행", GuidanceKind.BUS_4402, policy.bus4402),
        )
        rows.forEach { (title, kind, mode) ->
            GuidanceModeRow(
                title, kind, mode,
                onModeChange = { k, m -> shownKind = k; onModeChange(k, m) },
                nowBar = nowBar,
                onNowBarUnavailable = { k -> shownKind = k; onShowNowBarGuide() },
            )
        }
        // D-140: why 나우바 is dimmed before Android 16
        if (nowBar == NowBarSupport.UNSUPPORTED) {
            KoreanText(
                "이 휴대폰은 나우바를 쓸 수 없어 일반 알림으로 보내요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("now_bar_unavailable_note"),
            )
        }
        // D-143: the developer-option steps stay only as a fallback for a Samsung phone that does not draw the card
        if (showNowBarHelp && nowBar == NowBarSupport.AVAILABLE) {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onShowNowBarGuide,
                modifier = Modifier.heightIn(min = 48.dp).testTag("now_bar_show_guide"),
            ) { Text("나우바가 안 보이면") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MarkedChoiceLabel(1, "상단 알림 표시")
            SettingsChoice(
                options = listOf(LiveChipContent.COUNTDOWN to "남은 시간", LiveChipContent.CLASSROOM to "강의실"),
                selected = displayOptions.chipContent,
                onSelect = { shownKind = GuidanceKind.CLASS; onChipContentChange(it) },
                itemTag = { "live_chip_${it.name}" },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MarkedChoiceLabel(2, "잠금화면 첫 줄")
            SettingsChoice(
                options = listOf(LiveClassOrder.COURSE_FIRST to "수업명 먼저", LiveClassOrder.CLASSROOM_FIRST to "강의실 먼저"),
                selected = displayOptions.classOrder,
                onSelect = { shownKind = GuidanceKind.CLASS; onClassOrderChange(it) },
                itemTag = { "live_order_${it.name}" },
            )
        }
    }
}

/** A choice's label with the numbered mark it has in the picture; the two Now Bar choices apply to 수업 안내 only. */
@Composable
private fun MarkedChoiceLabel(marker: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PreviewMarker(marker)
        Text(text, style = MaterialTheme.typography.labelLarge)
        Text("수업 안내에만", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReminderRow(title: String, enabled: Boolean, onChange: (Boolean) -> Unit, tag: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        SettingsChoice(
            options = listOf(true to "일반 알림", false to "끔"),
            selected = enabled,
            onSelect = onChange,
            itemTag = { "${tag}_${if (it) "ON" else "OFF"}" },
        )
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * D-112: 카톡 점호 공지 읽기 follows Android's notification access for DIMA Now; this row shows whether it is on
 * and opens the system page that turns it on or off. The state is read again whenever the screen resumes.
 * D-137: back from that page, the status line turns over to the new one and the button's label cross-fades while the
 * button's width follows on the fast spatial spring.
 */
@Composable
private fun KakaoRollCallSetting() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(RollCallStore.accessGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = RollCallStore.accessGranted(context)
        if (!granted) RollCallStore.clear()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("kakao_roll_call_setting")) {
        Text("카톡 점호 공지", style = MaterialTheme.typography.labelLarge)
        AnimatedContent(
            targetState = granted,
            transitionSpec = { dimaContentSwap() },
            label = "kakao_roll_call_status",
        ) { on ->
            Text(
                if (on) {
                    "켜져 있어요. 카카오톡 알림 중 점호 공지만 읽어 기숙사 화면에 보여 주고, 공지 내용은 저장하지 않아요."
                } else {
                    "꺼져 있어요. 켜면 카카오톡 알림 중 점호 공지만 읽어 기숙사 화면에 오늘 점호 여부를 보여 줘요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            shapes = ButtonDefaults.shapes(),
            onClick = { RollCallStore.openAccessSettings(context) },
            modifier = Modifier.heightIn(min = 48.dp).testTag("kakao_roll_call_access"),
        ) {
            AnimatedContent(
                targetState = granted,
                transitionSpec = {
                    fadeIn(DimaMotion.effectsFast()) togetherWith fadeOut(DimaMotion.effectsFast()) using
                        SizeTransform(clip = false) { _, _ -> DimaMotion.spatialFast() }
                },
                label = "kakao_roll_call_access_label",
            ) { on -> Text(if (on) "설정에서 끄기" else "알림 접근 허용하기") }
        }
    }
}

@Composable
private fun GuidanceModeRow(
    title: String,
    kind: GuidanceKind,
    selected: NotificationGuidanceMode,
    onModeChange: (GuidanceKind, NotificationGuidanceMode) -> Unit,
    nowBar: NowBarSupport,
    onNowBarUnavailable: (GuidanceKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        // D-140: while the phone cannot show the Now Bar, 나우바 is dimmed and the guidance goes out as 일반 알림, which
        // is what shows as chosen; the stored choice stays, so 나우바 comes back by itself once it is turned on.
        val nowBarReady = nowBar == NowBarSupport.AVAILABLE
        SettingsChoice(
            options = listOf(
                NotificationGuidanceMode.LIVE_UPDATE to "나우바",
                NotificationGuidanceMode.STANDARD to "일반 알림",
                NotificationGuidanceMode.OFF to "끔",
            ),
            selected = NowBarAvailability.effectiveMode(selected, nowBar),
            onSelect = { mode ->
                if (mode == NotificationGuidanceMode.LIVE_UPDATE && !nowBarReady) onNowBarUnavailable(kind)
                else onModeChange(kind, mode)
            },
            itemTag = { "notification_mode_${kind.name}_${it.name}" },
            unavailable = if (nowBarReady) emptySet() else setOf(NotificationGuidanceMode.LIVE_UPDATE),
        )
    }
}

@Composable
fun TransitStopTestControls(
    testStopNumber: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("4402 정류장", style = MaterialTheme.typography.labelLarge)
        SettingsChoice(
            options = listOf(
                null to "선택 안 함",
                "34710" to "대학 셔틀 정류장",
                "33243" to "원룸촌 앞",
            ),
            selected = testStopNumber,
            onSelect = onChange,
        )
    }
}
