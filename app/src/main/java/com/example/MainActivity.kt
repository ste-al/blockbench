package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.BlockBenchAppRoot
import com.example.ui.MissingFirebaseConfigScreen
import com.example.ui.theme.BlockBenchTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Firebase auto-initialises from google-services.json. A build made without that file has no
    // default FirebaseApp, and the first Firebase call would crash; explain the problem instead.
    val isFirebaseConfigured = FirebaseApp.getApps(this).isNotEmpty()

    setContent {
      BlockBenchTheme {
        // The Surface paints the themed background (the window background is only a launch
        // placeholder) and provides the matching content colour for every screen below.
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          if (isFirebaseConfigured) BlockBenchAppRoot() else MissingFirebaseConfigScreen()
        }
      }
    }
  }
}
