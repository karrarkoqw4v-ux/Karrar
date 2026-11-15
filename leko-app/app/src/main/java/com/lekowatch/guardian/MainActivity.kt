package com.lekowatch.guardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lekowatch.guardian.navigation.LekoNavGraph
import com.lekowatch.guardian.ui.theme.LekoGuardianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LekoGuardianTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LekoNavGraph()
                }
            }
        }
    }
}
