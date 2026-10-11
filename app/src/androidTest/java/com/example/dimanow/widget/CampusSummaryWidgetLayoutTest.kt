package com.example.dimanow.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic widget measurement, independent of launcher data and minute scheduling. */
class CampusSummaryWidgetLayoutTest {
    private val plan = CampusSummaryWidgetPlan(
        headerLocationText = "현재 위치: 본관",
        headerDateText = "9월 16일 (수)",
        courseTitle = "스튜디오기초실습",
        courseDetail = "기예관 122 · 13:00 시작",
        shuttleTitle = "셔틀 (본관 출발)",
        shuttleContent = "엔터관행 5분 · 원룸촌행 12분",
        mealTitle = "학생식당 (운영 중)",
        mealContent = "제육볶음 · 잡곡밥 · 미역국 · 배추김치",
        requiresMinuteUpdate = false,
    )

    @Test
    fun minimumSizeKeepsCourseShuttleAndMealInsideTheirVisibleContainers() {
        verifySummary(widthDp = 250, heightDp = 110, compact = true, name = "summary-widget-minimum")
    }

    @Test
    fun thresholdSizeKeepsEveryDetailedSectionReadable() {
        // The smallest size that still selects the three-panel layout (DESIGN.md Widgets).
        verifySummary(widthDp = 300, heightDp = 180, compact = false, name = "summary-widget-threshold")
    }

    @Test
    fun expandedSizeKeepsDetailedSectionsVisible() {
        verifySummary(widthDp = 400, heightDp = 300, compact = false, name = "summary-widget-expanded")
    }

    @Test
    fun shuttleWidgetRendersToneCapsulesAndCompactLineInBothThemes() {
        val display = ShuttleWidgetDisplayPlan(
            headerTagText = "DIMA 셔틀 · 본관",
            rows = listOf(
                ShuttleWidgetRow(
                    "엔터관행",
                    listOf(
                        ShuttleWidgetCapsule("5분(08:25)", ShuttleWidgetCapsuleTone.NEAREST),
                        ShuttleWidgetCapsule("25분(08:45)", ShuttleWidgetCapsuleTone.LATER),
                    ),
                ),
                ShuttleWidgetRow(
                    "원룸촌행",
                    listOf(
                        ShuttleWidgetCapsule("12분(08:32)", ShuttleWidgetCapsuleTone.NEAREST),
                        ShuttleWidgetCapsule("09:40", ShuttleWidgetCapsuleTone.LAST),
                    ),
                ),
            ),
            spokenSummary = "DIMA 셔틀, 본관 출발. 엔터관행 5분 후 08:25, 다음 25분 후 08:45. 원룸촌행 12분 후 08:32, 다음 09:40 출발, 막차",
        )
        val compactDisplay = display.copy(
            rows = display.rows.map { row -> row.copy(capsules = row.capsules.map { it.copy(text = it.text.substringBefore("(")) }) },
        )
        forEachTheme { context, theme ->
            val wide = render(context, shuttleWidgetViews(context, display, compact = false), 320, 160, "shuttle-widget-4x2-$theme")
            assertEquals(display.spokenSummary, wide.contentDescription)
            val rows = wide.findViewById<ViewGroup>(R.id.widget_rows)
            assertEquals(2, rows.childCount)
            val nearest = rows.getChildAt(0).findViewById<TextView>(R.id.shuttle_row_capsule1)
            assertEquals(context.getColor(R.color.widget_on_accent), nearest.currentTextColor)
            val last = rows.getChildAt(1).findViewById<TextView>(R.id.shuttle_row_capsule2)
            assertEquals(context.getColor(R.color.widget_capsule_last_text), last.currentTextColor)
            listOf(nearest, last).forEach { capsule ->
                assertInsideAncestors(capsule, wide)
                assertTrue("capsule ellipsized: ${capsule.text}", capsule.layout.getEllipsisCount(0) == 0)
            }

            val compact = render(context, shuttleWidgetViews(context, compactDisplay, compact = true), 180, 80, "shuttle-widget-2x1-$theme")
            val line = compact.findViewById<TextView>(R.id.widget_compact_line)
            assertEquals(View.VISIBLE, line.visibility)
            assertEquals("엔터관행 5분  원룸촌행 12분", line.text.toString())
            assertInsideAncestors(line, compact)
            assertTextNotClipped(line)
        }
    }

