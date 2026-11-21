package com.exambase.ai.navigation

import android.net.Uri

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.exambase.ai.auth.appContainer
import com.exambase.ai.screens.CategoryScreen
import com.exambase.ai.screens.HomeScreen
import com.exambase.ai.screens.LessonScreen
import com.exambase.ai.screens.LibraryScreen
import com.exambase.ai.screens.LoginScreen
import com.exambase.ai.screens.OnboardingScreen
import com.exambase.ai.screens.PracticeScreen
import com.exambase.ai.screens.PracticeHistoryScreen
import com.exambase.ai.screens.ProfileScreen
import com.exambase.ai.screens.SessionsScreen
import com.exambase.ai.screens.QuizDetailScreen
import com.exambase.ai.screens.QuizListScreen
import com.exambase.ai.screens.QuizPlayerScreen
import com.exambase.ai.screens.QuizResultScreen
import com.exambase.ai.screens.QuizHistoryScreen
import com.exambase.ai.screens.QuizEditorScreen
import com.exambase.ai.screens.QuizQuestionsScreen
import com.exambase.ai.screens.RegisterScreen
import com.exambase.ai.screens.ResetPasswordScreen
import com.exambase.ai.screens.SplashScreen
import com.exambase.ai.screens.SubcategoryScreen
import com.exambase.ai.screens.SubjectDetailScreen
import com.exambase.ai.screens.SubjectsScreen
import com.exambase.ai.screens.VerifyEmailScreen
import com.exambase.ai.screens.FlashcardListScreen
import com.exambase.ai.screens.FlashcardDetailScreen
import com.exambase.ai.screens.FlashcardEditorScreen
import com.exambase.ai.screens.StudyScreen
import com.exambase.ai.screens.NotesListScreen
import com.exambase.ai.screens.NoteEditorScreen
import com.exambase.ai.screens.NoteVersionsScreen
import com.exambase.ai.screens.ContestListScreen
import com.exambase.ai.screens.ContestDetailScreen
import com.exambase.ai.screens.ContestPlayerScreen
import com.exambase.ai.screens.ContestLeaderboardScreen
import com.exambase.ai.screens.ExamListScreen
import com.exambase.ai.screens.ExamDetailScreen
import com.exambase.ai.screens.ExamPlayerScreen
import com.exambase.ai.screens.ExamResultScreen
import com.exambase.ai.screens.AnalyticsScreen
import com.exambase.ai.screens.AchievementsScreen
import com.exambase.ai.screens.SearchScreen
import com.exambase.ai.screens.AiAssistantScreen
import com.exambase.ai.screens.AiContentScreen
import com.exambase.ai.screens.AiTutorScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RESET_PASSWORD = "reset_password"
    const val VERIFY_EMAIL = "verify_email"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val SESSIONS = "sessions"
    const val SUBJECTS = "subjects"
    const val SUBJECT = "subject/{id}"
    const val CATEGORY = "category/{id}"
    const val SUBCATEGORY = "subcategory/{id}"
    const val LESSON = "lesson/{id}"
    const val PRACTICE = "practice/{lessonId}"
    const val PRACTICE_HISTORY = "practice-history"
    const val LIBRARY = "library"
    const val QUIZZES = "quizzes"
    const val FLASHCARDS = "flashcards"
    const val FLASHCARD = "flashcard/{id}"
    const val FLASHCARD_CREATE = "flashcard-create"
    const val FLASHCARD_EDIT = "flashcard/{id}/edit"
    const val FLASHCARD_STUDY = "flashcard/{id}/study"
    const val NOTES = "notes"
    const val NOTE_EDIT = "note/{id}"
    const val NOTE_NEW = "note-new"
    const val NOTE_VERSIONS = "note/{id}/versions"
    const val QUIZ = "quiz/{id}"
    const val QUIZ_PLAYER = "quiz/{id}/play"
    const val QUIZ_HISTORY = "quiz/{id}/history"
    const val QUIZ_RESULT = "quiz-attempt/{attemptId}/result"
    const val CONTESTS = "contests"
    const val CONTEST = "contest/{id}"
    const val CONTEST_PLAY = "contest/{id}/play"
    const val CONTEST_LEADERBOARD = "contest/{id}/leaderboard"
    const val EXAMS = "exams"
    const val EXAM = "exam/{id}"
    const val EXAM_PLAY = "exam/{id}/play"
    const val EXAM_RESULT = "exam-attempt/{attemptId}/result"
    const val ANALYTICS = "analytics"
    const val ACHIEVEMENTS = "achievements"
    const val SEARCH = "search"
    const val AI_ASSISTANT = "ai-assistant"
    const val AI_CONTENT = "ai-content"
    const val AI_TUTOR = "ai-tutor"

    fun subject(id: String) = "subject/$id"
    fun contest(id: String) = "contest/$id"
    fun contestPlay(id: String) = "contest/$id/play"
    fun contestLeaderboard(id: String) = "contest/$id/leaderboard"
    fun exam(id: String) = "exam/$id"
    fun examPlay(id: String) = "exam/$id/play"
    fun examResult(attemptId: String) = "exam-attempt/$attemptId/result"
    fun category(id: String) = "category/$id"
    fun subcategory(id: String) = "subcategory/$id"
    fun lesson(id: String) = "lesson/$id"
    fun practice(lessonId: String) = "practice/$lessonId"
    fun quiz(id: String) = "quiz/$id"
    fun flashcard(id: String) = "flashcard/$id"
    fun aiContentFromSelection(text: String) = "ai-content-from-selection/${Uri.encode(text)}"
}

