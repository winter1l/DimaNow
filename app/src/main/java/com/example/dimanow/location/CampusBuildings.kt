package com.example.dimanow.location

import com.example.dimanow.domain.CampusZoneId

/** One floor's line in a building's guide (D-132): `1층` and what is there; [floor] is blank when the source gives none. */
internal data class BuildingFloor(val floor: String, val facilities: List<String>)

/**
 * A school building on the 캠퍼스 지도 (D-132), as students name it, with what the page needs to find and explain it.
 *
 * - [number] is the school's building number, the leading digits of a numeric room (`20314` → 2), confirmed by the
 *   user on 2026-10-26: 1 대학본부, 2 지성관, 3 기예관, 5 덕성관, 8 종합촬영소, 10 디자인센터, 13 엔터테인먼트관.
 *   Buildings without one (체육관, 예인관) are still drawn and found by name.
 * - [osmWayIds] are the OpenStreetMap footprints that make up the building, the first one carrying the label:
 *   대학본부 includes the library wing (`amenity=library`, unnamed in OSM) since the library is on its first floor,
 *   and 지성관 includes the footprint OSM and Kakao Map label 학생회관 (D-108 reads 지성관 as 학생회관).
 * - [osmName] is OSM's name when it differs from the student name in more than spacing (shown with it).
 * - [aliases] are the names a room or a student may use; D-108's room → shuttle-side table reads the same names.
 * - [floors] comes from the 2021 DIMA MAP in the school newspaper (shared by the user 2026-10-26; facts only, the image
 *   is not bundled), so a facility may have moved since.
 */
