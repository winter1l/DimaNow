package com.example.dimanow.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.dimanow.domain.GeoPoint
import com.example.dimanow.location.CAMPUS_MAP_ORIGIN
import com.example.dimanow.location.CAMPUS_MAP_STUDENT_LIMIT_METERS
import com.example.dimanow.location.CampusBaseMap
import com.example.dimanow.location.CampusBaseMapAsset
import com.example.dimanow.location.CampusBuilding
import com.example.dimanow.location.CampusMapScene
import com.example.dimanow.location.CampusPlace
import com.example.dimanow.location.CampusPlaceKind
import com.example.dimanow.location.CampusPlaces
import com.example.dimanow.location.CenteredLabelRequest
import com.example.dimanow.location.LocalProjection
import com.example.dimanow.location.LocationSample
import com.example.dimanow.location.MapAreaKind
import com.example.dimanow.location.MapBox
import com.example.dimanow.location.MapCamera
import com.example.dimanow.location.MapCameraSpace
import com.example.dimanow.location.MapLabelRequest
import com.example.dimanow.location.MapLabelRole
import com.example.dimanow.location.MapLabelSpec
import com.example.dimanow.location.MapLineKind
import com.example.dimanow.location.OSM_ATTRIBUTION
import com.example.dimanow.location.PlaneBounds
import com.example.dimanow.location.PlaneBuilding
import com.example.dimanow.location.PlanePoint
import com.example.dimanow.location.RoomLocation
import com.example.dimanow.location.RoomLocator
import com.example.dimanow.location.ScreenPoint
import com.example.dimanow.location.StudentLocationReader
import com.example.dimanow.location.campusPlaceGeoUri
import com.example.dimanow.location.campusPlaceSpokenDescription
import com.example.dimanow.location.campusPlaceSupportingText
import com.example.dimanow.location.fittedMapHeight
import com.example.dimanow.location.formatDistance
import com.example.dimanow.location.geoUri
import com.example.dimanow.location.greatCircleMeters
import com.example.dimanow.location.interpolateCamera
import com.example.dimanow.location.placeCenteredLabels
import com.example.dimanow.location.placeMapLabels
import com.example.dimanow.location.scaleBarLabel
import com.example.dimanow.location.scaleBarMeters
import com.example.dimanow.theme.DimaShapes
import com.example.dimanow.theme.DimaTheme
import com.example.dimanow.ui.motion.LocalReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 캠퍼스 지도 (D-131(5), redrawn D-132), opened from 전체 or from a class's room: a map drawn from the bundled
 * OpenStreetMap extract — ground cover, roads and paths, building footprints with the school buildings' numbers and
 * names — with the 4402 stops and the student's position when location is already allowed. The map pans and zooms
 * (pinch, double tap, the +/− buttons) within the base map's box. Below it, the selected building's card (with the room
 * when the page was opened for one: `덕성관 101호`), the school buildings (tapping one shows it on the map) and the
 * stops and zones, each opening in the phone's map app. No map SDK, key or tiles. Read-only: nothing here changes a
 * zone (D-005), and the page never asks for the location permission.
 *
 * [focusRoom] is the room the page opens on (Home's or 시간표's location button): its building is outlined, labelled
 * and zoomed to; a room whose building [RoomLocator] does not know leaves the whole campus with a note.
 */
