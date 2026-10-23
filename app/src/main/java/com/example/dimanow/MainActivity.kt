package com.example.dimanow

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.dimanow.theme.DIMANowTheme
import com.example.dimanow.ui.motion.ProvideReducedMotion
import com.example.dimanow.ui.DimaNowApp

class MainActivity : ComponentActivity() {
  override fun onStart() {
    super.onStart()
    lifecycleScope.launch {
      com.example.dimanow.work.StudentMealSync.refresh(
        this@MainActivity,
        (application as DimaNowApplication).mealSource,
        com.example.dimanow.meal.MealRefreshTrigger.FOREGROUND,
      )
    }
  }

  // 값 + nonce 쌍이라 같은 위젯을 연달아 탭해도 매번 새 이벤트로 전달된다 (D-044 #7)
  private var targetPageEvent by mutableStateOf<Pair<String, Long>?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // D-104 (supersedes D-054 on large screens): the manifest keeps phones in portrait; a large screen
    // (smallest width 600dp+, where the app shows its navigation rail) follows the device. Android 16
    // already ignores the manifest lock there for apps targeting API 36; this covers Android 12–15.
    if (resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    // A recreated activity restores its saved tab; only a fresh launch follows the launch intent's deep link.
    if (savedInstanceState == null) {
      intent.getStringExtra("TARGET_PAGE")?.let { targetPageEvent = it to System.nanoTime() }
    }

    enableEdgeToEdge()
    setContent {
      val application = application as DimaNowApplication
      DIMANowTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          ProvideReducedMotion {
            DimaNowApp(
              repository = application.repository,
              preferences = application.preferences,
              shuttleSource = application.shuttleSource,
              mealSource = application.mealSource,
              noticeSource = application.noticeSource,
              lmsCredentialStore = application.lmsCredentialStore,
              lmsSessionController = application.lmsSessionController,
              lmsLoginBridge = application.lmsLoginBridge,
              lmsRenderedPageBridge = application.lmsRenderedPageBridge,
              lmsAutoLoginCoordinator = application.lmsAutoLoginCoordinator,
              lmsSource = application.lmsSource,
              liveSurfaceController = application.liveSurfaceController,
              appUpdateCoordinator = application.appUpdateCoordinator,
              targetPageEvent = targetPageEvent,
              schoolServiceBridge = application.schoolServiceBridge,
            )
          }
        }
      }
    }
  }

  internal companion object {
    const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra("TARGET_PAGE")?.let { targetPageEvent = it to System.nanoTime() }
  }
}
