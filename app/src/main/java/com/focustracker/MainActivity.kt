package com.focustracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.focustracker.ui.FocusViewModel
import com.focustracker.ui.HomeScreen
import com.focustracker.ui.theme.FocusTrackerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FocusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusTrackerTheme {
                HomeScreen(viewModel)
            }
        }
    }
}