@Composable
internal fun CampusMapScreen(onBack: () -> Unit, modifier: Modifier = Modifier, focusRoom: String? = null) {
    val context = LocalContext.current
    val places = CampusPlaces.all
    val stops = remember(places) { places.filter { it.kind == CampusPlaceKind.BUS_4402_STOP } }
    val position = rememberStudentPosition()
    val studentSample = (position as? StudentPosition.Known)?.sample
    val student = studentSample?.point
    val nearStudent = studentSample?.takeIf { greatCircleMeters(CAMPUS_MAP_ORIGIN, it.point) <= CAMPUS_MAP_STUDENT_LIMIT_METERS }
    val projection = remember { LocalProjection(CAMPUS_MAP_ORIGIN) }
    val baseMap = rememberCampusBaseMap()
    val scene = remember(baseMap, nearStudent?.point) {
        (baseMap as? BaseMapLoad.Ready)?.map?.let { map ->
            CampusMapScene.from(map, projection, stops.map { it.point } + listOfNotNull(nearStudent?.point))
        }
    }

    val roomLocation = remember(focusRoom) { focusRoom?.let(RoomLocator::locate) }
    var selectedName by rememberSaveable(focusRoom) { mutableStateOf((roomLocation as? RoomLocation.Found)?.building?.name) }
    var roomShown by rememberSaveable(focusRoom) { mutableStateOf(roomLocation != null) }
    val selected = selectedName?.let { name -> CampusBuilding.entries.firstOrNull { it.name == name } }
    val roomLabel = (roomLocation as? RoomLocation.Found)?.takeIf { roomShown && it.building == selected }?.label
    val unknownRoom = (roomLocation as? RoomLocation.Unknown)?.takeIf { roomShown }

    val camera = remember { mutableStateOf<MapCamera?>(null) }
    var focusRequest by remember { mutableStateOf(selected) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val mapPadding = with(LocalDensity.current) { MAP_PADDING.toPx() }
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val cameraMotion = remember { CameraMotion() }
    val listState = rememberLazyListState()

    // Moves the camera to the building asked for once the map has its size and its footprints. The move runs on the
    // page's scope, so clearing the request (which restarts this effect) does not stop it; a touch does.
    LaunchedEffect(focusRequest, mapSize, scene) {
        val building = focusRequest ?: return@LaunchedEffect
        val ready = scene ?: return@LaunchedEffect
        if (mapSize == IntSize.Zero) return@LaunchedEffect
        focusRequest = null
        val target = ready.boundsOf(building) ?: return@LaunchedEffect
        val space = cameraSpace(ready.home, ready.limit, mapSize, mapPadding)
        val from = space.clamp(camera.value ?: space.homeCamera)
        cameraMotion.launch(scope) { cameraMotion.animate(camera, from, space.focus(target), reducedMotion) }
    }

    fun select(building: CampusBuilding, moveCamera: Boolean) {
        selectedName = building.name
        roomShown = (roomLocation as? RoomLocation.Found)?.building == building && roomShown
        if (moveCamera) {
            focusRequest = building
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    ScreenScaffold(
        title = AppPage.MAP.title,
        modifier = modifier,
        navigationIcon = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack, modifier = Modifier.testTag("close_map")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        listState = listState,
        listTag = "map_list",
    ) {
        item(key = "map") {
            HomeContainerCard(Modifier.testTag("map_card")) {
                CampusMapView(
                    scene = scene,
                    loadFailed = baseMap is BaseMapLoad.Failed,
                    projection = projection,
                    paddingPx = mapPadding,
                    stops = stops,
                    student = nearStudent,
                    selected = selected,
                    camera = camera,
                    cameraMotion = cameraMotion,
                    onSize = { mapSize = it },
                    onSelect = { select(it, moveCamera = false) },
                )
                LocationStatusLine(position, shownOnMap = nearStudent != null)
            }
        }
        when {
            selected != null -> item(key = "focus_${selected.name}") {
                BuildingCard(
                    building = selected,
                    roomLabel = roomLabel,
                    distance = scene?.centerOf(selected)?.let { center -> student?.let { formatDistance(greatCircleMeters(it, center)) } },
                    onOpenInMapApp = {
                        scene?.centerOf(selected)?.let { openPointInMapApp(context, it, selected.displayName) }
                    },
                    onClose = {
                        selectedName = null
                        roomShown = false
                    },
                )
            }
            unknownRoom != null -> item(key = "room_unknown") {
                UnknownRoomCard(unknownRoom, onClose = { roomShown = false })
            }
        }
        item(key = "buildings_header") {
            SectionHeader("건물", "누르면 지도에서 보여 드려요")
        }
        item(key = "buildings") {
            DimaSegmentedList(Modifier.testTag("map_buildings")) {
                CampusBuilding.ordered.forEachIndexed { index, building ->
                    CampusBuildingRow(
                        building = building,
                        distance = scene?.centerOf(building)?.let { center -> student?.let { formatDistance(greatCircleMeters(it, center)) } },
                        index = index,
                        count = CampusBuilding.ordered.size,
                    ) { select(building, moveCamera = true) }
                }
            }
        }
        item(key = "places_header") {
            SectionHeader("정류장과 구역", "누르면 지도 앱에서 열려요")
        }
        item(key = "places") {
            DimaSegmentedList(Modifier.testTag("map_places")) {
                places.forEachIndexed { index, place ->
                    CampusPlaceRow(place, student, index, places.size) { openInMapApp(context, place) }
                }
            }
        }
        item(key = "note") {
            Text(
                CAMPUS_MAP_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp).testTag("map_note"),
            )
        }
    }
}

/** What the page says about its sources and what is left out (D-131(5), D-132). */
internal const val CAMPUS_MAP_NOTE =
    "지도 바탕은 OpenStreetMap 자료예요(© OpenStreetMap contributors, ODbL). 건물 번호와 층별 시설은 2021년 학보의 " +
        "DIMA MAP을 따라서 지금과 다를 수 있어요. 본관, 엔터관·예인관, 원룸촌은 앱이 위치 안내에 쓰는 구역의 중심이라 " +
        "건물 입구와 다를 수 있고, 교내 셔틀 승차 위치(본관·엔터관·운동장)는 앱에 좌표가 없어 넣지 않았어요."

/** Spoken name of each place row's action (D-131(5)). */
internal const val OPEN_IN_MAP_APP_LABEL = "지도 앱에서 열기"

/** Spoken name of a building row's action (D-132). */
internal const val SHOW_ON_MAP_LABEL = "지도에서 보기"

/** Spoken name of the location button beside a class's room on Home and 시간표 (D-132). */
internal const val SHOW_ROOM_ON_MAP_LABEL = "강의실 위치 보기"

/**
 * The location button beside a class's room (D-132), on Home's class card and each 시간표 course card: it opens 캠퍼스
 * 지도 on the room's building. A standard icon button, so it keeps the 48dp touch target.
 */
@Composable
internal fun RoomLocationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.LocationOn, contentDescription = SHOW_ROOM_ON_MAP_LABEL, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** The bundled base map as the page reads it (D-132). */
private sealed interface BaseMapLoad {
    data object Loading : BaseMapLoad
    data class Ready(val map: CampusBaseMap) : BaseMapLoad
    data object Failed : BaseMapLoad
}

/** Reads the base-map asset off the main thread, once per process ([CampusBaseMapAsset] keeps it). */
@Composable
private fun rememberCampusBaseMap(): BaseMapLoad {
    val context = LocalContext.current
    val state by produceState<BaseMapLoad>(BaseMapLoad.Loading, context) {
        value = withContext(Dispatchers.IO) { CampusBaseMapAsset.load(context) }?.let { BaseMapLoad.Ready(it) } ?: BaseMapLoad.Failed
    }
    return state
}

/** Where the student is, as far as this page knows (D-131(5)). */
private sealed interface StudentPosition {
    /** Location is not allowed; the page does not ask. */
    data object NotAllowed : StudentPosition
    data object Locating : StudentPosition
    data class Known(val sample: LocationSample) : StudentPosition
    /** Allowed, but no fix came. */
    data object Unknown : StudentPosition
}

/**
 * Reads the student's position whenever the page resumes (D-131(5)), only when the permission is already granted, so
 * returning from the system settings with location allowed shows it at once. A known position stays until a newer one
 * comes.
 */
@Composable
private fun rememberStudentPosition(): StudentPosition {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(StudentLocationReader.canRead(context)) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = StudentLocationReader.canRead(context)
        resumes++
    }
    var position by remember { mutableStateOf<StudentPosition>(if (allowed) StudentPosition.Locating else StudentPosition.NotAllowed) }
    LaunchedEffect(allowed, resumes) {
        if (!allowed) {
            position = StudentPosition.NotAllowed
            return@LaunchedEffect
        }
        // The first ON_RESUME arrives right after the page appears; reading then avoids a second, cancelled read.
        if (resumes == 0) return@LaunchedEffect
        if (position !is StudentPosition.Known) position = StudentPosition.Locating
        StudentLocationReader.fixes(context).collect { position = StudentPosition.Known(it) }
        if (position !is StudentPosition.Known) position = StudentPosition.Unknown
    }
    return position
}

/** The camera space for a map of [size] pixels with [padding] around the home view (D-132). */
private fun cameraSpace(home: PlaneBounds, limit: PlaneBounds, size: IntSize, padding: Float): MapCameraSpace =
    MapCameraSpace(home, limit, size.width.toFloat(), size.height.toFloat(), padding)

/**
 * The camera's moves that are not a finger's (D-132): to a focused building, back home, a double tap or a +/− button.
 * A new move or a touch cancels the one under way. The move uses a non-bouncy spring: a camera that overshoots would
 * zoom past its target and back.
 */
private class CameraMotion {
    private var job: Job? = null
    private val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun cancel() {
        job?.cancel()
        job = null
    }

    suspend fun animate(state: MutableState<MapCamera?>, from: MapCamera, to: MapCamera?, reducedMotion: Boolean) {
        if (reducedMotion || to == null) {
            state.value = to
            return
        }
        val progress = Animatable(0f)
        progress.animateTo(1f, spec) { state.value = interpolateCamera(from, to, value) }
    }

    fun launch(scope: kotlinx.coroutines.CoroutineScope, block: suspend () -> Unit) {
        cancel()
        job = scope.launch { block() }
    }
}

/** The base map's plane geometry as paths in metres (east, south), built once per scene (D-132). */
private class SceneShapes(scene: CampusMapScene) {
    val areas: List<Pair<MapAreaKind, Path>> = scene.areas.map { area ->
        area.kind to Path().apply {
            fillType = PathFillType.EvenOdd
            area.rings.forEach { addRing(it) }
        }
    }
    val lines: Map<MapLineKind, Path> = scene.lines.groupBy { it.kind }.mapValues { (_, lines) ->
        Path().apply {
            lines.forEach { line ->
                line.points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.east.toFloat(), -point.north.toFloat()) else lineTo(point.east.toFloat(), -point.north.toFloat())
                }
            }
        }
    }
    val buildings: List<Pair<PlaneBuilding, Path>> = scene.buildings.map { building -> building to Path().apply { addRing(building.ring) } }

    private fun Path.addRing(ring: List<PlanePoint>) {
        ring.forEachIndexed { index, point ->
            if (index == 0) moveTo(point.east.toFloat(), -point.north.toFloat()) else lineTo(point.east.toFloat(), -point.north.toFloat())
        }
        close()
    }
}

