package com.example.dimanow.location

import android.content.Context
import com.example.dimanow.domain.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** What a filled area of the 캠퍼스 지도 base map is (D-132), from its OpenStreetMap tags; drawn in this order. */
internal enum class MapAreaKind(val key: String) {
    WOOD("wood"),
    RESIDENTIAL("residential"),
    INDUSTRIAL("industrial"),
    CAMPUS("campus"),
    GREEN("green"),
    PITCH("pitch"),
    PLAZA("plaza"),
    WATER("water"),
}

/** A road, path or stream class on the base map (D-132), from OpenStreetMap's `highway`/`waterway`; drawn in this order. */
internal enum class MapLineKind(val key: String) {
    STREAM("stream"),
    TRACK("track"),
    PATH("path"),
    STEPS("steps"),
    SERVICE("service"),
    MINOR("minor"),
    MAJOR("major"),
}

/** A filled area: its outer ring first, then any holes ([rings] are open rings, the closing edge implied). */
internal data class MapArea(val kind: MapAreaKind, val rings: List<List<GeoPoint>>, val name: String?)

/** A building footprint as OpenStreetMap draws it (D-132); [osmId] is the way id, negative for a multipolygon. */
internal data class MapBuildingShape(val osmId: Long, val name: String?, val ring: List<GeoPoint>)

/** A road, path or stream as a polyline (D-132). */
internal data class MapLine(val kind: MapLineKind, val points: List<GeoPoint>, val name: String?)

/** A latitude/longitude rectangle (D-132). */
internal data class GeoBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val corners: List<GeoPoint> get() = listOf(GeoPoint(south, west), GeoPoint(north, east))
}

/**
 * The bundled base map of the 캠퍼스 지도 page (D-132): OpenStreetMap ground cover, roads and building footprints
 * around the campus, read from [CampusBaseMapAsset.PATH]. The data is © OpenStreetMap contributors under the ODbL,
 * and the page shows [attribution] on the map.
 */
internal data class CampusBaseMap(
    val bounds: GeoBounds,
    val areas: List<MapArea>,
    val buildings: List<MapBuildingShape>,
    val lines: List<MapLine>,
    val attribution: String,
    val extracted: String,
) {
    fun building(osmId: Long): MapBuildingShape? = buildings.firstOrNull { it.osmId == osmId }
}

/**
 * Reads the compact base-map asset (D-132). Its shape, written by the one-off extraction (2026-10-26, OpenStreetMap
 * API 0.6 `map` for the campus box plus the two wood multipolygons in full, clipped to the box, simplified to about a
 * metre, coordinates rounded to seven decimals):
 *
 * ```
 * {"version":1,"attribution":"© OpenStreetMap contributors","extracted":"2026-10-26","bounds":[s,w,n,e],
 *  "areas":[{"k":"wood","r":[[lat,lon,lat,lon,…],[hole…]],"n":"name"?}],
 *  "buildings":[{"id":471488708,"n":"덕성관"?,"r":[lat,lon,…]}],
 *  "lines":[{"k":"major","n":"서동대로"?,"p":[lat,lon,…]}]}
 * ```
 *
 * Unknown area or line kinds and broken entries are skipped, so a newer asset never breaks an older app.
 */
internal object CampusBaseMapParser {
    fun parse(text: String): CampusBaseMap {
        val root = Json.parseToJsonElement(text).jsonObject
        val bounds = root.getValue("bounds").jsonArray.map { it.jsonPrimitive.double }
        require(bounds.size == 4) { "bounds needs four numbers" }
        return CampusBaseMap(
            bounds = GeoBounds(bounds[0], bounds[1], bounds[2], bounds[3]),
            areas = root.array("areas").mapNotNull { element ->
                val area = element as? JsonObject ?: return@mapNotNull null
                val kind = MapAreaKind.entries.firstOrNull { it.key == area.string("k") } ?: return@mapNotNull null
                val rings = area.array("r").map { points(it as? JsonArray) }.filter { it.size >= 3 }
                if (rings.isEmpty()) null else MapArea(kind, rings, area.string("n"))
            },
            buildings = root.array("buildings").mapNotNull { element ->
                val building = element as? JsonObject ?: return@mapNotNull null
                val ring = points(building["r"] as? JsonArray)
                val id = building["id"]?.jsonPrimitive?.long ?: return@mapNotNull null
                if (ring.size < 3) null else MapBuildingShape(id, building.string("n"), ring)
            },
            lines = root.array("lines").mapNotNull { element ->
                val line = element as? JsonObject ?: return@mapNotNull null
                val kind = MapLineKind.entries.firstOrNull { it.key == line.string("k") } ?: return@mapNotNull null
                val points = points(line["p"] as? JsonArray)
                if (points.size < 2) null else MapLine(kind, points, line.string("n"))
            },
            attribution = root.string("attribution") ?: OSM_ATTRIBUTION,
            extracted = root.string("extracted").orEmpty(),
        )
    }

    private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /** A flat `[lat, lon, lat, lon, …]` array as points; a trailing odd number is dropped. */
    private fun points(array: JsonArray?): List<GeoPoint> {
        if (array == null) return emptyList()
        val numbers = array.map { it.jsonPrimitive.double }
        return (0 until numbers.size / 2).map { GeoPoint(numbers[2 * it], numbers[2 * it + 1]) }
    }
}

/** The OpenStreetMap credit the ODbL asks for wherever the data is shown (D-132). */
internal const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"

/** Loads the bundled base map once per process (D-132); null when the asset cannot be read, and the page then draws without it. */
internal object CampusBaseMapAsset {
    const val PATH = "campus_map.json"

    @Volatile private var cached: CampusBaseMap? = null

    fun load(context: Context): CampusBaseMap? {
        cached?.let { return it }
        return runCatching {
            context.applicationContext.assets.open(PATH).bufferedReader(Charsets.UTF_8).use { CampusBaseMapParser.parse(it.readText()) }
        }.getOrNull()?.also { cached = it }
    }
}
