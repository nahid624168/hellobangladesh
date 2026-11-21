package com.exambase.ai.contests

import com.exambase.ai.auth.AuthRepository
import com.exambase.core.network.ApiResult
import com.exambase.core.network.ContestApi
import com.exambase.core.network.ContestDetail
import com.exambase.core.network.ContestSummary
import com.exambase.core.network.JoinContestResponse
import com.exambase.core.network.LeaderboardPage
import com.exambase.core.network.MessageResponse
import com.exambase.core.network.PageResponse
import com.exambase.core.network.SaveContestAnswerRequest
import com.exambase.core.network.SaveContestAnswerResponse
import com.exambase.core.network.StartContestResponse
import com.exambase.core.network.SubmitContestResponse

interface ContestSource {
    suspend fun contests(status: String?, cursor: String?, limit: Int = PAGE_SIZE): ApiResult<PageResponse<ContestSummary>>
    suspend fun contest(id: String): ApiResult<ContestDetail>
    suspend fun join(id: String): ApiResult<JoinContestResponse>
    suspend fun leave(id: String): ApiResult<MessageResponse>
    suspend fun start(id: String): ApiResult<StartContestResponse>
    suspend fun saveAnswer(id: String, req: SaveContestAnswerRequest): ApiResult<SaveContestAnswerResponse>
    suspend fun submit(id: String): ApiResult<SubmitContestResponse>
    suspend fun leaderboard(id: String, cursor: String?, limit: Int = 20): ApiResult<LeaderboardPage>

    companion object {
        const val PAGE_SIZE = 20
    }
}

class ContestRepository(
    private val api: ContestApi,
    private val auth: AuthRepository,
) : ContestSource {
    override suspend fun contests(status: String?, cursor: String?, limit: Int) =
        auth.authed { api.contests(it, status, limit, cursor) }

    override suspend fun contest(id: String) = auth.authed { api.contest(it, id) }

    override suspend fun join(id: String) = auth.authed { api.joinContest(it, id) }

    override suspend fun leave(id: String) = auth.authed { api.leaveContest(it, id) }

    override suspend fun start(id: String) = auth.authed { api.startContest(it, id) }

    override suspend fun saveAnswer(id: String, req: SaveContestAnswerRequest) =
        auth.authed { api.saveContestAnswer(it, id, req) }

    override suspend fun submit(id: String) = auth.authed { api.submitContest(it, id) }

    override suspend fun leaderboard(id: String, cursor: String?, limit: Int) =
        auth.authed { api.contestLeaderboard(it, id, limit, cursor) }
}
