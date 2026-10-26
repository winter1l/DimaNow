package com.example.dimanow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dimanow.guidance.HomeBase

/** Home's movable cards (D-128(1)), by the keys their list items use; the briefing card always stays first. */
internal enum class HomeCard(val key: String, val title: String) {
    LEARNING("learning", "수업"),
    ACADEMIC("academic", "학사 일정"),
    SHUTTLE("shuttle", "셔틀"),
    ROLL_CALL("roll_call", "점호"),
    MEAL("meal", "식단"),
    NOTICES("notices", "학교 공지"),
}

/** What the favourites row can open (D-128(1)). */
internal enum class HomeShortcut(val key: String, val label: String, val icon: ImageVector) {
    TIMETABLE("timetable", "시간표", Icons.Default.Schedule),
    OUTING("outing", "외출·외박 신청", Icons.Default.Luggage),
    LIBRARY_CARD("library_card", "모바일 열람증", Icons.Default.Badge),
    // D-132/D-133: PAYCO's 식권 home, whose 사용하기 shows the 예인관 식권 barcode
    MEAL_TICKET("meal_ticket", "PAYCO 식권", Icons.Default.QrCode2),
    HEYOUNG("heyoung", "헤이영 출결", Icons.Default.EventAvailable),
    DORM("dorm", "기숙사", Icons.Default.Apartment),
    EQUIPMENT("equipment", "장비 대여", Icons.Default.Videocam),
    SETTINGS("settings", "설정", Icons.Default.Settings),
}

/**
 * The cards to draw, in order: the saved order first (unknown keys skipped), then any card it does not name in its
 * default place, so a card added in a later version still appears; hidden ones are left out.
 */
internal fun homeCardOrder(saved: List<String>, hidden: Set<String>): List<HomeCard> {
    val known = saved.mapNotNull { key -> HomeCard.entries.firstOrNull { it.key == key } }.distinct()
    return (known + HomeCard.entries.filter { it !in known }).filter { it.key !in hidden }
}

/** The favourites row until the student picks: 시간표 and 모바일 열람증, plus 외출·외박 신청 for 엔터관. */
internal fun homeFavorites(saved: List<String>?, homeBase: HomeBase): List<HomeShortcut> =
    saved?.mapNotNull { key -> HomeShortcut.entries.firstOrNull { it.key == key } }?.distinct()
        ?: listOfNotNull(HomeShortcut.TIMETABLE, HomeShortcut.OUTING.takeIf { homeBase == HomeBase.YEIN }, HomeShortcut.LIBRARY_CARD)

/** D-128(1): the favourites row under the briefing card, one tonal chip per shortcut, wrapping on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeFavoritesRow(shortcuts: List<HomeShortcut>, onOpen: (HomeShortcut) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().testTag("home_favorites"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shortcuts.forEach { shortcut ->
            AssistChip(
                onClick = { onOpen(shortcut) },
                label = { Text(shortcut.label) },
                leadingIcon = { Icon(shortcut.icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("home_favorite_${shortcut.key}"),
            )
        }
    }
}

/** The quiet `홈 편집` button at the end of Home (D-128(1)). */
@Composable
internal fun HomeEditButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(shapes = ButtonDefaults.shapes(), onClick = onClick, modifier = Modifier.testTag("home_edit")) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text("홈 편집", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

/**
 * D-128(1): the Home editor, full screen. Cards move with up/down buttons (reachable without dragging, also by
 * screen readers) and show or hide with a switch; favourites are ticked on, in the order ticked. Nothing is saved
 * until 저장.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeEditorDialog(
    order: List<HomeCard>,
    hidden: Set<String>,
    favorites: List<HomeShortcut>,
    onSave: (order: List<HomeCard>, hidden: Set<String>, favorites: List<HomeShortcut>) -> Unit,
    onDismiss: () -> Unit,
) {
    // The full order, hidden cards included, so hiding does not lose a card's place.
    var cards by rememberSaveable { mutableStateOf(homeCardOrder(order.map { it.key }, emptySet()).map { it.key }) }
    var hiddenKeys by rememberSaveable { mutableStateOf(hidden.toList()) }
    var picked by rememberSaveable { mutableStateOf(favorites.map { it.key }) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("home_editor"),
            topBar = {
                TopAppBar(
                    title = { Text("홈 편집") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                    },
                    actions = {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                onSave(
                                    cards.mapNotNull { key -> HomeCard.entries.firstOrNull { it.key == key } },
                                    hiddenKeys.toSet(),
                                    picked.mapNotNull { key -> HomeShortcut.entries.firstOrNull { it.key == key } },
                                )
                            },
                            modifier = Modifier.testTag("home_editor_save"),
                        ) { Text("저장") }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsSectionHeader("카드 순서")
                Text(
                    "수업 브리핑은 늘 맨 위에 있어요. 카드는 위아래로 옮기거나 숨길 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cards.forEachIndexed { index, key ->
                    val card = HomeCard.entries.first { it.key == key }
                    val shown = key !in hiddenKeys
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home_editor_card_$key"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(card.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { cards = cards.toMutableList().apply { add(index - 1, removeAt(index)) } },
                            enabled = index > 0,
                        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "${card.title} 위로") }
                        IconButton(
                            onClick = { cards = cards.toMutableList().apply { add(index + 1, removeAt(index)) } },
                            enabled = index < cards.lastIndex,
                        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "${card.title} 아래로") }
                        Switch(
                            checked = shown,
                            onCheckedChange = { show -> hiddenKeys = if (show) hiddenKeys - key else hiddenKeys + key },
                            modifier = Modifier.testTag("home_editor_show_$key"),
                        )
                    }
                }
                SettingsSectionHeader("즐겨찾기")
                Text(
                    "고른 순서대로 브리핑 카드 아래에 버튼으로 보여요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HomeShortcut.entries.forEach { shortcut ->
                    val checked = shortcut.key in picked
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Checkbox) { picked = if (checked) picked - shortcut.key else picked + shortcut.key }
                            .testTag("home_editor_favorite_${shortcut.key}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(shortcut.icon, contentDescription = null)
                        Text(shortcut.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
                OutlinedButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = {
                        cards = HomeCard.entries.map { it.key }
                        hiddenKeys = emptyList()
                    },
                    modifier = Modifier.padding(top = 8.dp).testTag("home_editor_reset"),
                ) { Text("카드 순서 처음대로") }
            }
        }
    }
}
