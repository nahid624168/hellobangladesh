package com.exambase.ai.dashboard

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.DashboardApi
import com.exambase.core.network.DashboardResponse

class DashboardRepository(
    private val api: DashboardApi,
    private val auth: AuthRepository,
) {
    suspend fun dashboard(): ApiResult<DashboardResponse> =
        auth.authed { api.dashboard(it) }
}
