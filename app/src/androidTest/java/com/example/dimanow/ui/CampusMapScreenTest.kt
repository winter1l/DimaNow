package com.example.dimanow.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.dimanow.location.CampusBuilding
import com.example.dimanow.location.CampusPlaceKind
import com.example.dimanow.location.CampusPlaces
import com.example.dimanow.location.OSM_ATTRIBUTION
import com.example.dimanow.theme.DIMANowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * D-131(5), D-132: the 캠퍼스 지도 page draws the OpenStreetMap base map with its credit and the 4402 stops, lists the
 * school buildings and the places, opens on a class's room when asked, and goes back.
 */
class CampusMapScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun mapDescription(text: String) = SemanticsMatcher("map says $text") { node ->
        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.any { it == text }
    }

    @Test
    fun mapListsEveryPlaceAndGoesBack() {
        var backs = 0
        compose.setContent {
            DIMANowTheme { CampusMapScreen(onBack = { backs++ }) }
        }

        compose.onNodeWithTag("map_drawing").assertExists().assert(mapDescription("캠퍼스 지도"))
        compose.onNodeWithTag("map_attribution").assertTextEquals(OSM_ATTRIBUTION)
        compose.onNodeWithTag("map_location_status").assertExists()
        compose.onNodeWithTag("map_zoom_in").assertExists()
        CampusPlaces.all.filter { it.kind == CampusPlaceKind.BUS_4402_STOP }.forEach { stop ->
            compose.onNodeWithTag("map_marker_${stop.id}").assertExists()
        }
        CampusBuilding.ordered.forEach { building ->
            compose.onNodeWithTag("map_list").performScrollToNode(hasTestTag("map_building_${building.name}"))
            compose.onNodeWithTag("map_building_${building.name}").assert(hasClickLabel(SHOW_ON_MAP_LABEL))
        }
        CampusPlaces.all.forEach { place ->
            compose.onNodeWithTag("map_list").performScrollToNode(hasTestTag("map_place_${place.id}"))
            compose.onNodeWithTag("map_place_${place.id}").assert(hasClickLabel(OPEN_IN_MAP_APP_LABEL))
        }
        compose.onNodeWithTag("map_list").performScrollToNode(hasTestTag("map_note"))
        compose.onNodeWithText(CAMPUS_MAP_NOTE).assertExists()

        compose.onNodeWithTag("close_map").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun aClassRoomOpensTheMapOnItsBuilding() {
        compose.setContent {
            DIMANowTheme { CampusMapScreen(onBack = {}, focusRoom = "덕성관101") }
        }

        compose.onNodeWithTag("map_focus_title").assertTextEquals("덕성관 101호")
        compose.onNodeWithTag("map_drawing").assert(mapDescription("캠퍼스 지도, 5번 건물 덕성관 표시 중"))
        // The focus zooms in, so the way back to the whole campus is offered.
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("map_reset")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("map_focus_card").assertExists()
        compose.onNodeWithText("2층", substring = false).assertExists()
    }

    @Test
    fun aRoomNumberFindsItsBuildingByTheLeadingDigits() {
        compose.setContent {
            DIMANowTheme { CampusMapScreen(onBack = {}, focusRoom = "20314") }
        }

        compose.onNodeWithTag("map_focus_title").assertTextEquals("지성관 3층 14호")
        compose.onNodeWithText("구름다리: 4층 → 기예관 2층, 3층 → 기예관 1층").assertExists()
    }

    @Test
    fun anUnknownBuildingLeavesTheWholeCampusWithANote() {
        compose.setContent {
            DIMANowTheme { CampusMapScreen(onBack = {}, focusRoom = "40211") }
        }

        compose.onNodeWithTag("map_room_unknown").assertExists()
        compose.onNodeWithText("강의실 번호 40211의 건물을 아직 몰라요").assertExists()
        compose.onNodeWithTag("map_focus_card").assertDoesNotExist()
        compose.onNodeWithTag("map_reset").assertDoesNotExist()
    }

    @Test
    fun aBuildingRowShowsItOnTheMap() {
        compose.setContent {
            DIMANowTheme { CampusMapScreen(onBack = {}) }
        }

        compose.onNodeWithTag("map_focus_card").assertDoesNotExist()
        compose.onNodeWithTag("map_list").performScrollToNode(hasTestTag("map_building_GIYE"))
        compose.onNodeWithTag("map_building_GIYE").performClick()
        compose.onNodeWithTag("map_focus_title").assertTextEquals("기예관")
        compose.onNodeWithTag("map_drawing").assert(mapDescription("캠퍼스 지도, 3번 건물 기예관 표시 중"))
        compose.onNode(hasText("창의실습지원센터")).assertExists()

        compose.onNodeWithTag("map_focus_close").performClick()
        compose.onNodeWithTag("map_focus_card").assertDoesNotExist()
    }
}
