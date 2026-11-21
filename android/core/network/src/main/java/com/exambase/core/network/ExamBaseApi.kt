package com.exambase.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** Contract used by repositories; tests supply a fake. */
interface ExamBaseApi {
    suspend fun register(req: RegisterRequest): ApiResult<AuthResponse>
    suspend fun login(req: LoginRequest): ApiResult<AuthResponse>
    suspend fun guestLogin(req: GuestRequest): ApiResult<AuthResponse> =
        ApiResult.Failure(ApiFailure(FailureKind.OTHER, "not_supported", "Guest mode is not available."))
    suspend fun refresh(refreshToken: String): ApiResult<AuthResponse>
    suspend fun logout(accessToken: String): ApiResult<MessageResponse>
    suspend fun verifyEmail(req: VerifyEmailRequest): ApiResult<MessageResponse>
    suspend fun resendVerification(accessToken: String): ApiResult<MessageResponse>
    suspend fun requestPasswordReset(email: String): ApiResult<MessageResponse>
    suspend fun confirmPasswordReset(req: PasswordResetConfirmRequest): ApiResult<MessageResponse>
    suspend fun me(accessToken: String): ApiResult<UserDto>
    suspend fun updateProfile(accessToken: String, req: UpdateProfileRequest): ApiResult<UserDto>
    suspend fun deleteAccount(accessToken: String, password: String): ApiResult<MessageResponse>
    suspend fun listSessions(accessToken: String): ApiResult<List<SessionDto>>
    suspend fun revokeSession(accessToken: String, sessionId: String): ApiResult<MessageResponse>
    suspend fun uploadAvatar(accessToken: String, req: UploadAvatarRequest): ApiResult<UserDto>
    suspend fun clearAvatar(accessToken: String): ApiResult<UserDto>
    suspend fun exportData(accessToken: String): ApiResult<DataExportDto>
}

/** Academic content (Steps 9-12). Separate from [ExamBaseApi] so existing fakes keep compiling. */
interface AcademicApi {
    suspend fun subjects(
        accessToken: String,
        search: String?,
        sort: String,
        featuredOnly: Boolean,
        limit: Int,
        cursor: String?,
    ): ApiResult<PageResponse<SubjectSummary>>
    suspend fun subject(accessToken: String, id: String): ApiResult<SubjectDetail>
    suspend fun category(accessToken: String, id: String): ApiResult<CategoryDetail>
    suspend fun subcategory(accessToken: String, id: String): ApiResult<SubcategoryDetail>
    suspend fun lesson(accessToken: String, id: String): ApiResult<LessonDetail>
    suspend fun recordView(accessToken: String, lessonId: String): ApiResult<MessageResponse>
    suspend fun setBookmark(accessToken: String, lessonId: String, bookmarked: Boolean): ApiResult<MessageResponse>
    suspend fun setProgress(accessToken: String, lessonId: String, status: String): ApiResult<MessageResponse>
    suspend fun bookmarks(accessToken: String, limit: Int, cursor: String?): ApiResult<PageResponse<LessonSummary>>
    suspend fun recentLessons(accessToken: String, limit: Int): ApiResult<List<LessonSummary>>
}

/** Question bank and practice (Step 14). Separate interface so existing fakes keep compiling. */
interface QuestionApi {
    suspend fun questions(accessToken: String, lessonId: String, limit: Int, cursor: String?): ApiResult<PageResponse<QuestionSummary>>
    suspend fun searchQuestions(
        accessToken: String,
        q: String? = null,
        lessonId: String? = null,
        subjectId: String? = null,
        limit: Int = 50,
        cursor: String? = null,
    ): ApiResult<PageResponse<QuestionSummary>>
    suspend fun question(accessToken: String, id: String): ApiResult<QuestionDetail>
    suspend fun checkAnswer(accessToken: String, id: String, req: CheckAnswerRequest): ApiResult<CheckAnswerResponse>
    suspend fun practiceSummary(accessToken: String, lessonId: String): ApiResult<PracticeSummary>
    suspend fun practiceHistory(accessToken: String, lessonId: String?, limit: Int, cursor: String?): ApiResult<PageResponse<PracticeAttemptDto>>
    suspend fun setQuestionBookmark(accessToken: String, id: String, bookmarked: Boolean): ApiResult<Unit>
    suspend fun questionBookmarks(accessToken: String, limit: Int, cursor: String?): ApiResult<PageResponse<QuestionBookmarkItem>>
    suspend fun reportQuestion(accessToken: String, id: String, req: ReportQuestionRequest): ApiResult<QuestionReportDto>
}

