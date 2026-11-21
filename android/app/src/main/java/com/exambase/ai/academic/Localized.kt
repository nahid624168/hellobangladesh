package com.exambase.ai.academic

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Bangla text is used when the device language is Bangla and a Bangla version exists; otherwise English. */
fun pickText(english: String, bangla: String?, language: String): String =
    if (language == "bn" && !bangla.isNullOrBlank()) bangla else english

@Composable
fun localized(english: String, bangla: String?): String =
    pickText(english, bangla, LocalConfiguration.current.locales[0].language)

@Composable
fun currentLanguage(): String = LocalConfiguration.current.locales[0].language
