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
  private var targetPage by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    targetPage = intent.getStringExtra("TARGET_PAGE")

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
            liveSurfaceController = application.liveSurfaceController,
            appUpdateCoordinator = application.appUpdateCoordinator,
            initialTargetPage = targetPage,
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    targetPage = intent.getStringExtra("TARGET_PAGE")
  }
}
