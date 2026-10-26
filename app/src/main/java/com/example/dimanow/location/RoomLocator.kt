package com.example.dimanow.location

/** Where a class's room is, as far as the app can tell (D-132). [room] is the text as the timetable has it. */
internal sealed interface RoomLocation {
    val room: String

    /** The room's [building], with [label] the room as the 캠퍼스 지도 shows it: `지성관 3층 14호`, `덕성관 101호`. */
    data class Found(override val room: String, val building: CampusBuilding, val label: String) : RoomLocation

    /** A room whose building the app does not know; the map shows the whole campus with [note]. */
    data class Unknown(override val room: String) : RoomLocation {
        val note: String
            get() = if (room.firstOrNull()?.isDigit() == true) {
                "강의실 번호 ${room}의 건물을 아직 몰라요"
            } else {
                "강의실 ‘${room}’의 건물을 아직 몰라요"
            }
    }
}

/**
 * Finds the building of a timetable room (D-132), for the location button beside a class's room on Home and 시간표.
 * Two forms are read:
 *
 * 1. **Building name + number**, as the intranet writes rooms (`덕성관101`, `기예관201-1`; D-108(5), observed
 *    2026-10-23): the longest building name or alias in the text ([CampusBuilding.aliases]: 대학본부/본관/도서관,
 *    지성관/학생회관, 종합촬영소/촬영소, 엔터테인먼트관/엔터관, 예인관/기숙사 …) names the building, and the rest is
 *    shown as the room number (`덕성관 101호`).
 * 2. **A room number alone**: building number + two-digit floor + two-digit room, so the building is what is left after
 *    dropping the last four digits — `20314` is building 2, floor 3, room 14 (`지성관 3층 14호`), `130201` building
 *    13 (`엔터테인먼트관 2층 1호`). A mezzanine is written with 중 (`30중08` → 기예관 중층 8호). The rule and the
 *    numbers in [BUILDING_NUMBERS] are the user's (2026-10-26). A number with fewer than five digits (`222`) or a
 *    building number not in the table (4, 6, 7, 9, 11, 12 …) is [RoomLocation.Unknown].
 *
 * Spaces are ignored and a trailing `호` is allowed. Nothing here changes D-108's shuttle side (`campusZoneForRoom`),
 * which still reads only building names.
 */
internal object RoomLocator {
    /** School building numbers (D-132, from the user 2026-10-26): 1 대학본부 … 13 엔터테인먼트관. */
    val BUILDING_NUMBERS: Map<Int, CampusBuilding> =
        CampusBuilding.entries.mapNotNull { building -> building.number?.let { it to building } }.toMap()

    /** Null for a blank room (nothing to show). */
    fun locate(room: String): RoomLocation? {
        val shown = room.trim()
        if (shown.isEmpty()) return null
        val text = shown.filterNot(Char::isWhitespace)
        RoomCode.parse(text)?.let { code ->
            val building = BUILDING_NUMBERS[code.buildingNumber] ?: return RoomLocation.Unknown(shown)
            return RoomLocation.Found(shown, building, code.label(building))
        }
        val (building, alias, index) = ALIASES
            .mapNotNull { (alias, building) -> text.indexOf(alias).takeIf { it >= 0 }?.let { Triple(building, alias, it) } }
            .maxByOrNull { (_, alias, _) -> alias.length }
            ?: return RoomLocation.Unknown(shown)
        val rest = text.substring(index + alias.length).trimStart('-', '_', '·', ',')
        return RoomLocation.Found(shown, building, nameFormLabel(building, alias, rest))
    }

    private fun nameFormLabel(building: CampusBuilding, alias: String, rest: String): String {
        val code = RoomCode.parse(rest)
        return when {
            // A facility named instead of the building (`도서관`) keeps its name after the building's.
            alias in FACILITY_ALIASES -> listOf(building.displayName, alias + rest).joinToString(" ")
            rest.isEmpty() -> building.displayName
            code != null && code.buildingNumber == building.number -> code.label(building)
            NUMBER_ONLY.matches(rest) -> "${building.displayName} ${rest.removeSuffix("호")}호"
            else -> "${building.displayName} $rest"
        }
    }

    /** Every alias with its building, the space-free form of each (rooms are matched without spaces). */
    private val ALIASES: List<Pair<String, CampusBuilding>> = CampusBuilding.entries.flatMap { building ->
        (building.aliases + building.displayName).map { it.filterNot(Char::isWhitespace) to building }
    }.distinct()

    private val NUMBER_ONLY = Regex("""\d[\d-]*호?""")

    /** Aliases that name a facility inside the building rather than the building itself. */
    private val FACILITY_ALIASES = setOf("도서관")
}

/** A numeric room (D-132): building number, then a two-digit floor (or a mezzanine) and a two-digit room. */
internal data class RoomCode(val buildingNumber: Int, val floor: Int?, val room: Int, val suffix: String, val raw: String) {
    /** `지성관 3층 14호`, `기예관 중층 8호`; a zero floor or room keeps the number as written (`지성관 20014`). */
    fun label(building: CampusBuilding): String = when {
        room == 0 || floor == 0 -> "${building.displayName} $raw"
        floor == null -> "${building.displayName} 중층 $room${suffix}호"
        else -> "${building.displayName} ${floor}층 $room${suffix}호"
    }

    companion object {
        private val PLAIN = Regex("""(\d+)(\d{2})(\d{2})(-\d+)?호?""")
        private val MEZZANINE = Regex("""(\d+)\d중(\d{1,2})(-\d+)?호?""")

        fun parse(text: String): RoomCode? {
            PLAIN.matchEntire(text)?.let { match ->
                val (building, floor, room, suffix) = match.destructured
                return RoomCode(building.toIntOrNull() ?: -1, floor.toInt(), room.toInt(), suffix, text.removeSuffix("호"))
            }
            MEZZANINE.matchEntire(text)?.let { match ->
                val (building, room, suffix) = match.destructured
                return RoomCode(building.toIntOrNull() ?: -1, null, room.toInt(), suffix, text.removeSuffix("호"))
            }
            return null
        }
    }
}
