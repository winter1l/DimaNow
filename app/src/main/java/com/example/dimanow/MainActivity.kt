package com.example.dimanow

import android.content.Intent
import android.os.Bundle
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
import com.example.dimanow.ui.DimaNowApp

class MainActivity : ComponentActivity() {
  // 값 + nonce 쌍이라 같은 위젯을 연달아 탭해도 매번 새 이벤트로 전달된다 (D-044 #7)
  private var targetPageEvent by mutableStateOf<Pair<String, Long>?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    intent.getStringExtra("TARGET_PAGE")?.let { targetPageEvent = it to System.nanoTime() }

    enableEdgeToEdge()
    setContent {
      val application = application as DimaNowApplication
      DIMANowTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          DimaNowApp(
            repository = application.repository,
            preferences = application.preferences,
            shuttleSource = application.shuttleSource,
            mealSource = application.mealSource,
            noticeSource = application.noticeSource,
            lmsCredentialStore = application.lmsCredentialStore,
            lmsSessionController = application.lmsSessionController,
            lmsLoginBridge = application.lmsLoginBridge,
            lmsAutoLoginCoordinator = application.lmsAutoLoginCoordinator,
            lmsSource = application.lmsSource,
            liveSurfaceController = application.liveSurfaceController,
            appUpdateCoordinator = application.appUpdateCoordinator,
            targetPageEvent = targetPageEvent,
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra("TARGET_PAGE")?.let { targetPageEvent = it to System.nanoTime() }
  }
}