private fun NavHostController.goClearingBackStack(route: String) {
    navigate(route) { popUpTo(0) { inclusive = true } }
}

@Composable
fun ExamBaseNavHost(navController: NavHostController = rememberNavController()) {
    val repo = LocalContext.current.appContainer().authRepository
    val signedOut: () -> Unit = { navController.goClearingBackStack(Routes.LOGIN) }
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                navController.goClearingBackStack(if (repo.hasSession()) Routes.HOME else Routes.ONBOARDING)
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { navController.goClearingBackStack(Routes.LOGIN) })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { navController.goClearingBackStack(Routes.HOME) },
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.RESET_PASSWORD) },
                onGuest = { navController.goClearingBackStack(Routes.HOME) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = { navController.goClearingBackStack(Routes.VERIFY_EMAIL) },
                onBackToLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.RESET_PASSWORD) {
            ResetPasswordScreen(
                onDone = { navController.goClearingBackStack(Routes.LOGIN) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VERIFY_EMAIL) {
            VerifyEmailScreen(onDone = { navController.goClearingBackStack(Routes.HOME) })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onBrowse = { navController.navigate(Routes.SUBJECTS) },
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenQuizzes = { navController.navigate(Routes.QUIZZES) },
                onOpenFlashcards = { navController.navigate(Routes.FLASHCARDS) },
                onOpenNotes = { navController.navigate(Routes.NOTES) },
                onOpenPracticeHistory = { navController.navigate(Routes.PRACTICE_HISTORY) },
                onOpenContests = { navController.navigate(Routes.CONTESTS) },
                onOpenExams = { navController.navigate(Routes.EXAMS) },
                onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenAi = { navController.navigate(Routes.AI_ASSISTANT) },
                onOpenAiContent = { navController.navigate(Routes.AI_CONTENT) },
                onOpenAiTutor = { navController.navigate(Routes.AI_TUTOR) },
                onOpenLesson = { navController.navigate(Routes.lesson(it)) },
                onOpenSubject = { navController.navigate(Routes.subject(it)) },
                onVerifyEmail = { navController.navigate(Routes.VERIFY_EMAIL) },
                onSignedOut = { navController.goClearingBackStack(Routes.LOGIN) },
            )
        }


        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenHit = { type, id ->
                    when (type) {
                        "lesson" -> navController.navigate(Routes.lesson(id))
                        "quiz" -> navController.navigate(Routes.quiz(id))
                        "flashcard" -> navController.navigate(Routes.flashcard(id))
                        "note" -> navController.navigate("note/$id")
                        "subject" -> navController.navigate(Routes.subject(id))
                        else -> { }
                    }
                },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.AI_ASSISTANT) {
            AiAssistantScreen(onBack = { navController.popBackStack() }, onSignedOut = signedOut)
        }
        composable(Routes.AI_CONTENT) {
            AiContentScreen(onBack = { navController.popBackStack() }, onSignedOut = signedOut)
        }
        composable("ai-content-from-selection/{source}") { entry ->
            AiContentScreen(onBack = { navController.popBackStack() }, onSignedOut = signedOut, initialSource = entry.arguments?.getString("source"))
        }
        composable(Routes.AI_TUTOR) {
            AiTutorScreen(onBack = { navController.popBackStack() }, onSignedOut = signedOut)
        }
        composable(Routes.ANALYTICS) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() },
                onAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.ACHIEVEMENTS) {
            AchievementsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.EXAMS) {
            ExamListScreen(
                onBack = { navController.popBackStack() },
                onOpenExam = { navController.navigate(Routes.exam(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.EXAM) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ExamDetailScreen(
                examId = id,
                onBack = { navController.popBackStack() },
                onStart = { navController.navigate(Routes.examPlay(it)) },
                onResult = { navController.navigate(Routes.examResult(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.EXAM_PLAY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ExamPlayerScreen(
                examId = id,
                onBack = { navController.popBackStack() },
                onFinished = { navController.navigate(Routes.examResult(it)) { popUpTo(Routes.exam(id)) } },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.EXAM_RESULT) { entry ->
            val attemptId = entry.arguments?.getString("attemptId").orEmpty()
            ExamResultScreen(
                attemptId = attemptId,
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.CONTESTS) {
            ContestListScreen(
                onBack = { navController.popBackStack() },
                onOpenContest = { navController.navigate(Routes.contest(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.CONTEST) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ContestDetailScreen(
                contestId = id,
                onBack = { navController.popBackStack() },
                onPlay = { navController.navigate(Routes.contestPlay(it)) },
                onLeaderboard = { navController.navigate(Routes.contestLeaderboard(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.CONTEST_PLAY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ContestPlayerScreen(
                contestId = id,
                onBack = { navController.popBackStack() },
                onFinished = { navController.navigate(Routes.contestLeaderboard(it)) { popUpTo(Routes.contest(id)) } },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.CONTEST_LEADERBOARD) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ContestLeaderboardScreen(
                contestId = id,
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onVerifyEmail = { navController.navigate(Routes.VERIFY_EMAIL) },
                onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                onSignedOut = { navController.goClearingBackStack(Routes.LOGIN) },
            )
        }
        composable(Routes.SESSIONS) {
            SessionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SUBJECTS) {
            SubjectsScreen(
                onBack = { navController.popBackStack() },
                onOpenSubject = { navController.navigate(Routes.subject(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.SUBJECT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SubjectDetailScreen(
                id = id,
                onBack = { navController.popBackStack() },
                onOpenCategory = { navController.navigate(Routes.category(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.CATEGORY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            CategoryScreen(
                id = id,
                onBack = { navController.popBackStack() },
                onOpenSubcategory = { navController.navigate(Routes.subcategory(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.SUBCATEGORY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SubcategoryScreen(
                id = id,
                onBack = { navController.popBackStack() },
                onOpenLesson = { navController.navigate(Routes.lesson(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.LESSON) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LessonScreen(
                id = id,
                onBack = { navController.popBackStack() },
                // Previous/next replace the current lesson so Back returns to the list, not through every lesson read.
                onOpenLesson = { next ->
                    navController.navigate(Routes.lesson(next)) { popUpTo(Routes.LESSON) { inclusive = true } }
                },
                onPractice = { navController.navigate(Routes.practice(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.PRACTICE) { entry ->
            val lessonId = entry.arguments?.getString("lessonId").orEmpty()
            PracticeScreen(
                lessonId = lessonId,
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.PRACTICE_HISTORY) {
            PracticeHistoryScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.QUIZZES) {
            QuizListScreen(
                onBack = { navController.popBackStack() },
                onOpenQuiz = { navController.navigate(Routes.quiz(it)) },
                onCreate = { navController.navigate("quiz-create") },
                onEdit = { navController.navigate("quiz/$it/edit") },
                onSignedOut = signedOut,
            )
        }
        composable("quiz-create") {
            QuizEditorScreen(
                quizSetId = null,
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
                onSaved = { id -> navController.navigate(Routes.quiz(id)) { popUpTo(Routes.QUIZZES) } },
            )
        }
        composable("quiz/{id}/edit") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            QuizEditorScreen(
                quizSetId = id,
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
                onSaved = { navController.popBackStack() },
            )
        }
        composable("quiz/{id}/questions") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            QuizQuestionsScreen(
                quizSetId = id,
                onBack = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.QUIZ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            QuizDetailScreen(id = id, onBack = { navController.popBackStack() }, onSignedOut = signedOut,
                onStart = { setId -> navController.navigate("quiz/$setId/play") },
                onHistory = { setId -> navController.navigate("quiz/$setId/history") },
                onOpenResult = { attemptId -> navController.navigate("quiz-attempt/$attemptId/result") },
                onEdit = { setId -> navController.navigate("quiz/$setId/edit") },
                onManageQuestions = { setId -> navController.navigate("quiz/$setId/questions") })
        }
        composable(Routes.QUIZ_PLAYER) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            QuizPlayerScreen(quizSetId = id, attemptId = null, onBack = { navController.popBackStack() }, onSignedOut = signedOut, onOpenResult = { attemptId -> navController.navigate("quiz-attempt/$attemptId/result") })
        }
        composable(Routes.QUIZ_HISTORY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            QuizHistoryScreen(quizSetId = id, onBack = { navController.popBackStack() }, onSignedOut = signedOut,
                onOpenResult = { attemptId -> navController.navigate("quiz-attempt/$attemptId/result") })
        }
        composable(Routes.QUIZ_RESULT) { entry ->
            val attemptId = entry.arguments?.getString("attemptId").orEmpty()
            QuizResultScreen(attemptId = attemptId, onBack = { navController.popBackStack() }, onSignedOut = signedOut,
                onRetryQuiz = { setId -> navController.navigate(Routes.quiz(setId)) { popUpTo(Routes.QUIZ) { inclusive = false } } })
        }
        composable(Routes.FLASHCARDS) {
            FlashcardListScreen(
                onBack = { navController.popBackStack() },
                onOpenSet = { navController.navigate(Routes.flashcard(it)) },
                onCreate = { navController.navigate(Routes.FLASHCARD_CREATE) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.FLASHCARD_CREATE) {
            FlashcardEditorScreen(
                setId = null,
                onBack = { navController.popBackStack() },
                onSaved = { id -> navController.navigate(Routes.flashcard(id)) { popUpTo(Routes.FLASHCARDS) } },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.FLASHCARD) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            FlashcardDetailScreen(
                setId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("flashcard/$it/edit") },
                onStudy = { navController.navigate("flashcard/$it/study") },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.FLASHCARD_EDIT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            FlashcardEditorScreen(
                setId = id,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.FLASHCARD_STUDY) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            StudyScreen(setId = id, onBack = { navController.popBackStack() }, onSignedOut = signedOut)
        }
        composable(Routes.NOTES) {
            NotesListScreen(
                onBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate("note/$it") },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.NOTE_NEW) {
            NoteEditorScreen(noteId = null, onBack = { navController.popBackStack() }, onSignedOut = signedOut)
        }
        composable(Routes.NOTE_EDIT) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            NoteEditorScreen(
                noteId = id,
                onBack = { navController.popBackStack() },
                onVersions = { navController.navigate("note/$it/versions") },
                onOpenAiContent = { navController.navigate(Routes.aiContentFromSelection(it)) },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.NOTE_VERSIONS) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            NoteVersionsScreen(
                noteId = id,
                onBack = { navController.popBackStack() },
                onRestored = { navController.popBackStack() },
                onSignedOut = signedOut,
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onOpenLesson = { navController.navigate(Routes.lesson(it)) },
                onSignedOut = signedOut,
            )
        }
    }
}