/**
 * The drawn map (D-132). The base map, the selected building's outline, the student's accuracy circle, the labels,
 * the scale bar and the north mark are drawn; the 4402 stops and the student are composables placed at their points,
 * each with a spoken description; the zoom buttons and the OpenStreetMap credit sit over it. One finger pans only once
 * zoomed in, so on the home view a drag scrolls the page; two fingers pinch, a double tap zooms in (and out again at the
 * closest zoom), and a tap on a school building selects it.
 */
@Composable
private fun CampusMapView(
    scene: CampusMapScene?,
    loadFailed: Boolean,
    projection: LocalProjection,
    paddingPx: Float,
    stops: List<CampusPlace>,
    student: LocationSample?,
    selected: CampusBuilding?,
    camera: MutableState<MapCamera?>,
    cameraMotion: CameraMotion,
    onSize: (IntSize) -> Unit,
    onSelect: (CampusBuilding) -> Unit,
) {
    val fallbackHome = remember(stops) {
        PlaneBounds.of((stops.map { it.point } + CampusPlaces.zoneAreas.flatMap { it.second }).map(projection::toPlane))
    }
    val home = scene?.home ?: fallbackHome
    val limit = scene?.limit ?: fallbackHome
    val palette = campusMapPalette()
    val shapes = remember(scene) { scene?.let(::SceneShapes) }
    val textMeasurer = rememberTextMeasurer(cacheSize = 96)
    val typography = MaterialTheme.typography
    val labelStyle = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    val smallStyle = typography.labelSmall
    val badgeStyle = typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val currentHome by rememberUpdatedState(home)
    val currentLimit by rememberUpdatedState(limit)
    val currentScene by rememberUpdatedState(scene)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var pixelSize by remember { mutableStateOf(IntSize.Zero) }
    val markerCount = stops.size + if (student != null) 1 else 0

    /** The camera space at the map's current size; null before the first layout. */
    fun space(): MapCameraSpace? = pixelSize.takeIf { it != IntSize.Zero }?.let { cameraSpace(currentHome, currentLimit, it, paddingPx) }

    /** Moves to [target] (null: back home), as a button or double tap asks. */
    fun animateTo(target: MapCamera?) {
        val current = space() ?: return
        val from = current.clamp(camera.value ?: current.homeCamera)
        cameraMotion.launch(scope) {
            cameraMotion.animate(camera, from, target ?: current.homeCamera, reducedMotion)
            if (target == null) camera.value = null
        }
    }

    Box(Modifier.fillMaxWidth().clip(DimaShapes.Tile)) {
        Layout(
            content = {
                stops.forEach { PlaceMarker(it, student?.point) }
                student?.let { StudentMarker(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    pixelSize = it
                    onSize(it)
                }
                .pointerInput(Unit) {
                    // Pinch and, once zoomed in, one-finger pan; only what it uses is consumed, so on the home view a
                    // one-finger drag still scrolls the page.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        cameraMotion.cancel()
                        var moving = false
                        var travel = Offset.Zero
                        var scale = 1f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val pressed = event.changes.count { it.pressed }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val mapSpace = cameraSpace(currentHome, currentLimit, size, paddingPx)
                            val now = mapSpace.clamp(camera.value ?: mapSpace.homeCamera)
                            if (!moving) {
                                travel += panChange
                                scale *= zoomChange
                                val slop = viewConfiguration.touchSlop
                                val pinched = abs(1 - scale) * event.calculateCentroidSize(useCurrent = false) > slop
                                moving = when {
                                    pressed >= 2 -> pinched || travel.getDistance() > slop
                                    now.zoom > 1.0 + ZOOM_EPSILON -> travel.getDistance() > slop
                                    else -> false
                                }
                            }
                            if (moving) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                var next = now
                                if (zoomChange != 1f && centroid != Offset.Unspecified) {
                                    next = mapSpace.zoom(next, zoomChange.toDouble(), centroid.x, centroid.y)
                                }
                                if (panChange != Offset.Zero) next = mapSpace.pan(next, panChange.x, panChange.y)
                                camera.value = next
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val mapSpace = cameraSpace(currentHome, currentLimit, size, paddingPx)
                            val now = mapSpace.clamp(camera.value ?: mapSpace.homeCamera)
                            // At the closest zoom a double tap goes back to the whole campus.
                            animateTo(if (now.zoom >= mapSpace.maxZoom - ZOOM_EPSILON) null else mapSpace.zoom(now, 2.0, offset.x, offset.y))
                        },
                        onTap = { offset ->
                            val tappedScene = currentScene ?: return@detectTapGestures
                            val mapSpace = cameraSpace(currentHome, currentLimit, size, paddingPx)
                            val viewport = mapSpace.viewport(mapSpace.clamp(camera.value ?: mapSpace.homeCamera))
                            tappedScene.buildingAt(viewport.toPlane(offset.x, offset.y))?.campusBuilding?.let(currentOnSelect)
                        },
                    )
                }
                .drawWithCache {
                    val labelLayouts = scene?.labels?.associateWith { spec ->
                        MeasuredLabel(
                            text = textMeasurer.measure(
                                spec.text,
                                if (spec.role == MapLabelRole.SCHOOL_BUILDING) labelStyle else smallStyle,
                                maxLines = 1,
                            ),
                            badge = spec.number?.let { textMeasurer.measure(it.toString(), badgeStyle) },
                        )
                    }.orEmpty()
                    val roadLayouts = scene?.namedRoads?.keys?.associateWith { textMeasurer.measure(it, smallStyle, maxLines = 1) }.orEmpty()
                    val markerLabels = (stops.map { it.name } + listOfNotNull(student?.let { "내 위치" }))
                        .map { textMeasurer.measure(it, smallStyle, maxLines = 1) }
                    val scaleCaptions = mutableMapOf<Int, TextLayoutResult>()
                    val northCaption = textMeasurer.measure("북", smallStyle)
                    val attribution = textMeasurer.measure(OSM_ATTRIBUTION, smallStyle).size
                    val attributionBox = attributionBox(size, Size(attribution.width.toFloat(), attribution.height.toFloat()))
                    onDrawBehind {
                        val mapSpace = MapCameraSpace(home, limit, size.width, size.height, paddingPx)
                        val viewport = mapSpace.viewport(mapSpace.clamp(camera.value ?: mapSpace.homeCamera))
                        val ppm = viewport.pixelsPerMeter.toFloat()
                        drawRect(palette.ground)
                        if (shapes != null) {
                            val left = (size.width / 2 - viewport.camera.centerEast * ppm).toFloat()
                            val top = (size.height / 2 + viewport.camera.centerNorth * ppm).toFloat()
                            withTransform({
                                translate(left, top)
                                scale(ppm, ppm, pivot = Offset.Zero)
                            }) {
                                drawBaseMap(shapes, palette, ppm, selected)
                            }
                        }
                        student?.let { sample ->
                            val center = viewport.toScreen(projection.toPlane(sample.point))
                            val radius = (sample.accuracyMeters * ppm).coerceAtMost(minOf(size.width, size.height) / 2)
                            if (radius > STUDENT_MARKER_SIZE.toPx() / 2) {
                                drawCircle(palette.ink.copy(alpha = 0.12f), radius, Offset(center.x, center.y))
                                drawCircle(palette.ink.copy(alpha = 0.35f), radius, Offset(center.x, center.y), style = Stroke(1.dp.toPx()))
                            }
                        }
                        // Markers and their labels first; building, area and road names then take the room left.
                        val anchors = (stops.map { it.point } + listOfNotNull(student?.point))
                            .map { viewport.toScreen(projection.toPlane(it)) }
                        val radii = stops.map { PLACE_MARKER_SIZE.toPx() / 2 } +
                            listOfNotNull(student?.let { STUDENT_MARKER_SIZE.toPx() / 2 })
                        val fixed = fixedBoxes(size, attributionBox, resetShown = camera.value != null)
                        val labelPadding = 4.dp.toPx()
                        val markerLabelBoxes = placeMapLabels(
                            labels = anchors.mapIndexed { index, anchor ->
                                MapLabelRequest(
                                    anchor,
                                    radii[index],
                                    markerLabels[index].size.width + 2 * labelPadding,
                                    markerLabels[index].size.height + 2.dp.toPx(),
                                )
                            },
                            width = size.width,
                            height = size.height,
                            gap = LABEL_GAP.toPx(),
                            fixed = fixed,
                        )
                        val obstacles = fixed + markerLabelBoxes +
                            anchors.mapIndexed { index, anchor -> MapBox.around(anchor, radii[index] * 2, radii[index] * 2) }
                        if (scene != null) drawPlaceLabels(scene, labelLayouts, roadLayouts, viewport, selected, palette, obstacles)
                        markerLabelBoxes.forEachIndexed { index, box ->
                            drawRoundRect(
                                palette.labelBackground,
                                topLeft = Offset(box.left, box.top),
                                size = Size(box.width, box.height),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                            )
                            drawText(markerLabels[index], palette.ink, Offset(box.left + labelPadding, box.top + 1.dp.toPx()))
                        }
                        drawScaleBar(viewport.pixelsPerMeter, palette, scaleCaptions) { textMeasurer.measure(scaleBarLabel(it), smallStyle) }
                        drawNorthMark(palette, northCaption)
                    }
                }
                .semantics {
                    isTraversalGroup = true
                    contentDescription = selected?.let { "캠퍼스 지도, ${it.spokenName} 표시 중" } ?: "캠퍼스 지도"
                }
                .testTag("map_drawing"),
        ) { measurables, constraints ->
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else MAP_FALLBACK_WIDTH.roundToPx()
            val fitted = fittedMapHeight(
                width = width.toFloat(),
                bounds = home,
                paddingX = paddingPx,
                paddingY = paddingPx,
                minHeight = MAP_MIN_HEIGHT.toPx(),
                maxHeight = MAP_MAX_HEIGHT.toPx(),
            ).roundToInt()
            val height = if (constraints.hasBoundedHeight) fitted.coerceIn(constraints.minHeight, constraints.maxHeight) else fitted
            val mapSpace = MapCameraSpace(home, limit, width.toFloat(), height.toFloat(), paddingPx)
            val viewport = mapSpace.viewport(mapSpace.clamp(camera.value ?: mapSpace.homeCamera))
            val markers = measurables.take(markerCount).map { it.measure(Constraints()) }
            val anchors = (stops.map { it.point } + listOfNotNull(student?.point)).map { viewport.toScreen(projection.toPlane(it)) }
            layout(width, height) {
                markers.forEachIndexed { index, marker ->
                    marker.place((anchors[index].x - marker.width / 2f).roundToInt(), (anchors[index].y - marker.height / 2f).roundToInt())
                }
            }
        }
        MapControls(
            zoom = { space()?.let { (camera.value?.let(it::clamp) ?: it.homeCamera).zoom } ?: 1.0 },
            maxZoom = { space()?.maxZoom ?: 1.0 },
            showReset = { camera.value != null },
            onZoomIn = {
                space()?.let { animateTo(it.zoom(it.clamp(camera.value ?: it.homeCamera), 2.0, it.width / 2, it.height / 2)) }
            },
            onZoomOut = {
                space()?.let {
                    val next = it.zoom(it.clamp(camera.value ?: it.homeCamera), 0.5, it.width / 2, it.height / 2)
                    animateTo(next.takeIf { zoomed -> zoomed.zoom > 1.0 + ZOOM_EPSILON })
                }
            },
            onReset = { animateTo(null) },
        )
        Text(
            scene?.attribution ?: OSM_ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = palette.ink,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(palette.labelBackground, DimaShapes.ExtraSmall)
                .padding(horizontal = 4.dp)
                .testTag("map_attribution"),
        )
        if (loadFailed) {
            Text(
                "지도 바탕을 불러오지 못했어요",
                style = MaterialTheme.typography.bodySmall,
                color = palette.ink,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(palette.labelBackground, DimaShapes.ExtraSmall)
                    .padding(8.dp),
            )
        }
    }
}

