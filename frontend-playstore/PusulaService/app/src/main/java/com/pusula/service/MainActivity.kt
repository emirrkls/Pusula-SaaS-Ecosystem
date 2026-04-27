package com.pusula.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.pusula.service.ui.auth.AuthViewModel
import com.pusula.service.ui.navigation.PusulaNavGraph
import com.pusula.service.ui.theme.PusulaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PusulaTheme {
                Surface {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    LaunchedEffect(Unit) {
                        authViewModel.tryRestoreSession()
                    }
                    PusulaNavGraph(authViewModel = authViewModel)
                }
            }
        }
    }
}