/** Quiz sets and server-scored attempts (Step 15 API, used by Step 16). Separate interface so existing fakes keep compiling. */
interface QuizApi {
    suspend fun quizSets(
        accessToken: String,
        sort: String,
        limit: Int,
        cursor: String?,
        q: String? = null,
        status: String? = null,
        lessonId: String? = null,
    ): ApiResult<PageResponse<QuizSetListItem>>
    suspend fun quizSet(accessToken: String, id: String): ApiResult<QuizSetDetail>
    suspend fun createQuizSet(accessToken: String, req: CreateQuizSetRequest): ApiResult<QuizSet>
    suspend fun updateQuizSet(accessToken: String, id: String, req: UpdateQuizSetRequest): ApiResult<QuizSet>
    suspend fun deleteQuizSet(accessToken: String, id: String): ApiResult<Unit>
    suspend fun duplicateQuizSet(accessToken: String, id: String): ApiResult<QuizSet>
    suspend fun setQuizQuestions(accessToken: String, id: String, req: SetQuestionsRequest): ApiResult<QuizSetDetail>
    suspend fun quizAttempts(accessToken: String, quizSetId: String, limit: Int, cursor: String?): ApiResult<PageResponse<QuizAttemptSummary>>
    suspend fun startQuizAttempt(accessToken: String, quizSetId: String): ApiResult<QuizAttemptView>
    suspend fun quizAttempt(accessToken: String, attemptId: String): ApiResult<QuizAttemptView>
    suspend fun submitQuizAttempt(accessToken: String, attemptId: String, req: SubmitQuizRequest): ApiResult<QuizResult>
}

/** Flashcard sets and spaced-repetition study (Step 18 API, used by Step 19). Separate from QuizApi. */
interface FlashcardApi {
    suspend fun flashcardSets(
        accessToken: String,
        sort: String,
        limit: Int,
        cursor: String?,
        q: String? = null,
        status: String? = null,
    ): ApiResult<PageResponse<FlashcardSetListItem>>
    suspend fun flashcardSet(accessToken: String, id: String): ApiResult<FlashcardSetDetail>
    suspend fun createFlashcardSet(accessToken: String, req: CreateFlashcardSetRequest): ApiResult<FlashcardSet>
    suspend fun updateFlashcardSet(accessToken: String, id: String, req: UpdateFlashcardSetRequest): ApiResult<FlashcardSet>
    suspend fun deleteFlashcardSet(accessToken: String, id: String): ApiResult<Unit>
    suspend fun duplicateFlashcardSet(accessToken: String, id: String): ApiResult<FlashcardSet>
    suspend fun addFlashcard(accessToken: String, setId: String, req: CreateFlashcardRequest): ApiResult<Flashcard>
    suspend fun updateFlashcard(accessToken: String, cardId: String, req: UpdateFlashcardRequest): ApiResult<Flashcard>
    suspend fun deleteFlashcard(accessToken: String, cardId: String): ApiResult<Unit>
    suspend fun dueFlashcards(accessToken: String, setId: String, limit: Int): ApiResult<List<StudyCard>>
    suspend fun reviewFlashcard(accessToken: String, cardId: String, req: ReviewCardRequest): ApiResult<ReviewResult>
    suspend fun flashcardAnalytics(accessToken: String, setId: String): ApiResult<FlashcardSetAnalytics>
}

/** Private notes, folders, versions, sync (Step 20 API, used by Step 21). */
interface NoteApi {
    suspend fun noteFolders(accessToken: String): ApiResult<List<NoteFolder>>
    suspend fun createNoteFolder(accessToken: String, req: CreateNoteFolderRequest): ApiResult<NoteFolder>
    suspend fun updateNoteFolder(accessToken: String, id: String, req: UpdateNoteFolderRequest): ApiResult<NoteFolder>
    suspend fun deleteNoteFolder(accessToken: String, id: String): ApiResult<Unit>
    suspend fun notes(
        accessToken: String,
        folderId: String? = null,
        q: String? = null,
        tag: String? = null,
        pinned: Boolean? = null,
        favorite: Boolean? = null,
        limit: Int = 50,
        cursor: String? = null,
    ): ApiResult<PageResponse<NoteSummary>>
    suspend fun note(accessToken: String, id: String): ApiResult<Note>
    suspend fun createNote(accessToken: String, req: CreateNoteRequest): ApiResult<Note>
    suspend fun updateNote(accessToken: String, id: String, req: UpdateNoteRequest): ApiResult<Note>
    suspend fun deleteNote(accessToken: String, id: String): ApiResult<Unit>
    suspend fun noteVersions(accessToken: String, id: String): ApiResult<List<NoteVersion>>
    suspend fun restoreNoteVersion(accessToken: String, id: String, version: Int): ApiResult<Note>
    suspend fun notesSync(accessToken: String, since: String?, limit: Int): ApiResult<NotesSyncResponse>
}

/** Contests (Steps 24–26). */
interface ContestApi {
    suspend fun contests(accessToken: String, status: String?, limit: Int, cursor: String?): ApiResult<PageResponse<ContestSummary>>
    suspend fun contest(accessToken: String, id: String): ApiResult<ContestDetail>
    suspend fun joinContest(accessToken: String, id: String): ApiResult<JoinContestResponse>
    suspend fun leaveContest(accessToken: String, id: String): ApiResult<MessageResponse>
    suspend fun startContest(accessToken: String, id: String): ApiResult<StartContestResponse>
    suspend fun saveContestAnswer(accessToken: String, id: String, req: SaveContestAnswerRequest): ApiResult<SaveContestAnswerResponse>
    suspend fun submitContest(accessToken: String, id: String): ApiResult<SubmitContestResponse>
    suspend fun contestLeaderboard(accessToken: String, id: String, limit: Int, cursor: String?): ApiResult<LeaderboardPage>
}

