package com.exambase.ai.analytics

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.AchievementsDto
import com.exambase.core.network.AnalyticsApi
import com.exambase.core.network.AnalyticsOverview
import com.exambase.core.network.ApiResult
import com.exambase.core.network.SubjectPerformanceDto
import com.exambase.core.network.TrendsDto
import com.exambase.core.network.UpdateDailyGoalRequest

interface AnalyticsSource {
    suspend fun overview(): ApiResult<AnalyticsOverview>
    suspend fun trends(days: Int = 30): ApiResult<TrendsDto>
    suspend fun subjects(): ApiResult<List<SubjectPerformanceDto>>
    suspend fun achievements(): ApiResult<AchievementsDto>
    suspend fun updateDailyGoal(target: Int): ApiResult<AnalyticsOverview>
}

class AnalyticsRepository(
    private val api: AnalyticsApi,
    private val auth: AuthRepository,
) : AnalyticsSource {
    override suspend fun overview() = auth.authed { api.analyticsOverview(it) }
    override suspend fun trends(days: Int) = auth.authed { api.analyticsTrends(it, days) }
    override suspend fun subjects() = auth.authed { api.analyticsSubjects(it) }
    override suspend fun achievements() = auth.authed { api.achievements(it) }
    override suspend fun updateDailyGoal(target: Int) =
        auth.authed { api.updateDailyGoal(it, UpdateDailyGoalRequest(target)) }
}
