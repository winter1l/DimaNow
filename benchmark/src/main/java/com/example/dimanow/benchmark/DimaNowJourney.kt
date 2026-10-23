package com.example.dimanow.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "io.github.winter1l.dimanow"

/** Cold start to the first Home frame: the only part that goes into the startup profile. */
internal fun MacrobenchmarkScope.startDimaNow() {
    pressHome()
    startActivityAndWait()
    dismissFirstRunDialogs(device)
}

internal fun MacrobenchmarkScope.runDimaNowJourney() {
    startDimaNow()
    openTab(device, "SHUTTLE")
    device.swipe(
        device.displayWidth * 3 / 4,
        device.displayHeight * 3 / 5,
        device.displayWidth / 4,
        device.displayHeight * 3 / 5,
        12,
    )
    openTab(device, "MEAL")
    openTab(device, "COURSES")
    // D-110: 시간표, 기숙사 and 설정 open from 전체 and go back to it. 장비 대여 is left out: opening it reads DIMA C.
    openTab(device, "ALL")
    openFromAll(device, "all_timetable")
    openFromAll(device, "all_dorm")
    openFromAll(device, "all_settings")
    openTab(device, "DASHBOARD")
}

private fun dismissFirstRunDialogs(device: UiDevice) {
    device.wait(Until.findObject(By.text("예인관")), 10_000)?.click()
    device.wait(Until.findObject(By.text("완료")), 10_000)?.click()
    device.waitForIdle()
}

private fun openTab(device: UiDevice, label: String) = clickRes(device, "nav_$label")

private fun openFromAll(device: UiDevice, resourceId: String) {
    clickRes(device, resourceId)
    device.pressBack()
    checkNotNull(device.wait(Until.findObject(By.res("all_list")), 5_000)) { "Back did not return to 전체" }
}

private fun clickRes(device: UiDevice, resourceId: String) {
    checkNotNull(device.wait(Until.findObject(By.res(resourceId)), 5_000)) {
        "UI element was not found: $resourceId"
    }.click()
    device.waitForIdle()
}
