package com.example.unmask.core

import com.example.unmask.features.ar.*
import com.example.unmask.features.auth.*
import com.example.unmask.features.game.*
import com.example.unmask.features.lobby.*
import com.example.unmask.features.online.*
import com.example.unmask.features.profile.*


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.unmask.core.theme.UNMASKTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      UNMASKTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
