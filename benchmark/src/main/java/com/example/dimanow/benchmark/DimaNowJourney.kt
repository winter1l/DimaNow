package com.example.dimanow.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.example.dimanow"

internal fun MacrobenchmarkScope.runDimaNowJourney() {
    pressHome()
    startActivityAndWait()
    dismissFirstRunDialogs(device)
    openTab(device, "SHUTTLE")
    device.swipe(
        device.displayWidth * 3 / 4,
        device.displayHeight * 3 / 5,
        device.displayWidth / 4,
        device.displayHeight * 3 / 5,
        12,
    )
    openTab(device, "MEAL")
    openTab(device, "SETTINGS")
    openTab(device, "DASHBOARD")
}

private fun dismissFirstRunDialogs(device: UiDevice) {
    device.wait(Until.findObject(By.text("예인관")), 10_000)?.click()
    device.wait(Until.findObject(By.text("완료")), 10_000)?.click()
    device.waitForIdle()
}

private fun openTab(device: UiDevice, label: String) {
    checkNotNull(device.wait(Until.findObject(By.res("nav_$label")), 5_000)) {
        "Bottom navigation tab was not found: $label"
    }.click()
    device.waitForIdle()
}