internal enum class CampusBuilding(
    val number: Int?,
    val displayName: String,
    val osmWayIds: List<Long>,
    val zone: CampusZoneId,
    val aliases: List<String>,
    val osmName: String? = null,
    val floors: List<BuildingFloor> = emptyList(),
) {
    MAIN_HALL(
        number = 1,
        displayName = "대학본부",
        osmWayIds = listOf(471_488_712L, 469_419_567L),
        zone = CampusZoneId.MAIN,
        aliases = listOf("대학본부", "본관", "도서관"),
        floors = listOf(BuildingFloor("1층", listOf("도서관", "20주년 기념홀"))),
    ),
    JISEONG(
        number = 2,
        displayName = "지성관",
        osmWayIds = listOf(471_488_710L, 471_488_711L),
        zone = CampusZoneId.MAIN,
        aliases = listOf("지성관", "학생회관"),
        floors = listOf(
            BuildingFloor("1층", listOf("피트니스 센터", "동아쉼터", "세븐일레븐", "라감 커피", "서점")),
            BuildingFloor("2층", listOf("학생식당", "교무처", "학생복지처", "건강관리실", "우리은행")),
            BuildingFloor("3층", listOf("학보사 PRESS", "콘서트홀")),
        ),
    ),
    GIYE(
        number = 3,
        displayName = "기예관",
        osmWayIds = listOf(471_488_709L),
        zone = CampusZoneId.MAIN,
        aliases = listOf("기예관"),
        floors = listOf(
            BuildingFloor("1층", listOf("창의실습지원센터")),
            BuildingFloor("2층", listOf("종합편집실", "녹음실")),
            BuildingFloor("3층", listOf("대학방송국 DBS", "개방편집실", "학생상담실", "취업지원센터", "창업교육센터")),
            BuildingFloor("4층", listOf("그린나래", "컴퓨터실")),
        ),
    ),
    DEOKSEONG(
        number = 5,
        displayName = "덕성관",
        osmWayIds = listOf(471_488_708L),
        zone = CampusZoneId.MAIN,
        aliases = listOf("덕성관"),
        floors = listOf(
            BuildingFloor("1층", listOf("계단강의실", "개인 연습실", "합주실")),
            BuildingFloor("2층", listOf("소극장")),
            BuildingFloor("3층", listOf("예비군대대", "DIMA 컬처스", "개인편집실")),
        ),
    ),
    STUDIO(
        number = 8,
        displayName = "종합촬영소",
        osmWayIds = listOf(471_488_705L),
        zone = CampusZoneId.YEIN,
        aliases = listOf("종합촬영소", "촬영소", "영화촬영스튜디오", "촬영스튜디오"),
        osmName = "DIMA 영화촬영 스튜디오",
        floors = listOf(BuildingFloor("", listOf("종합 촬영 스튜디오", "이경순 소리박물관", "디마 엔터테인먼트"))),
    ),
    DESIGN_CENTER(
        number = 10,
        displayName = "디자인센터",
        osmWayIds = listOf(471_488_702L),
        zone = CampusZoneId.YEIN,
        aliases = listOf("디자인센터"),
        floors = listOf(BuildingFloor("1층", listOf("스타일실", "텍스타일실"))),
    ),
    ENTERTAINMENT(
        number = 13,
        displayName = "엔터테인먼트관",
        osmWayIds = listOf(471_488_704L),
        zone = CampusZoneId.YEIN,
        aliases = listOf("엔터테인먼트관", "엔터관", "엔터"),
        floors = listOf(
            BuildingFloor("1층", listOf("DIMA 갤러리")),
            BuildingFloor("2층", listOf("대극장")),
        ),
    ),
    GYM(
        number = null,
        displayName = "체육관",
        osmWayIds = listOf(468_690_098L),
        zone = CampusZoneId.MAIN,
        aliases = listOf("종합체육관", "체육관"),
        osmName = "동아방송예술대학 체육관",
    ),
    YEIN_HALL(
        number = null,
        displayName = "예인관",
        osmWayIds = listOf(471_488_701L),
        zone = CampusZoneId.YEIN,
        aliases = listOf("예인관", "기숙사"),
    ),
    ;

    /** The footprint that carries the map label. */
    val labelWayId: Long get() = osmWayIds.first()

    /** What a screen reader says for the number badge and name (`2번 건물 지성관`); the screen shows a badge instead. */
    val spokenName: String get() = number?.let { "${it}번 건물 $displayName" } ?: displayName

    /** The sky bridges (구름다리) that start or end here, as `4층 → 기예관 2층` lines (D-132). */
    val bridgeLines: List<String>
        get() = SKY_BRIDGES.mapNotNull { bridge ->
            when (this) {
                bridge.from -> "${bridge.fromFloor}층 → ${bridge.to.displayName} ${bridge.toFloor}층"
                bridge.to -> "${bridge.toFloor}층 → ${bridge.from.displayName} ${bridge.fromFloor}층"
                else -> null
            }
        }

    companion object {
        /** Numbered buildings in number order, then the others, as the list under the map shows them. */
        val ordered: List<CampusBuilding> = entries.sortedWith(compareBy(nullsLast()) { it.number })

        fun byNumber(number: Int): CampusBuilding? = entries.firstOrNull { it.number == number }

        fun byWayId(osmId: Long): CampusBuilding? = entries.firstOrNull { osmId in it.osmWayIds }
    }
}

/** A sky bridge between two buildings' floors, from the 2021 DIMA MAP (D-132). */
internal data class SkyBridge(val from: CampusBuilding, val fromFloor: Int, val to: CampusBuilding, val toFloor: Int)

/** 지성관 4층 → 기예관 2층, 지성관 3층 → 기예관 1층, 기예관 3층 → 덕성관 2층 (the campus sits on a slope). */
internal val SKY_BRIDGES = listOf(
    SkyBridge(CampusBuilding.JISEONG, 4, CampusBuilding.GIYE, 2),
    SkyBridge(CampusBuilding.JISEONG, 3, CampusBuilding.GIYE, 1),
    SkyBridge(CampusBuilding.GIYE, 3, CampusBuilding.DEOKSEONG, 2),
)