/** A measured map label: the name and, for a numbered school building, its number. */
private class MeasuredLabel(val text: TextLayoutResult, val badge: TextLayoutResult?)

/** Ground cover, roads, paths and footprints in plane metres (the caller scales by [ppm] pixels per metre). */
private fun DrawScope.drawBaseMap(shapes: SceneShapes, palette: CampusMapPalette, ppm: Float, selected: CampusBuilding?) {
    fun px(dpValue: Float) = dpValue * density / ppm
    shapes.areas.forEach { (kind, path) ->
        drawPath(path, palette.area(kind))
        if (kind == MapAreaKind.PITCH) drawPath(path, palette.pitchLine, style = Stroke(px(1f)))
    }
    fun stroke(kind: MapLineKind, color: Color, widthMeters: Float, minDp: Float, extraDp: Float = 0f, dash: Pair<Float, Float>? = null) {
        val path = shapes.lines[kind] ?: return
        val width = maxOf(widthMeters, px(minDp)) + px(extraDp)
        drawPath(
            path,
            color,
            style = Stroke(
                width = width,
                cap = if (dash == null) StrokeCap.Round else StrokeCap.Butt,
                join = StrokeJoin.Round,
                pathEffect = dash?.let { PathEffect.dashPathEffect(floatArrayOf(px(it.first), px(it.second))) },
            ),
        )
    }
    stroke(MapLineKind.STREAM, palette.stream, 3f, 1.2f)
    stroke(MapLineKind.SERVICE, palette.roadCasing, 4f, 1.5f, extraDp = 1.5f)
    stroke(MapLineKind.MINOR, palette.roadCasing, 6f, 2.5f, extraDp = 1.5f)
    stroke(MapLineKind.MAJOR, palette.majorCasing, 10f, 4f, extraDp = 1.5f)
    stroke(MapLineKind.TRACK, palette.track, 2.5f, 1.2f, dash = 4f to 3f)
    stroke(MapLineKind.PATH, palette.path, 1.2f, 1f, dash = 3f to 2f)
    stroke(MapLineKind.STEPS, palette.path, 2f, 1.5f, dash = 1.5f to 1.5f)
    stroke(MapLineKind.SERVICE, palette.roadFill, 4f, 1.5f)
    stroke(MapLineKind.MINOR, palette.roadFill, 6f, 2.5f)
    stroke(MapLineKind.MAJOR, palette.majorFill, 10f, 4f)
    shapes.buildings.forEach { (building, path) ->
        val school = building.campusBuilding != null
        drawPath(path, if (school) palette.schoolBuilding else palette.building)
        drawPath(path, if (school) palette.schoolBuildingEdge else palette.buildingEdge, style = Stroke(px(1f), join = StrokeJoin.Round))
    }
    if (selected != null) {
        shapes.buildings.filter { it.first.campusBuilding == selected }.forEach { (_, path) ->
            drawPath(path, palette.selectedFill)
            drawPath(path, palette.selectedEdge, style = Stroke(px(2.5f), join = StrokeJoin.Round))
        }
    }
}

