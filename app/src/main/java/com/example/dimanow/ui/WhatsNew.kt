package com.example.dimanow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.dimanow.theme.DimaShapes

/** One line of a version's changes, as the student would notice it. */
internal data class WhatsNewEntry(val title: String, val text: String)

/** What this build changed (D-126(7)); shown once after an update, never on a fresh install. */
internal val WHATS_NEW_ENTRIES = listOf(
    WhatsNewEntry("과제 마감 알림", "오늘 마감인 과제와 퀴즈를 아침 8시 30분에 한 번 모아 알려 줘요."),
    WhatsNewEntry("점호 날 외출·외박 알림", "점호가 있다는 카톡 공지를 읽은 날, 신청 마감 1시간 전에 알려 줘요. 누르면 바로 신청 화면이 열려요."),
    WhatsNewEntry("도서관", "전체 탭의 도서관에서 대출 중인 책과 반납일, 이용 시간을 봐요. 반납일 아침에는 알림으로 알려 줘요."),
    WhatsNewEntry("캠퍼스 지도", "전체 탭의 캠퍼스 지도에서 4402 정류장과 건물 위치, 내 위치까지의 거리를 볼 수 있어요."),
    WhatsNewEntry("성적 보기", "성적 열람 기간에는 학사 일정에서 바로 인트라넷을 열 수 있어요."),
    WhatsNewEntry("내 홈 만들기", "홈 맨 아래 '홈 편집'에서 카드 순서를 바꾸거나 숨기고, 자주 쓰는 바로가기를 고를 수 있어요."),
    WhatsNewEntry("공지 키워드 알림", "설정에서 '장학', '수강신청' 같은 단어를 정하면 그 단어가 든 새 학교 공지를 알려 줘요."),
    WhatsNewEntry("앱 바로가기와 빠른 설정", "앱 아이콘을 길게 누르면 셔틀·식단·외출·외박 신청·모바일 열람증이 바로 열려요. 빠른 설정에 '다음 셔틀' 타일도 추가할 수 있어요."),
    WhatsNewEntry("캘린더로 내보내기", "시간표 화면에서 수업과 학사 일정을 캘린더 파일로 공유할 수 있어요."),
    WhatsNewEntry("멀리 있는 마감도 홈에", "영상처럼 마감이 먼 항목도 홈의 수업 카드에 보여요."),
    WhatsNewEntry("한 줄로 보는 셔틀 시간표", "셔틀과 4402 시간표가 한 줄로 바뀌었어요. 넘겨 보다가 '지금'을 누르면 다음 차로 돌아와요."),
    WhatsNewEntry("주말에는 다음 주 식단", "토요일과 일요일에는 식단 탭이 다음 주를 보여 줘요."),
    WhatsNewEntry("모바일 열람증·헤이영 바로가기", "전체 탭의 바로가기에서 도서관 앱과 헤이영을 바로 열 수 있어요."),
)

/** Whether Home offers this build's changes: after an update only, until the student closes the card. */
internal fun showsWhatsNew(seenVersionCode: Int?, currentVersionCode: Int, onboardingCompleted: Boolean): Boolean =
    onboardingCompleted && (seenVersionCode == null || seenVersionCode < currentVersionCode)

/**
 * The what's-new card at the top of Home (D-126(7)). Like the update card (D-102) it waits in the feed instead of
 * covering the app at launch; 자세히 보기 opens the list in a bottom sheet and 닫기 retires it for this version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeWhatsNewNotice(versionName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("home_whats_new"),
        shape = DimaShapes.Card,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(22.dp))
                Text("$versionName 버전에서 달라진 점", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(
                WHATS_NEW_ENTRIES.take(2).joinToString(", ") { it.title } + " 등이 새로 생겼어요.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss, modifier = Modifier.testTag("home_whats_new_dismiss")) { Text("닫기") }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { showSheet = true }, modifier = Modifier.testTag("home_whats_new_open")) {
                    Text("자세히 보기")
                }
            }
        }
    }
    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding()
                    .testTag("whats_new_sheet"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("$versionName 버전에서 달라진 점", style = MaterialTheme.typography.titleLarge)
                WHATS_NEW_ENTRIES.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall)
                        Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        showSheet = false
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("whats_new_done"),
                ) { Text("확인했어요") }
            }
        }
    }
}
