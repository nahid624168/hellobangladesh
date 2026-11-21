package com.exambase.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.exambase.ai.navigation.ExamBaseNavHost
import com.exambase.ai.screens.GuestSessionLayout
import com.exambase.core.designsystem.theme.ExamBaseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as ExamBaseApp).container.authRepository
        setContent {
            val user by repo.currentUser.collectAsState()
            val dark = when (user?.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            ExamBaseTheme(darkTheme = dark) {
                val navController = rememberNavController()
                GuestSessionLayout(repo, navController) { ExamBaseNavHost(navController) }
            }
        }
    }
}