/**
 * Building, area and road names (D-132), most important first, each only from its zoom on and only where it fits
 * ([placeCenteredLabels]); the selected building's name goes first, on a filled tag. Names have a ground-coloured halo,
 * as map apps draw them, so they read over roads and footprints.
 */
private fun DrawScope.drawPlaceLabels(
    scene: CampusMapScene,
    layouts: Map<MapLabelSpec, MeasuredLabel>,
    roads: Map<String, TextLayoutResult>,
    viewport: com.example.dimanow.location.CameraViewport,
    selected: CampusBuilding?,
    palette: CampusMapPalette,
    obstacles: List<MapBox>,
) {
    val zoom = viewport.camera.zoom
    val badgeSize = BADGE_SIZE.toPx()
    val badgeGap = 3.dp.toPx()
    val tagPadding = 6.dp.toPx()
    val specs = scene.labels
        .filter { zoom >= it.minZoom - ZOOM_EPSILON || it.building == selected && selected != null }
        .sortedByDescending { it.building != null && it.building == selected }
    val requests = specs.map { spec ->
        val measured = layouts[spec] ?: return@map null
        val anchor = viewport.toScreen(spec.anchor)
        if (anchor.x < 0 || anchor.y < 0 || anchor.x > size.width || anchor.y > size.height) return@map null
        val badgeWidth = if (measured.badge != null) badgeSize + badgeGap else 0f
        val tag = if (spec.building != null && spec.building == selected) 2 * tagPadding else 0f
        CenteredLabelRequest(
            anchor,
            width = badgeWidth + measured.text.size.width + tag,
            height = maxOf(measured.text.size.height.toFloat(), if (measured.badge != null) badgeSize else 0f) + if (tag > 0f) 4.dp.toPx() else 0f,
        )
    }
    val boxes = placeCenteredLabels(requests, size.width, size.height, obstacles, gap = 2.dp.toPx())
    val halo = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
    val placed = boxes.filterNotNull().toMutableList()
    // A numbered building whose name has no room still shows its number, so the crowded middle of the campus reads
    // as ①②③⑤ before zooming in.
    val badgeOnly = placeCenteredLabels(
        specs.mapIndexed { index, spec ->
            val anchor = requests[index]?.anchor
            if (boxes[index] == null && anchor != null && spec.number != null) CenteredLabelRequest(anchor, badgeSize, badgeSize) else null
        },
        size.width,
        size.height,
        obstacles + placed,
    )
    specs.forEachIndexed { index, spec ->
        val box = badgeOnly[index] ?: return@forEachIndexed
        val badge = layouts[spec]?.badge ?: return@forEachIndexed
        val center = Offset(box.left + box.width / 2, box.top + box.height / 2)
        drawCircle(palette.halo, badgeSize / 2 + 1.5.dp.toPx(), center)
        drawCircle(palette.badge, badgeSize / 2, center)
        drawText(badge, palette.onBadge, Offset(center.x - badge.size.width / 2f, center.y - badge.size.height / 2f))
        placed += box
    }
    specs.forEachIndexed { index, spec ->
        val box = boxes[index] ?: return@forEachIndexed
        val measured = layouts[spec] ?: return@forEachIndexed
        val isSelected = spec.building != null && spec.building == selected
        var left = box.left
        if (isSelected) {
            drawRoundRect(palette.selectedTag, Offset(box.left, box.top), Size(box.width, box.height), CornerRadius(box.height / 2))
            left += tagPadding
        }
        measured.badge?.let { badge ->
            val center = Offset(left + badgeSize / 2, box.top + box.height / 2)
            drawCircle(if (isSelected) palette.onSelectedTag else palette.badge, badgeSize / 2, center)
            drawText(badge, if (isSelected) palette.selectedTag else palette.onBadge, Offset(center.x - badge.size.width / 2f, center.y - badge.size.height / 2f))
            left += badgeSize + badgeGap
        }
        val top = box.top + (box.height - measured.text.size.height) / 2
        val ink = when {
            isSelected -> palette.onSelectedTag
            spec.role == MapLabelRole.AREA -> palette.areaInk
            spec.role == MapLabelRole.WATER -> palette.waterInk
            spec.role == MapLabelRole.BUILDING -> palette.inkMuted
            else -> palette.ink
        }
        // A text layout keeps the last drawStyle it was painted with when none is given, so every fill after a halo
        // names Fill; without it the name is stroked in its ink too and reads as a blob.
        if (!isSelected) drawText(measured.text, palette.halo, Offset(left, top), drawStyle = halo)
        drawText(measured.text, ink, Offset(left, top), drawStyle = Fill)
    }
    // Road names along the longest visible stretch of each road, upright, where they fit.
    roads.forEach { (name, layout) ->
        val polylines = scene.namedRoads[name] ?: return@forEach
        val need = layout.size.width + 12.dp.toPx()
        var best: Pair<ScreenPoint, ScreenPoint>? = null
        var bestLength = need
        polylines.forEach { points ->
            points.zipWithNext().forEach { (a, b) ->
                val start = viewport.toScreen(a)
                val end = viewport.toScreen(b)
                val length = hypot(end.x - start.x, end.y - start.y)
                val inside = listOf(start, end).all { it.x in 0f..size.width && it.y in 0f..size.height }
                if (inside && length >= bestLength) {
                    best = start to end
                    bestLength = length
                }
            }
        }
        val (start, end) = best ?: return@forEach
        var degrees = Math.toDegrees(atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())).toFloat()
        if (degrees > 90f) degrees -= 180f
        if (degrees < -90f) degrees += 180f
        val mid = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)
        val radians = Math.toRadians(degrees.toDouble())
        val w = layout.size.width.toFloat()
        val h = layout.size.height.toFloat()
        val boundsWidth = (abs(w * cos(radians)) + abs(h * sin(radians))).toFloat()
        val boundsHeight = (abs(w * sin(radians)) + abs(h * cos(radians))).toFloat()
        val box = MapBox.around(ScreenPoint(mid.x, mid.y), boundsWidth, boundsHeight)
        if (!box.isInside(size.width, size.height) || obstacles.any { it.overlaps(box) } || placed.any { it.overlaps(box) }) return@forEach
        placed += box
        rotate(degrees, pivot = mid) {
            val topLeft = Offset(mid.x - w / 2, mid.y - h / 2)
            drawText(layout, palette.halo, topLeft, drawStyle = halo)
            drawText(layout, palette.inkMuted, topLeft, drawStyle = Fill)
        }
    }
}

