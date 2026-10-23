package com.example.dimanow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.DimaMotion

/**
 * The 창의실습지원센터 rental notice (2026학년도 2학기, as the user gave it on 2026-10-23): 대여사유 작성 요령 and
 * 유의사항 in the app's voice. The school posts the notice again every term, so the app links to the notice
 * board's search, which lists the newest one first, rather than to one term's post.
 */
internal object EquipmentRentalNotice {
    const val SEARCH_URL =
        "https://www.dima.ac.kr/?p=111&searchText=%EA%B8%B0%EC%9E%90%EC%9E%AC+%EB%B0%8F+%EC%8B%A4%EC%8A%B5%EC%8B%A4&page=1"

    val reasonSteps = listOf(
        "소속(프로젝트)명과 대여 사유를 자세히 적어 주세요.",
        "아래처럼 쓰면 돼요. 예시에 없는 사유로도 신청할 수 있어요.",
        "사용 목적을 정확히 알 수 없으면 신청이 반려될 수 있어요. 프로젝트 이름을 빠뜨리거나 줄임말을 쓰지 마세요.",
    )

    val reasonExamples = listOf(
        "000동아리 00스터디를 위해 대여합니다.",
        "00계열/과 00수업 과제를 위해 대여합니다.",
        "000공연/행사 중계를 위해 대여합니다.",
        "DSV-50 신청 시: EOS 200Dii 카메라 거치를 위해 신청합니다.",
    )

    val cautions = listOf(
        "가" to "장비를 받으면 그 자리에서 구성품과 상태를 확인하고 가져가세요. 받자마자 문제를 발견하면 조교에게 말해 주세요.",
        "나" to "장비를 받은 뒤부터 다음 대여자가 가져가기 전까지 발견된 문제는 대여자가 모두 배상하는 것이 원칙이에요.",
        "다" to "수령과 반납은 본인이 직접 하는 것이 원칙이에요.",
        "라" to "대여 당일에 장비를 받지 않으면 대여가 취소돼요.",
        "마" to "같은 장비를 2개 이상 신청하면 승인되더라도 대여할 때 중복이 확인되면 모두 취소돼요.",
        "바" to "교내외 행사나 장비 파손으로 기자재와 실습실 대여가 제한되거나 바뀔 수 있어요.",
    )
}

/**
 * 대여 안내 at the top of 장비 대여: three points students most often miss, the full notice under 자세히 보기,
 * and the way to apply. The card holds its own controls and is not itself a tap target (D-102).
 */
@Composable
internal fun EquipmentGuideCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    onApply: (() -> Unit)?,
    onOpenNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeContainerCard(modifier.testTag("equipment_guide")) {
        HomeCardHeader(icon = Icons.Default.Info, title = "대여 안내", showChevron = false)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GuidePoint(Icons.Default.EditNote, "대여 사유에 소속(프로젝트)명과 사용 목적을 자세히 적어요.")
            GuidePoint(Icons.Default.Person, "수령과 반납은 본인이 직접 해요. 대여 당일에 받지 않으면 취소돼요.")
            GuidePoint(Icons.Default.Inventory2, "받자마자 구성품과 상태를 확인해요. 그 뒤 발견된 문제는 대여자가 배상해요.")
        }
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = DimaMotion.spatialDefault(),
            label = "equipment_guide_chevron",
        )
        TextButton(
            shapes = ButtonDefaults.shapes(),
            onClick = onToggle,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("equipment_guide_more")
                .semantics {
                    stateDescription = if (expanded) "펼쳐짐" else "접힘"
                    if (expanded) collapse { onToggle(); true } else expand { onToggle(); true }
                },
        ) {
            Text(if (expanded) "접기" else "자세히 보기")
            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(rotation))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(DimaMotion.spatialDefault()) + fadeIn(DimaMotion.effectsDefault()),
            exit = shrinkVertically(DimaMotion.spatialDefault()) + fadeOut(DimaMotion.effectsFast()),
        ) {
            EquipmentNoticeDetails(onOpenNotice)
        }
        if (onApply != null) {
            FilledTonalButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("equipment_open_dimac"),
            ) { Text("DIMA C에서 신청하기") }
        }
    }
}

@Composable
private fun GuidePoint(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EquipmentNoticeDetails(onOpenNotice: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("equipment_guide_details"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NoticeHeading("대여 사유 작성 요령")
        EquipmentRentalNotice.reasonSteps.forEachIndexed { index, step ->
            NoticeLine(marker = "${index + 1})", text = step)
            if (index == 1) {
                Surface(
                    shape = DimaShapes.Tile,
                    color = DimaTheme.colors.cardInner,
                    modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("예시", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        EquipmentRentalNotice.reasonExamples.forEach { example ->
                            Text(example, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        NoticeHeading("유의사항")
        EquipmentRentalNotice.cautions.forEach { (marker, text) -> NoticeLine(marker = "$marker)", text = text) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "공지는 학기마다 새로 올라와요. 수령·반납 시간과 신청 일정은 이번 학기 공지에서 확인해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onOpenNotice,
                modifier = Modifier.heightIn(min = 48.dp).testTag("equipment_notice_open"),
            ) {
                Text("학기별 대여 공지 보기")
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            }
        }
    }
}

@Composable
private fun NoticeHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun NoticeLine(marker: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
