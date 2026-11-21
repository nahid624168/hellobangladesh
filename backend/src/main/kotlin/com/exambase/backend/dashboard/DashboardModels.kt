package com.exambase.backend.dashboard

import kotlinx.serialization.Serializable

@Serializable
data class DashboardResponse(
    val greetingName: String,
    val emailVerified: Boolean,
    val dailyGoalTarget: Int = 20,
    val dailyGoalProgress: Int = 0,
    val dailyGoalMet: Boolean = false,
    val currentStreakDays: Int = 0,
    val totalPoints: Long = 0,
    val continueLearning: List<ContinueItem> = emptyList(),
    val recentItems: List<RecentItem> = emptyList(),
    val subjectShortcuts: List<SubjectShortcut> = emptyList(),
    val banners: List<BannerDto> = emptyList(),
    val recommendations: List<RecommendItem> = emptyList(),
    val isNewUser: Boolean = false,
)

@Serializable
data class ContinueItem(
    val type: String, // lesson | quiz | exam | contest
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

@Serializable
data class RecentItem(
    val type: String,
    val id: String,
    val title: String,
    val at: String? = null,
)

@Serializable
data class SubjectShortcut(
    val id: String,
    val title: String,
    val titleBn: String? = null,
)

@Serializable
data class BannerDto(
    val id: String,
    val title: String,
    val body: String = "",
    val action: String? = null, // route hint e.g. contests
)

@Serializable
data class RecommendItem(
    val type: String,
    val id: String,
    val title: String,
    val reason: String? = null,
)
