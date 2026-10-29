package com.example.dimanow.location

import com.example.dimanow.school.campusZoneForRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** D-132: a timetable room resolves to its building by name or by its number, or says the building is unknown. */
class RoomLocatorTest {
    private fun found(room: String): RoomLocation.Found {
        val location = RoomLocator.locate(room)
        assertTrue("$room → $location", location is RoomLocation.Found)
        return location as RoomLocation.Found
    }

    private fun unknown(room: String): RoomLocation.Unknown {
        val location = RoomLocator.locate(room)
        assertTrue("$room → $location", location is RoomLocation.Unknown)
        return location as RoomLocation.Unknown
    }

    @Test
    fun `the intranet's building name plus number names the building and keeps the number`() {
        assertEquals(CampusBuilding.DEOKSEONG, found("덕성관101").building)
        assertEquals("덕성관 101호", found("덕성관101").label)
        assertEquals("기예관 201-1호", found("기예관201-1").label)
        assertEquals("지성관 301호", found("지성관 301").label)
        assertEquals("엔터테인먼트관 B101", found("엔터테인먼트관 B101").label)
        assertEquals("종합촬영소 3스튜디오", found("DIMA 종합촬영소 3스튜디오").label)
        assertEquals("체육관", found("체육관").label)
        assertEquals("예인관", found("예인관").label)
    }

    @Test
    fun `aliases students use find the same buildings as D-108`() {
        assertEquals(CampusBuilding.MAIN_HALL, found("본관201").building)
        assertEquals("대학본부 201호", found("본관201").label)
        assertEquals(CampusBuilding.MAIN_HALL, found("대학 본부 105").building)
        assertEquals(CampusBuilding.JISEONG, found("학생회관202").building)
        assertEquals(CampusBuilding.ENTERTAINMENT, found("엔터관301").building)
        assertEquals(CampusBuilding.STUDIO, found("촬영소A").building)
        assertEquals(CampusBuilding.YEIN_HALL, found("기숙사 세미나실").building)
        // A facility named instead of its building keeps its name.
        assertEquals(CampusBuilding.MAIN_HALL, found("도서관").building)
        assertEquals("대학본부 도서관", found("도서관").label)
    }

    @Test
    fun `a room number alone is building, two-digit floor and two-digit room`() {
        assertEquals(CampusBuilding.JISEONG, found("20314").building)
        assertEquals("지성관 3층 14호", found("20314").label)
        assertEquals("엔터테인먼트관 2층 1호", found("130201").label)
        assertEquals("덕성관 1층 5호", found("50105").label)
        assertEquals("디자인센터 2층 8호", found("100208").label)
        assertEquals("대학본부 1층 2호", found("10102").label)
        assertEquals("기예관 4층 11호", found("30411").label)
        assertEquals("종합촬영소 1층 1호", found("80101").label)
        assertEquals("덕성관 2층 11호", found("50211").label)
        // Spaces, a trailing 호 and a sub-room are allowed.
        assertEquals("지성관 3층 14호", found(" 203 14호 ").label)
        assertEquals("지성관 3층 14-1호", found("20314-1").label)
    }

    @Test
    fun `a mezzanine room is written with 중`() {
        assertEquals(CampusBuilding.GIYE, found("30중08").building)
        assertEquals("기예관 중층 8호", found("30중08").label)
    }

    @Test
    fun `a building name with the full number reads the floor too`() {
        assertEquals("지성관 3층 14호", found("지성관20314").label)
        // A number that does not start with the named building's number stays as written.
        assertEquals("덕성관 20314호", found("덕성관20314").label)
    }

    @Test
    fun `an unknown building number or a short number leaves the whole campus with a note`() {
        assertEquals("강의실 번호 40211의 건물을 아직 몰라요", unknown("40211").note)
        assertEquals("강의실 번호 120301의 건물을 아직 몰라요", unknown("120301").note)
        assertEquals("강의실 번호 222의 건물을 아직 몰라요", unknown("222").note)
        assertEquals("강의실 번호 1234의 건물을 아직 몰라요", unknown("1234").note)
        assertEquals("강의실 ‘가동101’의 건물을 아직 몰라요", unknown("가동101").note)
        assertEquals("강의실 ‘온라인’의 건물을 아직 몰라요", unknown("온라인").note)
        unknown("99999999999999999999")
        assertNull(RoomLocator.locate(""))
        assertNull(RoomLocator.locate("   "))
    }

    @Test
    fun `the building numbers are the user's table`() {
        assertEquals(
            mapOf(
                1 to CampusBuilding.MAIN_HALL,
                2 to CampusBuilding.JISEONG,
                3 to CampusBuilding.GIYE,
                5 to CampusBuilding.DEOKSEONG,
                8 to CampusBuilding.STUDIO,
                10 to CampusBuilding.DESIGN_CENTER,
                13 to CampusBuilding.ENTERTAINMENT,
            ),
            RoomLocator.BUILDING_NUMBERS,
        )
    }

    @Test
    fun `every alias D-108 knows is on the same shuttle side as its building`() {
        CampusBuilding.entries.forEach { building ->
            (building.aliases + building.displayName).forEach { alias ->
                val side = campusZoneForRoom("${alias}101")
                if (side != null) assertEquals("$alias", building.zone, side)
            }
        }
    }

    @Test
    fun `sky bridges read from each end`() {
        assertEquals(listOf("4층 → 기예관 2층", "3층 → 기예관 1층"), CampusBuilding.JISEONG.bridgeLines)
        assertEquals(listOf("2층 → 지성관 4층", "1층 → 지성관 3층", "3층 → 덕성관 2층"), CampusBuilding.GIYE.bridgeLines)
        assertEquals(listOf("2층 → 기예관 3층"), CampusBuilding.DEOKSEONG.bridgeLines)
        assertEquals(emptyList<String>(), CampusBuilding.STUDIO.bridgeLines)
    }

    @Test
    fun `the list puts numbered buildings first, in number order`() {
        assertEquals(listOf(1, 2, 3, 5, 8, 10, 13, null, null), CampusBuilding.ordered.map { it.number })
        assertEquals("2번 건물 지성관", CampusBuilding.JISEONG.spokenName)
        assertEquals("체육관", CampusBuilding.GYM.spokenName)
    }
}