    @Test
    fun mealWidgetRendersMenuAndSpokenSummaryInBothThemes() {
        val content = MealWidgetContent(
            hoursLabel = "운영 중 · 14:00까지",
            menuText = "제육볶음\n잡곡밥\n미역국\n배추김치",
            stateText = null,
            spokenSummary = "학생식당. 운영 중 · 14:00까지. 제육볶음, 잡곡밥, 미역국, 배추김치",
        )
        forEachTheme { context, theme ->
            val layout = MealWidgetLayoutPlanner().plan(minWidthDp = 180, minHeightDp = 180)
            val root = render(context, mealWidgetViews(context, content, layout), 180, 180, "meal-widget-2x2-$theme")
            assertEquals(content.spokenSummary, root.contentDescription)
            val menu = root.findViewById<TextView>(R.id.meal_widget_menu)
            assertEquals(context.resources.getDimension(R.dimen.widget_text_body), menu.textSize, 0.5f)
            assertInsideAncestors(menu, root)
            val hours = root.findViewById<TextView>(R.id.meal_widget_hours)
            assertTrue("hours ellipsized: ${hours.text}", (0 until hours.lineCount).all { hours.layout.getEllipsisCount(it) == 0 })
            assertTextNotClipped(hours)
        }
    }

    private fun verifySummary(widthDp: Int, heightDp: Int, compact: Boolean, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = render(context, campusSummaryWidgetViews(context, plan, widthDp, heightDp), widthDp, heightDp, name)
        val textIds = if (compact) listOf(R.id.summary_widget_compact_course, R.id.summary_widget_compact_shuttle, R.id.summary_widget_compact_meal)
            else listOf(R.id.summary_widget_course_title, R.id.summary_widget_course_detail,
                R.id.summary_widget_shuttle_content, R.id.summary_widget_meal_content)
        textIds.forEach { id ->
            val text = root.findViewById<TextView>(id)
            assertInsideAncestors(text, root)
            assertTrue("${context.resources.getResourceEntryName(id)} lost its text", text.text.isNotBlank())
            assertTrue("${context.resources.getResourceEntryName(id)} cannot show even one line", text.height >= text.lineHeight)
            assertTextNotClipped(text)
            assertTrue("${context.resources.getResourceEntryName(id)} is below the 12sp data minimum",
                text.textSize >= context.resources.getDimension(R.dimen.widget_text_caption) - 0.5f)
        }
        // The whole widget is one tap target read as a complete summary, even where lines are ellipsized.
        val description = root.contentDescription.toString()
        listOf(plan.courseDetail, plan.shuttleContent, plan.mealContent).forEach { assertTrue(description.contains(it)) }
    }

    private fun assertTextNotClipped(text: TextView) {
        val textHeight = text.layout.height
        val availableHeight = text.height - text.compoundPaddingTop - text.compoundPaddingBottom
        assertTrue("${text.resources.getResourceEntryName(text.id)} clips its text layout: $textHeight > $availableHeight",
            textHeight <= availableHeight)
    }

    private fun forEachTheme(block: (Context, String) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        listOf(Configuration.UI_MODE_NIGHT_NO to "light", Configuration.UI_MODE_NIGHT_YES to "dark").forEach { (night, theme) ->
            val configuration = Configuration(base.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            instrumentation.runOnMainSync { block(base.createConfigurationContext(configuration), theme) }
        }
    }

    private fun render(context: Context, views: RemoteViews, widthDp: Int, heightDp: Int, name: String): ViewGroup {
        var root: ViewGroup? = null
        val build = {
            val applied = views.apply(context, null) as ViewGroup
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (heightDp * density).toInt()
            applied.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            applied.layout(0, 0, width, height)
            capture(applied, name)
            root = applied
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) build() else InstrumentationRegistry.getInstrumentation().runOnMainSync(build)
        return root!!
    }

    private fun assertInsideAncestors(view: View, root: ViewGroup) {
        assertTrue("${view.resources.getResourceEntryName(view.id)} has no height", view.height > 0)
        val bounds = Rect(0, 0, view.width, view.height)
        root.offsetDescendantRectToMyCoords(view, bounds)
        var ancestor = view.parent as? ViewGroup
        while (ancestor != null) {
            val ancestorBounds = Rect(0, 0, ancestor.width, ancestor.height)
            if (ancestor != root) root.offsetDescendantRectToMyCoords(ancestor, ancestorBounds)
            assertTrue("${view.resources.getResourceEntryName(view.id)} clipped by ${ancestor.javaClass.simpleName}: $bounds outside $ancestorBounds",
                ancestorBounds.contains(bounds))
            if (ancestor == root) break
            ancestor = ancestor.parent as? ViewGroup
        }
    }

    private fun capture(root: View, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val variant = InstrumentationRegistry.getArguments().getString("visualVariant") ?: "phone"
        val directory = File(context.getExternalFilesDir(null), "m3-review/$variant").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
