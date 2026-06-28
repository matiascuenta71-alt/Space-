package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.SpaceViewModel
import com.example.ui.components.SpaceDashboard
import com.example.ui.theme.SpaceTheme

class MainActivity : ComponentActivity() {
    
    private val viewModel: SpaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            val accentColorName by viewModel.accentColorName.collectAsState()

            SpaceTheme(themeName = appTheme, accentColorName = accentColorName) {
                SpaceDashboard(viewModel = viewModel)
            }
        }
    }
}
