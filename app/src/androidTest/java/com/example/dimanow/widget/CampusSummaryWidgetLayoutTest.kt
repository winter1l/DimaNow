package com.example.dimanow.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.example.dimanow.R
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic widget measurement, independent of launcher data and minute scheduling. */
class CampusSummaryWidgetLayoutTest {
    @Test
    fun minimumSizeKeepsCourseShuttleAndMealInsideTheirVisibleContainers() {
        verifyWidget(widthDp = 250, heightDp = 110, compact = true, name = "summary-widget-minimum")
    }

    @Test
    fun expandedSizeKeepsDetailedSectionsVisible() {
        verifyWidget(widthDp = 400, heightDp = 300, compact = false, name = "summary-widget-expanded")
    }

    private fun verifyWidget(widthDp: Int, heightDp: Int, compact: Boolean, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val plan = CampusSummaryWidgetPlan(
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
            val root = campusSummaryWidgetViews(context, plan, widthDp, heightDp).apply(context, null) as ViewGroup
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val height = (heightDp * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            capture(root, name)
            val textIds = if (compact) listOf(R.id.summary_widget_compact_course, R.id.summary_widget_compact_shuttle, R.id.summary_widget_compact_meal)
                else listOf(R.id.summary_widget_course_title, R.id.summary_widget_course_detail,
                    R.id.summary_widget_shuttle_content, R.id.summary_widget_meal_content)
            textIds.forEach { id ->
                val text = root.findViewById<TextView>(id)
                assertInsideAncestors(text, root)
                assertTrue("${context.resources.getResourceEntryName(id)} lost its text", text.text.isNotBlank())
                assertTrue("${context.resources.getResourceEntryName(id)} cannot show even one line", text.height >= text.lineHeight)
                val textHeight = text.layout.height
                val availableHeight = text.height - text.compoundPaddingTop - text.compoundPaddingBottom
                assertTrue("${context.resources.getResourceEntryName(id)} clips its text layout: $textHeight > $availableHeight",
                    textHeight <= availableHeight)
            }
            if (compact) {
                assertTrue(root.findViewById<TextView>(R.id.summary_widget_compact_course).contentDescription.contains(plan.courseDetail))
                assertTrue(root.findViewById<TextView>(R.id.summary_widget_compact_shuttle).contentDescription.contains(plan.shuttleContent))
                assertTrue(root.findViewById<TextView>(R.id.summary_widget_compact_meal).contentDescription.contains(plan.mealContent))
            }
        }
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