/** Exams (Step 27). */
interface ExamApi {
    suspend fun exams(accessToken: String, limit: Int, cursor: String?): ApiResult<PageResponse<ExamSummary>>
    suspend fun exam(accessToken: String, id: String): ApiResult<ExamDetail>
    suspend fun startExam(accessToken: String, id: String): ApiResult<StartExamResponse>
    suspend fun saveExamAnswer(accessToken: String, attemptId: String, req: SaveExamAnswerRequest): ApiResult<SaveExamAnswerResponse>
    suspend fun submitExam(accessToken: String, attemptId: String): ApiResult<ExamResultDto>
    suspend fun examResult(accessToken: String, attemptId: String): ApiResult<ExamResultDto>
}


/** Analytics (Step 28). No client point-grant endpoint. */
interface AnalyticsApi {
    suspend fun analyticsOverview(accessToken: String): ApiResult<AnalyticsOverview>
    suspend fun analyticsTrends(accessToken: String, days: Int): ApiResult<TrendsDto>
    suspend fun analyticsSubjects(accessToken: String): ApiResult<List<SubjectPerformanceDto>>
    suspend fun achievements(accessToken: String): ApiResult<AchievementsDto>
    suspend fun updateDailyGoal(accessToken: String, req: UpdateDailyGoalRequest): ApiResult<AnalyticsOverview>
}

interface DashboardApi {
    suspend fun dashboard(accessToken: String): ApiResult<DashboardResponse>
}

/** AI gateway (Step 32 backend, Step 33 client). The client sends a task name and text only; it never talks to a provider. */
interface AiApi {
    suspend fun aiConsent(accessToken: String): ApiResult<AiConsentDto>
    suspend fun acceptAiConsent(accessToken: String, version: String): ApiResult<AiConsentDto>
    suspend fun revokeAiConsent(accessToken: String): ApiResult<AiConsentDto>
    suspend fun aiQuota(accessToken: String): ApiResult<AiQuotaDto>
    suspend fun aiRun(accessToken: String, req: AiRunRequest): ApiResult<AiRunResponse>
    suspend fun aiConversations(accessToken: String): ApiResult<PageResponse<AiConversationItemDto>>
    suspend fun aiConversation(accessToken: String, id: String): ApiResult<AiConversationDetailDto>
    suspend fun deleteAiConversation(accessToken: String, id: String): ApiResult<Unit>
    suspend fun aiGeneratedQuizSets(accessToken: String): ApiResult<List<AiGeneratedQuizSetDto>>
    suspend fun createAiGeneratedQuiz(accessToken: String, req: AiGeneratedQuizCreateRequest): ApiResult<AiGeneratedQuizDetailDto>
    suspend fun aiGeneratedQuiz(accessToken: String, id: String): ApiResult<AiGeneratedQuizDetailDto>
    suspend fun deleteAiGeneratedQuiz(accessToken: String, id: String): ApiResult<Unit>
}

interface SearchApi {
    suspend fun search(accessToken: String, q: String, types: String?, limit: Int, cursor: String?): ApiResult<SearchResponse>
    suspend fun searchRecent(accessToken: String): ApiResult<RecentSearchesResponse>
    suspend fun searchSuggested(accessToken: String): ApiResult<SuggestedSearchesResponse>
}

