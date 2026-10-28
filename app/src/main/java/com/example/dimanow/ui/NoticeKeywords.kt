package com.example.dimanow.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.rememberCoroutineScope
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.ui.motion.DimaMotion
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.dimanow.data.DEFAULT_NOTICE_KEYWORDS

/** At most this many keywords, each this long: enough for interests, short enough to read at a glance. */
internal const val MAX_NOTICE_KEYWORDS = 10
internal const val MAX_NOTICE_KEYWORD_LENGTH = 20

/** A typed keyword as it is kept: trimmed, length-capped, and null when empty or already listed. */
internal fun cleanNoticeKeyword(typed: String, existing: List<String>): String? {
    val keyword = typed.trim().take(MAX_NOTICE_KEYWORD_LENGTH)
    if (keyword.isEmpty() || existing.size >= MAX_NOTICE_KEYWORDS) return null
    return keyword.takeUnless { candidate -> existing.any { it.equals(candidate, ignoreCase = true) } }
}

/** D-128(3): the Settings row for notice keyword alerts — on/off, then the keywords that can be removed or added. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoticeKeywordSetting(
    enabled: Boolean,
    keywords: List<String>,
    onEnabledChange: (Boolean) -> Unit,
    onKeywordsChange: (List<String>) -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    fun add() {
        cleanNoticeKeyword(typed, keywords)?.let { onKeywordsChange(keywords + it) }
        typed = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("notice_keyword_setting")) {
        Text("공지 키워드", style = MaterialTheme.typography.labelLarge)
        SettingsChoice(
            options = listOf(true to "일반 알림", false to "끔"),
            selected = enabled,
            onSelect = onEnabledChange,
            itemTag = { "notification_notice_${if (it) "ON" else "OFF"}" },
        )
        Text(
            "새 학교 공지 제목에 이 단어가 있으면 알려 줘요. 키워드는 이 기기에만 저장돼요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keywords.forEach { keyword ->
                    InputChip(
                        selected = false,
                        onClick = { onKeywordsChange(keywords - keyword) },
                        label = { Text(keyword) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "$keyword 빼기", modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            if (keywords.size < MAX_NOTICE_KEYWORDS) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(MAX_NOTICE_KEYWORD_LENGTH) },
                        label = { Text("키워드 추가") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f).testTag("notice_keyword_input"),
                    )
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = ::add, enabled = typed.isNotBlank()) { Text("추가") }
                }
            }
        }
    }
}

/**
 * D-128(3): offered once, after the student first opens 학교 공지 from Home and comes back — not at launch (D-102).
 * The sample keywords start chosen; 켜기 turns alerts on with the chosen ones, 나중에 never asks again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NoticeKeywordSuggestionSheet(onEnable: (List<String>) -> Unit, onLater: () -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(DEFAULT_NOTICE_KEYWORDS) }
    // D-137: the buttons slide the sheet down before the caller removes it, as the scrim, Back and a swipe do; removing
    // it at once made it vanish.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onLater, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding()
                .testTag("notice_keyword_suggestion"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("관심 있는 공지를 알려 드릴까요?", style = MaterialTheme.typography.titleLarge)
            Text(
                "고른 단어가 들어간 새 학교 공지가 올라오면 알림으로 알려 줘요. 설정에서 언제든 바꿀 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DEFAULT_NOTICE_KEYWORDS.forEach { keyword ->
                    NoticeKeywordChip(
                        keyword = keyword,
                        selected = keyword in chosen,
                        onClick = { chosen = if (keyword in chosen) chosen - keyword else chosen + keyword },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onLater() } },
                    modifier = Modifier.testTag("notice_keyword_later"),
                ) { Text("나중에") }
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        val keywords = chosen
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onEnable(keywords) }
                    },
                    enabled = chosen.isNotEmpty(),
                    modifier = Modifier.testTag("notice_keyword_enable"),
                ) { Text("알림 켜기") }
            }
        }
    }
}

/**
 * A suggested keyword (D-137): the selection follows the app's chip motion (the day cells), the corners rounding to a
 * pill on the theme's fast spatial spring and tightening under the finger, the fill and label changing on the fast
 * effects spring instead of switching in one frame.
 */
@Composable
private fun NoticeKeywordChip(keyword: String, selected: Boolean, onClick: () -> Unit) {
    val defaults = FilterChipDefaults.filterChipColors()
    val container by animateColorAsState(
        if (selected) defaults.selectedContainerColor else defaults.containerColor,
        DimaMotion.effectsFast(),
        label = "notice_keyword_container",
    )
    val label by animateColorAsState(
        if (selected) defaults.selectedLabelColor else defaults.labelColor,
        DimaMotion.effectsFast(),
        label = "notice_keyword_label",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(keyword) },
        shapes = FilterChipDefaults.shapes(
            shape = FilterChipDefaults.shape,
            selectedShape = CircleShape,
            pressedShape = DimaShapes.ExtraSmall,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = container,
            labelColor = label,
            selectedContainerColor = container,
            selectedLabelColor = label,
        ),
    )
}
