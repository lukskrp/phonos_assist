package com.phonosassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonosassist.ui.MainScreen
import com.phonosassist.ui.theme.PhonosAssistTheme
import com.phonosassist.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhonosAssistTheme {
                val viewModel: MainViewModel = viewModel()

                MainScreen(viewModel = viewModel)
            }
        }
    }
}