/** The scale bar at the bottom start (D-131(5)): the longest round length that fits a third of the width. */
private fun DrawScope.drawScaleBar(
    pixelsPerMeter: Double,
    palette: CampusMapPalette,
    captions: MutableMap<Int, TextLayoutResult>,
    measure: (Int) -> TextLayoutResult,
) {
    val step = scaleBarMeters(pixelsPerMeter, size.width * SCALE_BAR_MAX_FRACTION) ?: return
    val caption = captions.getOrPut(step) { measure(step) }
    val start = SCALE_BAR_INSET.toPx()
    val barY = size.height - SCALE_BAR_INSET.toPx()
    val barEnd = start + (step * pixelsPerMeter).toFloat()
    val tick = 5.dp.toPx()
    val stroke = 2.5.dp.toPx()
    val halo = palette.halo
    listOf(halo to stroke + 2.dp.toPx(), palette.ink to stroke).forEach { (color, width) ->
        drawLine(color, Offset(start, barY), Offset(barEnd, barY), width, StrokeCap.Round)
        drawLine(color, Offset(start, barY - tick), Offset(start, barY), width, StrokeCap.Round)
        drawLine(color, Offset(barEnd, barY - tick), Offset(barEnd, barY), width, StrokeCap.Round)
    }
    val topLeft = Offset(start, barY - tick - 2.dp.toPx() - caption.size.height)
    drawText(caption, halo, topLeft, drawStyle = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
    drawText(caption, palette.ink, topLeft, drawStyle = Fill)
}

/** A small arrow over 북 in the top end corner; the map is always north up (D-131(5)). */
private fun DrawScope.drawNorthMark(palette: CampusMapPalette, caption: TextLayoutResult) {
    val northX = size.width - NORTH_MARK_INSET.toPx()
    val arrowTop = 8.dp.toPx()
    val arrowHeight = 10.dp.toPx()
    val arrow = Path().apply {
        moveTo(northX, arrowTop)
        lineTo(northX + 5.dp.toPx(), arrowTop + arrowHeight)
        lineTo(northX - 5.dp.toPx(), arrowTop + arrowHeight)
        close()
    }
    drawPath(arrow, palette.halo, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
    drawPath(arrow, palette.ink)
    val topLeft = Offset(northX - caption.size.width / 2f, arrowTop + arrowHeight + 1.dp.toPx())
    drawText(caption, palette.halo, topLeft, drawStyle = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
    drawText(caption, palette.ink, topLeft, drawStyle = Fill)
}

/** Where the OpenStreetMap credit sits (bottom end), for keeping labels off it. */
private fun androidx.compose.ui.unit.Density.attributionBox(map: Size, text: Size): MapBox {
    val width = text.width + 8.dp.toPx()
    return MapBox(map.width - width, map.height - text.height, map.width, map.height)
}

/** The map's own elements labels must not cover: scale bar, north mark, zoom buttons, credit and reset button. */
private fun androidx.compose.ui.unit.Density.fixedBoxes(map: Size, attribution: MapBox, resetShown: Boolean): List<MapBox> {
    val controlsWidth = CONTROL_SIZE.toPx() + 2 * CONTROL_INSET.toPx()
    val controlsHeight = 2 * CONTROL_SIZE.toPx() + CONTROL_GAP.toPx() + CONTROL_INSET.toPx()
    return listOfNotNull(
        MapBox(0f, map.height - SCALE_BAR_AREA_HEIGHT.toPx(), SCALE_BAR_INSET.toPx() + map.width * SCALE_BAR_MAX_FRACTION, map.height),
        MapBox(map.width - 2 * NORTH_MARK_INSET.toPx(), 0f, map.width, NORTH_MARK_AREA_HEIGHT.toPx()),
        MapBox(map.width - controlsWidth, attribution.top - controlsHeight, map.width, attribution.top),
        attribution,
        if (resetShown) MapBox(0f, 0f, CONTROL_SIZE.toPx() + 2 * CONTROL_INSET.toPx(), CONTROL_SIZE.toPx() + 2 * CONTROL_INSET.toPx()) else null,
    )
}

/**
 * The map's buttons (D-132): 확대 and 축소 at the bottom end, above the credit, and 캠퍼스 전체 보기 at the top start
 * once the view has left the home view. They are the non-gesture way to zoom the UX guide asks for.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MapControls(
    zoom: () -> Double,
    maxZoom: () -> Double,
    showReset: () -> Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = DimaTheme.colors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = CONTROL_INSET, bottom = CONTROL_INSET + ATTRIBUTION_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(CONTROL_GAP),
    ) {
        FilledTonalIconButton(
            onClick = onZoomIn,
            enabled = zoom() < maxZoom() - ZOOM_EPSILON,
            colors = colors,
            modifier = Modifier.testTag("map_zoom_in"),
        ) { Icon(Icons.Default.Add, contentDescription = "확대") }
        FilledTonalIconButton(
            onClick = onZoomOut,
            enabled = zoom() > 1.0 + ZOOM_EPSILON,
            colors = colors,
            modifier = Modifier.testTag("map_zoom_out"),
        ) { Icon(Icons.Default.Remove, contentDescription = "축소") }
    }
    if (showReset()) {
        FilledTonalIconButton(
            onClick = onReset,
            colors = colors,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CONTROL_INSET)
                .testTag("map_reset"),
        ) { Icon(Icons.Default.ZoomOutMap, contentDescription = "캠퍼스 전체 보기") }
    }
}

/** A 4402 stop's marker: the tertiary role, a rounded square and a bus, so colour is never the only sign (D-131(5)). */
@Composable
private fun PlaceMarker(place: CampusPlace, student: GeoPoint?) {
    val (container, content) = markerColors(place.kind, MaterialTheme.colorScheme)
    val description = campusPlaceSpokenDescription(place, student)
    Surface(
        modifier = Modifier
            .size(PLACE_MARKER_SIZE)
            .testTag("map_marker_${place.id}")
            .clearAndSetSemantics { contentDescription = description },
        shape = markerShape(place.kind, large = false),
        color = container,
        contentColor = content,
        border = BorderStroke(2.dp, DimaTheme.colors.card),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(markerIcon(place.kind), contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

/** The student's dot (D-131(5)): neutral inverse-surface ink with a card-coloured ring, unlike any place marker. */
@Composable
private fun StudentMarker(sample: LocationSample) {
    val description = "내 위치, 오차 ${accuracyText(sample.accuracyMeters)}"
    Surface(
        modifier = Modifier
            .size(STUDENT_MARKER_SIZE)
            .testTag("map_marker_student")
            .clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        border = BorderStroke(3.dp, DimaTheme.colors.card),
    ) {}
}

/** The quiet line under the map: whether and how the student's position is shown (D-131(5)). */
@Composable
private fun LocationStatusLine(position: StudentPosition, shownOnMap: Boolean) {
    val modifier = Modifier
        .testTag("map_location_status")
        .semantics { liveRegion = LiveRegionMode.Polite }
    val text = when (position) {
        StudentPosition.Locating -> null
        StudentPosition.NotAllowed -> "위치 권한이 꺼져 있어요. 설정에서 허용하면 지도에 내 위치와 거리가 함께 보여요."
        StudentPosition.Unknown -> "내 위치를 찾지 못했어요. 이 화면을 다시 열면 다시 찾아볼게요."
        is StudentPosition.Known -> if (shownOnMap) {
            "지도에 내 위치를 함께 표시했어요 · 오차 ${accuracyText(position.sample.accuracyMeters)}"
        } else {
            "지금 위치는 지도 밖이에요. 장소까지의 거리는 아래 목록에 있어요."
        }
    }
    if (text == null) {
        LoadingLine("내 위치를 찾고 있어요", modifier)
    } else {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
}

/** A list section's title and the one line saying what its rows do. */
@Composable
private fun SectionHeader(title: String, hint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSectionHeader(title)
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * The selected building (D-132): the room as the page was asked for it (`덕성관 101호`) or the building's name, its
 * number, OSM's name where it differs, what is on each floor, its sky bridges, and `지도 앱에서 열기`.
 */
@Composable
private fun BuildingCard(
    building: CampusBuilding,
    roomLabel: String?,
    distance: String?,
    onOpenInMapApp: () -> Unit,
    onClose: () -> Unit,
) {
    HomeContainerCard(Modifier.testTag("map_focus_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BuildingBadge(building)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    roomLabel ?: building.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }.testTag("map_focus_title"),
                )
                val details = listOfNotNull(
                    building.number?.let { "${it}번 건물" + if (roomLabel != null) " ${building.displayName}" else "" },
                    distance?.let { "내 위치에서 $it" },
                )
                if (details.isNotEmpty()) {
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose, modifier = Modifier.testTag("map_focus_close")) {
                Icon(Icons.Default.Close, contentDescription = "선택 닫기")
            }
        }
        building.osmName?.let {
            Text("지도에는 ‘$it’로 나와요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (building.floors.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                building.floors.forEach { floor ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (floor.floor.isNotEmpty()) {
                            Text(
                                floor.floor,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(32.dp),
                            )
                        }
                        Text(floor.facilities.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        building.bridgeLines.takeIf { it.isNotEmpty() }?.let { lines ->
            Text(
                "구름다리: " + lines.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            shapes = ButtonDefaults.shapes(),
            onClick = onOpenInMapApp,
            modifier = Modifier.testTag("map_focus_open"),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(OPEN_IN_MAP_APP_LABEL, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A room whose building the app does not know yet (D-132): the note, and the whole campus stays on the map. */
@Composable
private fun UnknownRoomCard(room: RoomLocation.Unknown, onClose: () -> Unit) {
    HomeContainerCard(Modifier.testTag("map_room_unknown")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(room.note, style = MaterialTheme.typography.titleSmall)
                Text(
                    "캠퍼스 전체를 보여 드려요. 아래 건물 목록에서 골라 볼 수도 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "안내 닫기")
            }
        }
    }
}

/** A school building in the list (D-132): the whole row shows it on the map. */
@Composable
private fun CampusBuildingRow(building: CampusBuilding, distance: String?, index: Int, count: Int, onShow: () -> Unit) {
    val summary = building.floors.flatMap { it.facilities }.take(3).joinToString(" · ")
    SegmentedListItem(
        onClick = onShow,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier
            .testTag("map_building_${building.name}")
            .semantics { onClick(label = SHOW_ON_MAP_LABEL) { onShow(); true } },
        leadingContent = { BuildingBadge(building) },
        supportingContent = listOfNotNull(summary.takeIf(String::isNotEmpty), distance?.let { "내 위치에서 $it" })
            .takeIf { it.isNotEmpty() }
            ?.let { lines -> { Text(lines.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        colors = dimaSegmentedListColors(),
    ) {
        Text(building.displayName)
    }
}

/** A building's number in a primary circle, as on the map; buildings without a number show an icon (D-132). */
@Composable
private fun BuildingBadge(building: CampusBuilding) {
    val number = building.number
    Surface(
        modifier = Modifier.size(40.dp).clearAndSetSemantics { contentDescription = building.spokenName },
        shape = CircleShape,
        color = if (number != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (number != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (number != null) {
                Text(number.toString(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            } else {
                Icon(Icons.Default.Apartment, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** One place in the list (D-131(5)): the whole row opens it in the map app, as 전체's link rows open the browser. */
@Composable
private fun CampusPlaceRow(place: CampusPlace, student: GeoPoint?, index: Int, count: Int, onOpen: () -> Unit) {
    SegmentedListItem(
        onClick = onOpen,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        // The row says where it goes, which SegmentedListItem has no parameter for.
        modifier = Modifier
            .testTag("map_place_${place.id}")
            .semantics { onClick(label = OPEN_IN_MAP_APP_LABEL) { onOpen(); true } },
        leadingContent = { PlaceBadge(place.kind) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        supportingContent = { Text(campusPlaceSupportingText(place, student)) },
        colors = dimaSegmentedListColors(),
    ) {
        Text(place.name)
    }
}

/** The row's leading mark: the map marker's colour, shape and icon at list size, so the list doubles as the key. */
@Composable
private fun PlaceBadge(kind: CampusPlaceKind) {
    val (container, content) = markerColors(kind, MaterialTheme.colorScheme)
    Surface(modifier = Modifier.size(40.dp), shape = markerShape(kind, large = true), color = container, contentColor = content) {
        Box(contentAlignment = Alignment.Center) {
            Icon(markerIcon(kind), contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/** Opens [place] in the phone's map app through a `geo:` link, or says that no app can (D-131(5), like [openUrl]). */
private fun openInMapApp(context: Context, place: CampusPlace) = openGeoUri(context, campusPlaceGeoUri(place))

/** Opens a building's middle in the phone's map app (D-132), labelled with its name. */
private fun openPointInMapApp(context: Context, point: GeoPoint, label: String) = openGeoUri(context, geoUri(point, label))

private fun openGeoUri(context: Context, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "지도를 열 앱이 없어요", Toast.LENGTH_SHORT).show()
    }
}

private fun accuracyText(accuracyMeters: Float): String =
    formatDistance(accuracyMeters.toDouble()).let { if (it.endsWith("이내")) it else "약 $it" }

/** Marker roles by kind (D-131(5)): school zones primary, 원룸촌 secondary, 4402 stops tertiary. */
private fun markerColors(kind: CampusPlaceKind, scheme: ColorScheme): Pair<Color, Color> = when (kind) {
    CampusPlaceKind.SCHOOL_ZONE -> scheme.primary to scheme.onPrimary
    CampusPlaceKind.HOUSING_ZONE -> scheme.secondary to scheme.onSecondary
    CampusPlaceKind.BUS_4402_STOP -> scheme.tertiary to scheme.onTertiary
}

private fun markerShape(kind: CampusPlaceKind, large: Boolean): Shape = when (kind) {
    CampusPlaceKind.BUS_4402_STOP -> if (large) DimaShapes.Tile else DimaShapes.Small
    else -> CircleShape
}

private fun markerIcon(kind: CampusPlaceKind): ImageVector = when (kind) {
    CampusPlaceKind.SCHOOL_ZONE -> Icons.Default.School
    CampusPlaceKind.HOUSING_ZONE -> Icons.Default.Home
    CampusPlaceKind.BUS_4402_STOP -> Icons.Default.DirectionsBus
}

/**
 * The base map's colours (D-132), in the conventions of map apps — warm ground, green woods and fields, blue water,
 * white roads with grey casings and yellow main roads, beige footprints — with the school buildings and the selection
 * in the app's primary role. Dark mode keeps the same order of lightness on a dark ground (roads and footprints lighter
 * than the ground), so the map does not turn into one flat tone; names have a ground-coloured halo in both.
 */
@Immutable
private data class CampusMapPalette(
    val ground: Color,
    val wood: Color,
    val green: Color,
    val pitch: Color,
    val pitchLine: Color,
    val water: Color,
    val residential: Color,
    val industrial: Color,
    val campus: Color,
    val plaza: Color,
    val majorFill: Color,
    val majorCasing: Color,
    val roadFill: Color,
    val roadCasing: Color,
    val track: Color,
    val path: Color,
    val stream: Color,
    val building: Color,
    val buildingEdge: Color,
    val schoolBuilding: Color,
    val schoolBuildingEdge: Color,
    val selectedFill: Color,
    val selectedEdge: Color,
    val selectedTag: Color,
    val onSelectedTag: Color,
    val badge: Color,
    val onBadge: Color,
    val ink: Color,
    val inkMuted: Color,
    val areaInk: Color,
    val waterInk: Color,
    val halo: Color,
    val labelBackground: Color,
) {
    fun area(kind: MapAreaKind): Color = when (kind) {
        MapAreaKind.WOOD -> wood
        MapAreaKind.RESIDENTIAL -> residential
        MapAreaKind.INDUSTRIAL -> industrial
        MapAreaKind.CAMPUS -> campus
        MapAreaKind.GREEN -> green
        MapAreaKind.PITCH -> pitch
        MapAreaKind.PLAZA -> plaza
        MapAreaKind.WATER -> water
    }
}

@Composable
private fun campusMapPalette(): CampusMapPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = DimaTheme.colors.page.luminance() < 0.5f
    return remember(scheme, dark) {
        if (dark) {
            val ground = Color(0xFF211D1E)
            CampusMapPalette(
                ground = ground,
                wood = Color(0xFF203427),
                green = Color(0xFF263F2C),
                pitch = Color(0xFF2B4A31),
                pitchLine = Color(0xFF477550),
                water = Color(0xFF1B3A4A),
                residential = Color(0xFF2B2627),
                industrial = Color(0xFF2C2730),
                campus = lerp(ground, scheme.primaryContainer, 0.28f),
                plaza = Color(0xFF332D2F),
                majorFill = Color(0xFF7A6640),
                majorCasing = Color(0xFF4F4229),
                roadFill = Color(0xFF4D4648),
                roadCasing = Color(0xFF2F2A2B),
                track = Color(0xFF7A6E57),
                path = Color(0xFF936F6F),
                stream = Color(0xFF3C7189),
                building = Color(0xFF3D3638),
                buildingEdge = Color(0xFF5A5053),
                schoolBuilding = lerp(Color(0xFF3D3638), scheme.primaryContainer, 0.55f),
                schoolBuildingEdge = scheme.primary.copy(alpha = 0.7f),
                selectedFill = scheme.primary.copy(alpha = 0.35f),
                selectedEdge = scheme.primary,
                selectedTag = scheme.primary,
                onSelectedTag = scheme.onPrimary,
                badge = scheme.primary,
                onBadge = scheme.onPrimary,
                ink = scheme.onSurface,
                inkMuted = scheme.onSurfaceVariant,
                areaInk = Color(0xFFA3D497),
                waterInk = Color(0xFF8FCBE3),
                halo = ground.copy(alpha = 0.92f),
                labelBackground = ground.copy(alpha = 0.85f),
            )
        } else {
            val ground = Color(0xFFF3F0EB)
            CampusMapPalette(
                ground = ground,
                wood = Color(0xFFD3E7C9),
                green = Color(0xFFCBEABD),
                pitch = Color(0xFFB6DFA6),
                pitchLine = Color(0xFF8CC17B),
                water = Color(0xFFA8D3E4),
                residential = Color(0xFFEBE4DE),
                industrial = Color(0xFFE8E1EA),
                campus = lerp(ground, scheme.primaryContainer, 0.35f),
                plaza = Color(0xFFE5DFDC),
                majorFill = Color(0xFFFFE6A8),
                majorCasing = Color(0xFFD4AE62),
                roadFill = Color.White,
                roadCasing = Color(0xFFCDC4BC),
                track = Color(0xFFAF9B74),
                path = Color(0xFFC08E8E),
                stream = Color(0xFF86BED6),
                building = Color(0xFFDCD3CB),
                buildingEdge = Color(0xFFB9ADA4),
                schoolBuilding = lerp(Color(0xFFDCD3CB), scheme.primaryContainer, 0.6f),
                schoolBuildingEdge = scheme.primary.copy(alpha = 0.55f),
                selectedFill = scheme.primary.copy(alpha = 0.3f),
                selectedEdge = scheme.primary,
                selectedTag = scheme.primary,
                onSelectedTag = scheme.onPrimary,
                badge = scheme.primary,
                onBadge = scheme.onPrimary,
                ink = scheme.onSurface,
                inkMuted = scheme.onSurfaceVariant,
                areaInk = Color(0xFF3B6B33),
                waterInk = Color(0xFF28657F),
                halo = ground.copy(alpha = 0.92f),
                labelBackground = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

// App layout decisions for the drawn map (D-131(5), D-132), not Material dimensions.
private val MAP_PADDING = 24.dp
private val MAP_MIN_HEIGHT = 260.dp
private val MAP_MAX_HEIGHT = 480.dp
private val MAP_FALLBACK_WIDTH = 328.dp
private val PLACE_MARKER_SIZE = 26.dp
private val STUDENT_MARKER_SIZE = 18.dp
private val LABEL_GAP = 4.dp
private val BADGE_SIZE = 17.dp
private const val SCALE_BAR_MAX_FRACTION = 0.3f
private val SCALE_BAR_INSET = 12.dp
private val SCALE_BAR_AREA_HEIGHT = 40.dp
private val NORTH_MARK_INSET = 20.dp
private val NORTH_MARK_AREA_HEIGHT = 44.dp
/** The map buttons' layout size: a 40dp button inside its 48dp touch target. */
private val CONTROL_SIZE = 48.dp
private val CONTROL_GAP = 0.dp
private val CONTROL_INSET = 4.dp
private val ATTRIBUTION_HEIGHT = 16.dp
private const val ZOOM_EPSILON = 0.01
