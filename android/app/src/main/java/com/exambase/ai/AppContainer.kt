package com.exambase.ai

import android.content.Context
import com.exambase.ai.academic.AcademicRepository
import com.exambase.ai.auth.AuthRepository
import com.exambase.ai.questions.QuestionRepository
import com.exambase.ai.flashcards.FlashcardRepository
import com.exambase.ai.notes.NoteRepository
import com.exambase.ai.contests.ContestRepository
import com.exambase.ai.analytics.AnalyticsRepository
import com.exambase.ai.search.SearchRepository
import com.exambase.ai.dashboard.DashboardRepository
import com.exambase.ai.ai.AiRepository
import com.exambase.ai.exams.ExamRepository
import com.exambase.ai.quizzes.QuizRepository
import com.exambase.core.datastore.SecureTokenStore
import com.exambase.core.network.HttpExamBaseApi

/** Manual dependency container (Hilt can replace this later without touching screens). */
class AppContainer(context: Context) {
    private val tokenStore = SecureTokenStore(context)
    private val api = HttpExamBaseApi(BuildConfig.API_BASE_URL)
    val authRepository = AuthRepository(api, tokenStore)
    val academicRepository = AcademicRepository(api, authRepository)
    val questionRepository = QuestionRepository(api, authRepository)
    val quizRepository = QuizRepository(api, authRepository)
    val flashcardRepository = FlashcardRepository(api, authRepository)
    val noteRepository = NoteRepository(api, authRepository)
    val contestRepository = ContestRepository(api, authRepository)
    val examRepository = ExamRepository(api, authRepository)
    val analyticsRepository = AnalyticsRepository(api, authRepository)
    val searchRepository = SearchRepository(api, authRepository)
    val dashboardRepository = DashboardRepository(api, authRepository)
    val aiRepository = AiRepository(api, authRepository)
}