class HttpExamBaseApi(
    baseUrl: String,
    private val http: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 120_000
            requestTimeoutMillis = 120_000
        }
    },
) : ExamBaseApi, AcademicApi, QuestionApi, QuizApi, FlashcardApi, NoteApi, ContestApi, ExamApi, AnalyticsApi, SearchApi, DashboardApi, AiApi {
    private val base = baseUrl.trimEnd('/') + "/api/v1"
    val json = Json { ignoreUnknownKeys = true }

    override suspend fun register(req: RegisterRequest) = post<RegisterRequest, AuthResponse>("/auth/register", req)
    override suspend fun login(req: LoginRequest) = post<LoginRequest, AuthResponse>("/auth/login", req)
    override suspend fun guestLogin(req: GuestRequest) = post<GuestRequest, AuthResponse>("/auth/guest", req)
    override suspend fun refresh(refreshToken: String) = post<RefreshRequest, AuthResponse>("/auth/refresh", RefreshRequest(refreshToken))
    override suspend fun logout(accessToken: String) = post<EmptyRequest, MessageResponse>("/auth/logout", EmptyRequest(), accessToken)
    override suspend fun verifyEmail(req: VerifyEmailRequest) = post<VerifyEmailRequest, MessageResponse>("/auth/verify-email", req)
    override suspend fun resendVerification(accessToken: String) =
        post<EmptyRequest, MessageResponse>("/auth/resend-verification", EmptyRequest(), accessToken)
    override suspend fun requestPasswordReset(email: String) =
        post<PasswordResetRequest, MessageResponse>("/auth/password-reset/request", PasswordResetRequest(email))
    override suspend fun confirmPasswordReset(req: PasswordResetConfirmRequest) =
        post<PasswordResetConfirmRequest, MessageResponse>("/auth/password-reset/confirm", req)
    override suspend fun me(accessToken: String) = send<UserDto>(HttpMethod.Get, "/users/me", null, accessToken)
    override suspend fun updateProfile(accessToken: String, req: UpdateProfileRequest) =
        send<UserDto>(HttpMethod.Patch, "/users/me", json.encodeToString(req), accessToken)
    override suspend fun deleteAccount(accessToken: String, password: String) =
        post<DeleteAccountRequest, MessageResponse>("/users/me/delete", DeleteAccountRequest(password), accessToken)
    override suspend fun listSessions(accessToken: String) =
        send<List<SessionDto>>(HttpMethod.Get, "/users/me/sessions", null, accessToken)
    override suspend fun revokeSession(accessToken: String, sessionId: String) =
        send<MessageResponse>(HttpMethod.Delete, "/users/me/sessions/${sessionId.encodeURLParameter()}", null, accessToken)
    override suspend fun uploadAvatar(accessToken: String, req: UploadAvatarRequest) =
        post<UploadAvatarRequest, UserDto>("/users/me/avatar", req, accessToken)
    override suspend fun clearAvatar(accessToken: String) =
        send<UserDto>(HttpMethod.Delete, "/users/me/avatar", null, accessToken)
    override suspend fun exportData(accessToken: String) =
        send<DataExportDto>(HttpMethod.Get, "/users/me/export", null, accessToken)

    override suspend fun subjects(
        accessToken: String, search: String?, sort: String, featuredOnly: Boolean, limit: Int, cursor: String?,
    ) = send<PageResponse<SubjectSummary>>(
        HttpMethod.Get,
        "/academic/subjects" + query("q" to search, "sort" to sort, "featured" to if (featuredOnly) "true" else null, "limit" to limit.toString(), "cursor" to cursor),
        null, accessToken,
    )
    override suspend fun subject(accessToken: String, id: String) = send<SubjectDetail>(HttpMethod.Get, "/academic/subjects/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun category(accessToken: String, id: String) = send<CategoryDetail>(HttpMethod.Get, "/academic/categories/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun subcategory(accessToken: String, id: String) = send<SubcategoryDetail>(HttpMethod.Get, "/academic/subcategories/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun lesson(accessToken: String, id: String) = send<LessonDetail>(HttpMethod.Get, "/academic/lessons/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun recordView(accessToken: String, lessonId: String) =
        send<MessageResponse>(HttpMethod.Post, "/academic/lessons/${lessonId.encodeURLParameter()}/view", "{}", accessToken)
    override suspend fun setBookmark(accessToken: String, lessonId: String, bookmarked: Boolean) = send<MessageResponse>(
        if (bookmarked) HttpMethod.Put else HttpMethod.Delete, "/academic/lessons/${lessonId.encodeURLParameter()}/bookmark", null, accessToken,
    )
    override suspend fun setProgress(accessToken: String, lessonId: String, status: String) = send<MessageResponse>(
        HttpMethod.Put, "/academic/lessons/${lessonId.encodeURLParameter()}/progress", json.encodeToString(SetProgressRequest(status)), accessToken,
    )
    override suspend fun bookmarks(accessToken: String, limit: Int, cursor: String?) =
        send<PageResponse<LessonSummary>>(HttpMethod.Get, "/users/me/bookmarks" + query("limit" to limit.toString(), "cursor" to cursor), null, accessToken)
    override suspend fun recentLessons(accessToken: String, limit: Int) =
        send<List<LessonSummary>>(HttpMethod.Get, "/users/me/recent-lessons" + query("limit" to limit.toString()), null, accessToken)

    override suspend fun questions(accessToken: String, lessonId: String, limit: Int, cursor: String?) =
        send<PageResponse<QuestionSummary>>(
            HttpMethod.Get, "/questions" + query("lessonId" to lessonId, "limit" to limit.toString(), "cursor" to cursor), null, accessToken,
        )
    override suspend fun searchQuestions(
        accessToken: String,
        q: String?,
        lessonId: String?,
        subjectId: String?,
        limit: Int,
        cursor: String?,
    ) =
        send<PageResponse<QuestionSummary>>(
            HttpMethod.Get,
            "/questions" + query(
                "q" to q,
                "lessonId" to lessonId,
                "subjectId" to subjectId,
                "limit" to limit.toString(),
                "cursor" to cursor,
            ),
            null,
            accessToken,
        )
    override suspend fun question(accessToken: String, id: String) =
        send<QuestionDetail>(HttpMethod.Get, "/questions/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun checkAnswer(accessToken: String, id: String, req: CheckAnswerRequest) =
        send<CheckAnswerResponse>(HttpMethod.Post, "/questions/${id.encodeURLParameter()}/check", json.encodeToString(req), accessToken)
    override suspend fun practiceSummary(accessToken: String, lessonId: String) =
        send<PracticeSummary>(HttpMethod.Get, "/users/me/practice-summary" + query("lessonId" to lessonId), null, accessToken)
    override suspend fun practiceHistory(accessToken: String, lessonId: String?, limit: Int, cursor: String?) =
        send<PageResponse<PracticeAttemptDto>>(
            HttpMethod.Get,
            "/users/me/practice-history" + query("lessonId" to lessonId, "limit" to limit.toString(), "cursor" to cursor),
            null,
            accessToken,
        )
    override suspend fun setQuestionBookmark(accessToken: String, id: String, bookmarked: Boolean): ApiResult<Unit> =
        try {
            val path = "/questions/${id.encodeURLParameter()}/bookmark"
            val response: HttpResponse = http.request(base + path) {
                method = if (bookmarked) HttpMethod.Put else HttpMethod.Delete
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (response.status.isSuccess()) ApiResult.Success(Unit)
            else ApiResult.Failure(parseFailure(response.status, response.bodyAsText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiResult.Failure(ApiFailure(FailureKind.NETWORK, "network", "No connection"))
        } catch (e: Exception) {
            ApiResult.Failure(ApiFailure(FailureKind.OTHER, "unexpected", "Unexpected response"))
        }
    override suspend fun questionBookmarks(accessToken: String, limit: Int, cursor: String?) =
        send<PageResponse<QuestionBookmarkItem>>(
            HttpMethod.Get,
            "/users/me/question-bookmarks" + query("limit" to limit.toString(), "cursor" to cursor),
            null,
            accessToken,
        )
    override suspend fun reportQuestion(accessToken: String, id: String, req: ReportQuestionRequest) =
        send<QuestionReportDto>(HttpMethod.Post, "/questions/${id.encodeURLParameter()}/report", json.encodeToString(req), accessToken)

    override suspend fun quizSets(
        accessToken: String,
        sort: String,
        limit: Int,
        cursor: String?,
        q: String?,
        status: String?,
        lessonId: String?,
    ) =
        send<PageResponse<QuizSetListItem>>(
            HttpMethod.Get,
            "/quiz-sets" + query(
                "sort" to sort,
                "limit" to limit.toString(),
                "cursor" to cursor,
                "q" to q,
                "status" to status,
                "lessonId" to lessonId,
            ),
            null,
            accessToken,
        )
    override suspend fun quizSet(accessToken: String, id: String) =
        send<QuizSetDetail>(HttpMethod.Get, "/quiz-sets/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun createQuizSet(accessToken: String, req: CreateQuizSetRequest) =
        send<QuizSet>(HttpMethod.Post, "/quiz-sets", json.encodeToString(req), accessToken)
    override suspend fun updateQuizSet(accessToken: String, id: String, req: UpdateQuizSetRequest) =
        send<QuizSet>(HttpMethod.Patch, "/quiz-sets/${id.encodeURLParameter()}", json.encodeToString(req), accessToken)
    override suspend fun deleteQuizSet(accessToken: String, id: String): ApiResult<Unit> =
        try {
            val response: HttpResponse = http.request(base + "/quiz-sets/${id.encodeURLParameter()}") {
                method = HttpMethod.Delete
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (response.status.isSuccess()) ApiResult.Success(Unit)
            else ApiResult.Failure(parseFailure(response.status, response.bodyAsText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiResult.Failure(ApiFailure(FailureKind.NETWORK, "network", "No connection"))
        } catch (e: Exception) {
            ApiResult.Failure(ApiFailure(FailureKind.OTHER, "unexpected", "Unexpected response"))
        }
    override suspend fun duplicateQuizSet(accessToken: String, id: String) =
        send<QuizSet>(HttpMethod.Post, "/quiz-sets/${id.encodeURLParameter()}/duplicate", "{}", accessToken)
    override suspend fun setQuizQuestions(accessToken: String, id: String, req: SetQuestionsRequest) =
        send<QuizSetDetail>(HttpMethod.Put, "/quiz-sets/${id.encodeURLParameter()}/questions", json.encodeToString(req), accessToken)
    override suspend fun quizAttempts(accessToken: String, quizSetId: String, limit: Int, cursor: String?) =
        send<PageResponse<QuizAttemptSummary>>(
            HttpMethod.Get, "/quiz-sets/${quizSetId.encodeURLParameter()}/attempts" + query("limit" to limit.toString(), "cursor" to cursor), null, accessToken,
        )
    override suspend fun startQuizAttempt(accessToken: String, quizSetId: String) =
        send<QuizAttemptView>(HttpMethod.Post, "/quiz-sets/${quizSetId.encodeURLParameter()}/attempts", "{}", accessToken)
    override suspend fun quizAttempt(accessToken: String, attemptId: String) =
        send<QuizAttemptView>(HttpMethod.Get, "/quiz-attempts/${attemptId.encodeURLParameter()}", null, accessToken)
    override suspend fun submitQuizAttempt(accessToken: String, attemptId: String, req: SubmitQuizRequest) =
        send<QuizResult>(HttpMethod.Post, "/quiz-attempts/${attemptId.encodeURLParameter()}/submit", json.encodeToString(req), accessToken)

    override suspend fun flashcardSets(
        accessToken: String, sort: String, limit: Int, cursor: String?, q: String?, status: String?,
    ) = send<PageResponse<FlashcardSetListItem>>(
        HttpMethod.Get,
        "/flashcard-sets" + query("sort" to sort, "limit" to limit.toString(), "cursor" to cursor, "q" to q, "status" to status),
        null, accessToken,
    )
    override suspend fun flashcardSet(accessToken: String, id: String) =
        send<FlashcardSetDetail>(HttpMethod.Get, "/flashcard-sets/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun createFlashcardSet(accessToken: String, req: CreateFlashcardSetRequest) =
        send<FlashcardSet>(HttpMethod.Post, "/flashcard-sets", json.encodeToString(req), accessToken)
    override suspend fun updateFlashcardSet(accessToken: String, id: String, req: UpdateFlashcardSetRequest) =
        send<FlashcardSet>(HttpMethod.Patch, "/flashcard-sets/${id.encodeURLParameter()}", json.encodeToString(req), accessToken)
    override suspend fun deleteFlashcardSet(accessToken: String, id: String): ApiResult<Unit> =
        try {
            val response: HttpResponse = http.request(base + "/flashcard-sets/${id.encodeURLParameter()}") {
                method = HttpMethod.Delete
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (response.status.isSuccess()) ApiResult.Success(Unit)
            else ApiResult.Failure(parseFailure(response.status, response.bodyAsText()))
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) { ApiResult.Failure(ApiFailure(FailureKind.NETWORK, "network", "No connection")) }
        catch (e: Exception) { ApiResult.Failure(ApiFailure(FailureKind.OTHER, "unexpected", "Unexpected response")) }
    override suspend fun duplicateFlashcardSet(accessToken: String, id: String) =
        send<FlashcardSet>(HttpMethod.Post, "/flashcard-sets/${id.encodeURLParameter()}/duplicate", "{}", accessToken)
    override suspend fun addFlashcard(accessToken: String, setId: String, req: CreateFlashcardRequest) =
        send<Flashcard>(HttpMethod.Post, "/flashcard-sets/${setId.encodeURLParameter()}/cards", json.encodeToString(req), accessToken)
    override suspend fun updateFlashcard(accessToken: String, cardId: String, req: UpdateFlashcardRequest) =
        send<Flashcard>(HttpMethod.Patch, "/flashcards/${cardId.encodeURLParameter()}", json.encodeToString(req), accessToken)
    override suspend fun deleteFlashcard(accessToken: String, cardId: String): ApiResult<Unit> =
        try {
            val response: HttpResponse = http.request(base + "/flashcards/${cardId.encodeURLParameter()}") {
                method = HttpMethod.Delete
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (response.status.isSuccess()) ApiResult.Success(Unit)
            else ApiResult.Failure(parseFailure(response.status, response.bodyAsText()))
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) { ApiResult.Failure(ApiFailure(FailureKind.NETWORK, "network", "No connection")) }
        catch (e: Exception) { ApiResult.Failure(ApiFailure(FailureKind.OTHER, "unexpected", "Unexpected response")) }
    override suspend fun dueFlashcards(accessToken: String, setId: String, limit: Int) =
        send<List<StudyCard>>(HttpMethod.Get, "/flashcard-sets/${setId.encodeURLParameter()}/due" + query("limit" to limit.toString()), null, accessToken)
    override suspend fun reviewFlashcard(accessToken: String, cardId: String, req: ReviewCardRequest) =
        send<ReviewResult>(HttpMethod.Post, "/flashcards/${cardId.encodeURLParameter()}/review", json.encodeToString(req), accessToken)
    override suspend fun flashcardAnalytics(accessToken: String, setId: String) =
        send<FlashcardSetAnalytics>(HttpMethod.Get, "/flashcard-sets/${setId.encodeURLParameter()}/analytics", null, accessToken)

    override suspend fun noteFolders(accessToken: String) =
        send<List<NoteFolder>>(HttpMethod.Get, "/note-folders", null, accessToken)
    override suspend fun createNoteFolder(accessToken: String, req: CreateNoteFolderRequest) =
        send<NoteFolder>(HttpMethod.Post, "/note-folders", json.encodeToString(req), accessToken)
    override suspend fun updateNoteFolder(accessToken: String, id: String, req: UpdateNoteFolderRequest) =
        send<NoteFolder>(HttpMethod.Patch, "/note-folders/${id.encodeURLParameter()}", json.encodeToString(req), accessToken)
    override suspend fun deleteNoteFolder(accessToken: String, id: String): ApiResult<Unit> =
        emptyDelete("/note-folders/${id.encodeURLParameter()}", accessToken)
    override suspend fun notes(
        accessToken: String, folderId: String?, q: String?, tag: String?, pinned: Boolean?, favorite: Boolean?, limit: Int, cursor: String?,
    ) = send<PageResponse<NoteSummary>>(
        HttpMethod.Get,
        "/notes" + query(
            "folderId" to folderId, "q" to q, "tag" to tag,
            "pinned" to pinned?.toString(), "favorite" to favorite?.toString(),
            "limit" to limit.toString(), "cursor" to cursor,
        ),
        null, accessToken,
    )
    override suspend fun note(accessToken: String, id: String) =
        send<Note>(HttpMethod.Get, "/notes/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun createNote(accessToken: String, req: CreateNoteRequest) =
        send<Note>(HttpMethod.Post, "/notes", json.encodeToString(req), accessToken)
    override suspend fun updateNote(accessToken: String, id: String, req: UpdateNoteRequest) =
        send<Note>(HttpMethod.Patch, "/notes/${id.encodeURLParameter()}", json.encodeToString(req), accessToken)
    override suspend fun deleteNote(accessToken: String, id: String): ApiResult<Unit> =
        emptyDelete("/notes/${id.encodeURLParameter()}", accessToken)
    override suspend fun noteVersions(accessToken: String, id: String) =
        send<List<NoteVersion>>(HttpMethod.Get, "/notes/${id.encodeURLParameter()}/versions", null, accessToken)
    override suspend fun restoreNoteVersion(accessToken: String, id: String, version: Int) =
        send<Note>(HttpMethod.Post, "/notes/${id.encodeURLParameter()}/versions/$version/restore", "{}", accessToken)
    override suspend fun notesSync(accessToken: String, since: String?, limit: Int) =
        send<NotesSyncResponse>(HttpMethod.Get, "/notes/sync" + query("since" to since, "limit" to limit.toString()), null, accessToken)

    override suspend fun contests(accessToken: String, status: String?, limit: Int, cursor: String?) =
        send<PageResponse<ContestSummary>>(
            HttpMethod.Get,
            "/contests" + query("status" to status, "limit" to limit.toString(), "cursor" to cursor),
            null,
            accessToken,
        )

    override suspend fun contest(accessToken: String, id: String) =
        send<ContestDetail>(HttpMethod.Get, "/contests/${id.encodeURLParameter()}", null, accessToken)

    override suspend fun joinContest(accessToken: String, id: String) =
        post<EmptyRequest, JoinContestResponse>("/contests/${id.encodeURLParameter()}/join", EmptyRequest(), accessToken)

    override suspend fun leaveContest(accessToken: String, id: String) =
        post<EmptyRequest, MessageResponse>("/contests/${id.encodeURLParameter()}/leave", EmptyRequest(), accessToken)

    override suspend fun startContest(accessToken: String, id: String) =
        post<EmptyRequest, StartContestResponse>("/contests/${id.encodeURLParameter()}/start", EmptyRequest(), accessToken)

    override suspend fun saveContestAnswer(accessToken: String, id: String, req: SaveContestAnswerRequest) =
        post<SaveContestAnswerRequest, SaveContestAnswerResponse>("/contests/${id.encodeURLParameter()}/answers", req, accessToken)

    override suspend fun submitContest(accessToken: String, id: String) =
        post<EmptyRequest, SubmitContestResponse>("/contests/${id.encodeURLParameter()}/submit", EmptyRequest(), accessToken)

    override suspend fun contestLeaderboard(accessToken: String, id: String, limit: Int, cursor: String?) =
        send<LeaderboardPage>(
            HttpMethod.Get,
            "/contests/${id.encodeURLParameter()}/leaderboard" + query("limit" to limit.toString(), "cursor" to cursor),
            null,
            accessToken,
        )

    override suspend fun exams(accessToken: String, limit: Int, cursor: String?) =
        send<PageResponse<ExamSummary>>(
            HttpMethod.Get,
            "/exams" + query("limit" to limit.toString(), "cursor" to cursor),
            null,
            accessToken,
        )

    override suspend fun exam(accessToken: String, id: String) =
        send<ExamDetail>(HttpMethod.Get, "/exams/${id.encodeURLParameter()}", null, accessToken)

    override suspend fun startExam(accessToken: String, id: String) =
        post<EmptyRequest, StartExamResponse>("/exams/${id.encodeURLParameter()}/start", EmptyRequest(), accessToken)

    override suspend fun saveExamAnswer(accessToken: String, attemptId: String, req: SaveExamAnswerRequest) =
        post<SaveExamAnswerRequest, SaveExamAnswerResponse>(
            "/exam-attempts/${attemptId.encodeURLParameter()}/answers",
            req,
            accessToken,
        )

    override suspend fun submitExam(accessToken: String, attemptId: String) =
        post<EmptyRequest, ExamResultDto>("/exam-attempts/${attemptId.encodeURLParameter()}/submit", EmptyRequest(), accessToken)

    override suspend fun examResult(accessToken: String, attemptId: String) =
        send<ExamResultDto>(HttpMethod.Get, "/exam-attempts/${attemptId.encodeURLParameter()}/result", null, accessToken)


    override suspend fun dashboard(accessToken: String) =
        send<DashboardResponse>(HttpMethod.Get, "/dashboard", null, accessToken)

    override suspend fun search(accessToken: String, q: String, types: String?, limit: Int, cursor: String?) =
        send<SearchResponse>(
            HttpMethod.Get,
            "/search" + query("q" to q, "types" to types, "limit" to limit.toString(), "cursor" to cursor),
            null,
            accessToken,
        )

    override suspend fun aiConsent(accessToken: String) = send<AiConsentDto>(HttpMethod.Get, "/ai/consent", null, accessToken)
    override suspend fun acceptAiConsent(accessToken: String, version: String) =
        post<AiAcceptConsentRequest, AiConsentDto>("/ai/consent", AiAcceptConsentRequest(version), accessToken)
    override suspend fun revokeAiConsent(accessToken: String) = send<AiConsentDto>(HttpMethod.Delete, "/ai/consent", null, accessToken)
    override suspend fun aiQuota(accessToken: String) = send<AiQuotaDto>(HttpMethod.Get, "/ai/quota", null, accessToken)
    override suspend fun aiRun(accessToken: String, req: AiRunRequest) = post<AiRunRequest, AiRunResponse>("/ai/run", req, accessToken)
    override suspend fun aiConversations(accessToken: String) = send<PageResponse<AiConversationItemDto>>(HttpMethod.Get, "/ai/conversations", null, accessToken)
    override suspend fun aiConversation(accessToken: String, id: String) = send<AiConversationDetailDto>(HttpMethod.Get, "/ai/conversations/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun deleteAiConversation(accessToken: String, id: String) = emptyDelete("/ai/conversations/${id.encodeURLParameter()}", accessToken)
    override suspend fun aiGeneratedQuizSets(accessToken: String) = send<List<AiGeneratedQuizSetDto>>(HttpMethod.Get, "/ai/quiz-sets", null, accessToken)
    override suspend fun createAiGeneratedQuiz(accessToken: String, req: AiGeneratedQuizCreateRequest) = post<AiGeneratedQuizCreateRequest, AiGeneratedQuizDetailDto>("/ai/quiz-sets", req, accessToken)
    override suspend fun aiGeneratedQuiz(accessToken: String, id: String) = send<AiGeneratedQuizDetailDto>(HttpMethod.Get, "/ai/quiz-sets/${id.encodeURLParameter()}", null, accessToken)
    override suspend fun deleteAiGeneratedQuiz(accessToken: String, id: String) = emptyDelete("/ai/quiz-sets/${id.encodeURLParameter()}", accessToken)

    override suspend fun searchRecent(accessToken: String) =
        send<RecentSearchesResponse>(HttpMethod.Get, "/search/recent", null, accessToken)

    override suspend fun searchSuggested(accessToken: String) =
        send<SuggestedSearchesResponse>(HttpMethod.Get, "/search/suggested", null, accessToken)

    private suspend fun emptyDelete(path: String, accessToken: String): ApiResult<Unit> =
        try {
            val response: HttpResponse = http.request(base + path) {
                method = HttpMethod.Delete
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            if (response.status.isSuccess()) ApiResult.Success(Unit)
            else ApiResult.Failure(parseFailure(response.status, response.bodyAsText()))
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) { ApiResult.Failure(ApiFailure(FailureKind.NETWORK, "network", "No connection")) }
        catch (e: Exception) { ApiResult.Failure(ApiFailure(FailureKind.OTHER, "unexpected", "Unexpected response")) }

    /** Builds "?a=b&c=d" from the non-null values, percent-encoding both sides. */
    private fun query(vararg pairs: Pair<String, String?>): String {
        val parts = pairs.mapNotNull { (k, v) -> v?.let { "${k.encodeURLParameter()}=${it.encodeURLParameter()}" } }
        return if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
    }

    private suspend inline fun <reified Req, reified Res> post(path: String, body: Req, token: String? = null): ApiResult<Res> =
        send(HttpMethod.Post, path, json.encodeToString(body), token)

    private suspend inline fun <reified Res> send(method: HttpMethod, path: String, bodyJson: String?, token: String?): ApiResult<Res> =
        try {
            val response: HttpResponse = http.request(base + path) {
                this.method = method
                if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                if (bodyJson != null) setBody(TextContent(bodyJson, ContentType.Application.Json))
            }
            val text = response.bodyAsText()
            if (response.status.isSuccess()) ApiResult.Success(json.decodeFromString<Res>(text))
            else ApiResult.Failure(parseFailure(response.status, text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ApiResult.Failure(ApiFailure(FailureKind.NETWORK, "network", "No connection"))
        } catch (e: Exception) {
            ApiResult.Failure(ApiFailure(FailureKind.OTHER, "unexpected", "Unexpected response"))
        }

    fun parseFailure(status: HttpStatusCode, text: String): ApiFailure {
        val body = runCatching { json.decodeFromString<ApiErrorEnvelope>(text).error }.getOrNull()
        val kind = when (status) {
            HttpStatusCode.Unauthorized -> FailureKind.UNAUTHORIZED
            HttpStatusCode.TooManyRequests -> FailureKind.RATE_LIMITED
            HttpStatusCode.BadRequest -> FailureKind.VALIDATION
            else -> FailureKind.OTHER
        }
        return ApiFailure(kind, body?.code ?: "http_${status.value}", body?.message ?: "Request failed", body?.fields ?: emptyMap())
    }
}
