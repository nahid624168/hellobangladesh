package com.exambase.backend

import com.exambase.backend.academic.AcademicService
import com.exambase.backend.academic.JdbcAcademicRepository
import com.exambase.backend.academic.academicRoutes
import com.exambase.backend.auth.AuthPrincipal
import com.exambase.backend.auth.AuthService
import com.exambase.backend.auth.EmailSender
import com.exambase.backend.auth.JWT_AUTH
import com.exambase.backend.auth.JdbcEmailCodeRepository
import com.exambase.backend.auth.JdbcSessionRepository
import com.exambase.backend.auth.JdbcUserRepository
import com.exambase.backend.auth.JwtService
import com.exambase.backend.auth.LogEmailSender
import com.exambase.backend.auth.SmtpEmailSender
import com.exambase.backend.auth.PasswordHasher
import com.exambase.backend.auth.RateLimiter
import com.exambase.backend.auth.SessionRepository
import com.exambase.backend.auth.authRoutes
import com.exambase.backend.auth.userRoutes
import com.exambase.backend.questions.JdbcPracticeRepository
import com.exambase.backend.questions.JdbcQuestionRepository
import com.exambase.backend.questions.PracticeService
import com.exambase.backend.questions.QuestionService
import com.exambase.backend.questions.practiceRoutes
import com.exambase.backend.questions.questionRoutes
import com.exambase.backend.quizzes.JdbcQuizRepository
import com.exambase.backend.quizzes.QuizService
import com.exambase.backend.flashcards.FlashcardService
import com.exambase.backend.flashcards.JdbcFlashcardRepository
import com.exambase.backend.flashcards.flashcardRoutes
import com.exambase.backend.notes.JdbcNoteRepository
import com.exambase.backend.notes.NoteService
import com.exambase.backend.notes.noteRoutes
import com.exambase.backend.contests.ContestService
import com.exambase.backend.contests.JdbcContestRepository
import com.exambase.backend.contests.contestRoutes
import com.exambase.backend.search.JdbcSearchRepository
import com.exambase.backend.dashboard.DashboardService
import com.exambase.backend.dashboard.dashboardRoutes
import com.exambase.backend.search.SearchService
import com.exambase.backend.search.searchRoutes
import com.exambase.backend.admin.AdminService
import com.exambase.backend.admin.JdbcAdminRepository
import com.exambase.backend.admin.adminRoutes
import com.exambase.backend.analytics.AnalyticsService
import com.exambase.backend.analytics.JdbcAnalyticsRepository
import com.exambase.backend.analytics.analyticsRoutes
import com.exambase.backend.exams.ExamService
import com.exambase.backend.exams.JdbcExamRepository
import com.exambase.backend.exams.examRoutes
import com.exambase.backend.realtime.ContestRealtimeHub
import com.exambase.backend.realtime.InMemoryEventBus
import com.exambase.backend.realtime.JdbcOutbox
import com.exambase.backend.realtime.realtimeRoutes
import com.exambase.backend.quizzes.quizRoutes
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.Clock
import java.time.Duration
import java.util.UUID

const val SERVICE_NAME = "exambase-backend"
const val SERVICE_VERSION = "0.14.0"

class AppServices(
    val auth: AuthService,
    val jwt: JwtService,
    val sessions: SessionRepository,
    val authLimiter: RateLimiter,
    val clock: Clock = Clock.systemUTC(),
    /** Null in tests that only exercise auth. */
    val academic: AcademicService? = null,
    /** Null in tests that do not exercise the question bank. */
    val questions: QuestionService? = null,
    /** Null in tests that do not exercise practice. */
    val practice: PracticeService? = null,
    /** Null in tests that do not exercise quizzes. */
    val quizzes: QuizService? = null,
    /** Null in tests that do not exercise flashcards. */
    val flashcards: FlashcardService? = null,
    /** Null in tests that do not exercise notes. */
    val notes: NoteService? = null,
    /** Null in tests that do not exercise contests. */
    val contests: ContestService? = null,
    /** Realtime hub for contest WebSockets (Step 25). */
    val realtimeHub: ContestRealtimeHub? = null,
    /** Null in tests that do not exercise exams. */
    val exams: ExamService? = null,
    /** Null in tests that do not exercise analytics. */
    val analytics: AnalyticsService? = null,
    val search: SearchService? = null,
    val dashboard: DashboardService? = null,
    val admin: AdminService? = null,
    /** Null in tests that do not exercise the AI gateway (Step 32). */
    val ai: com.exambase.backend.ai.AiService? = null,
)

fun main() {
    val config = AppConfig.fromEnv()
    val database = Database.connectOrNull(config)?.also { it.migrate() }
    val emailSender: EmailSender = if (config.smtpHost != null && config.smtpFrom != null) {
        SmtpEmailSender(config.smtpHost, config.smtpPort, config.smtpUsername, config.smtpPassword, config.smtpFrom, config.smtpStartTls)
    } else LogEmailSender()
    val services = database?.let { buildServices(config, it, emailSender) }
    if (database != null) startGuestCleanup(JdbcUserRepository(database))
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config, database, services)
    }.start(wait = true)
}

/** Hourly cleanup of expired guest accounts so guest mode cannot fill the database. */
private fun startGuestCleanup(users: JdbcUserRepository) {
    val log = org.slf4j.LoggerFactory.getLogger("GuestCleanup")
    val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "guest-cleanup").apply { isDaemon = true }
    }
    executor.scheduleWithFixedDelay({
        runCatching { users.purgeExpiredGuests(com.exambase.backend.auth.GUEST_EMAIL_SUFFIX, java.time.Instant.now().minus(Duration.ofHours(1))) }
            .onSuccess { if (it > 0) log.info("Removed {} expired guest accounts", it) }
            .onFailure { log.warn("Guest cleanup failed", it) }
    }, 5, 60, java.util.concurrent.TimeUnit.MINUTES)
}

/** Fails fast with a clear message when required secrets are missing. */
fun buildServices(config: AppConfig, database: Database, emailSender: EmailSender = LogEmailSender()): AppServices {
    val secret = config.jwtSecret
    require(secret != null && secret.length >= 32) {
        "JWT_SECRET must be set to a random string of at least 32 characters (e.g. `openssl rand -hex 32`)."
    }
    require(config.environment != "production" || emailSender !is LogEmailSender) {
        "No email provider is configured. LogEmailSender is for development only."
    }
    val jwt = JwtService(secret, config.jwtIssuer, Duration.ofMinutes(config.accessTtlMinutes))
    val sessions = JdbcSessionRepository(database)
    val signedUrls = com.exambase.backend.storage.SignedUrlService(config)
    val auth = AuthService(
        users = JdbcUserRepository(database),
        sessions = sessions,
        codes = JdbcEmailCodeRepository(database),
        passwords = PasswordHasher(),
        jwt = jwt,
        email = emailSender,
        refreshTtl = Duration.ofDays(config.refreshTtlDays),
        signedUrls = signedUrls,
        exportCounts = { userId -> loadExportCounts(database, userId) },
    )
    val hub = ContestRealtimeHub(bus = InMemoryEventBus(), outbox = JdbcOutbox(database))
    return AppServices(
        auth, jwt, sessions, RateLimiter(maxRequests = 20, window = Duration.ofMinutes(1)),
        academic = AcademicService(JdbcAcademicRepository(database, signedUrls)),
        questions = QuestionService(JdbcQuestionRepository(database)),
        practice = PracticeService(JdbcPracticeRepository(database), RateLimiter(maxRequests = 60, window = Duration.ofMinutes(1))),
        flashcards = FlashcardService(JdbcFlashcardRepository(database)),
        notes = NoteService(JdbcNoteRepository(database)),
        quizzes = QuizService(
            JdbcQuizRepository(database), JdbcQuestionRepository(database),
            RateLimiter(maxRequests = 60, window = Duration.ofMinutes(1)),
        ),
        contests = ContestService(
            JdbcContestRepository(database),
            RateLimiter(maxRequests = 30, window = Duration.ofMinutes(1)),
            realtime = { contestId, name, payload -> hub.publish(contestId, name, payload) },
        ),
        realtimeHub = hub,
        exams = ExamService(
            JdbcExamRepository(database),
            emailVerified = { userId ->
                database.withConnection { c ->
                    c.prepareStatement(
                        "SELECT email_verified_at IS NOT NULL FROM users WHERE id = ? AND status <> 'deleted'",
                    ).use { ps ->
                        ps.setObject(1, userId)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) else false }
                    }
                }
            },
        ),
        analytics = AnalyticsService(JdbcAnalyticsRepository(database)),
        search = SearchService(JdbcSearchRepository(database)),
        dashboard = null,
        admin = AdminService(JdbcAdminRepository(database)),
        ai = buildAiService(config, database),
    ).let { base ->
        AppServices(
            auth = base.auth,
            jwt = base.jwt,
            sessions = base.sessions,
            authLimiter = base.authLimiter,
            academic = base.academic,
            questions = base.questions,
            practice = base.practice,
            flashcards = base.flashcards,
            notes = base.notes,
            quizzes = base.quizzes,
            contests = base.contests,
            realtimeHub = base.realtimeHub,
            exams = base.exams,
            analytics = base.analytics,
            search = base.search,
            dashboard = DashboardService(database, base.analytics),
            admin = base.admin,
            ai = base.ai,
        )
    }
}

/** Lightweight counts for GDPR-style personal data export (Step 8b). */
private fun loadExportCounts(database: Database, userId: java.util.UUID): com.exambase.backend.auth.ExportCounts =
    database.withConnection { c ->
        fun count(sql: String): Int = c.prepareStatement(sql).use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        com.exambase.backend.auth.ExportCounts(
            noteCount = count("SELECT count(*) FROM notes WHERE owner_id = ? AND deleted_at IS NULL"),
            quizSetCount = count("SELECT count(*) FROM quiz_sets WHERE owner_id = ? AND deleted_at IS NULL"),
            flashcardSetCount = count("SELECT count(*) FROM flashcard_sets WHERE owner_id = ? AND deleted_at IS NULL"),
            bookmarkCount = count("SELECT count(*) FROM lesson_bookmarks WHERE user_id = ?"),
            practiceAttemptCount = count("SELECT count(*) FROM practice_attempts WHERE user_id = ?"),
        )
    }

fun Application.module(config: AppConfig, database: Database?, services: AppServices? = null) {
    if (config.trustProxy) install(io.ktor.server.plugins.forwardedheaders.XForwardedHeaders)
    install(CallLogging)
    install(ContentNegotiation) { json() }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = 64 * 1024
        masking = false
    }
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respond(e.status, ApiError(ErrorBody(e.code, e.message ?: "", e.fields)))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ErrorBody("invalid_request", "The request body is invalid.")))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(ErrorBody("internal_error", "Something went wrong. Please try again.")),
            )
        }
    }

    if (services != null) {
        install(Authentication) {
            jwt(JWT_AUTH) {
                realm = "exambase"
                verifier(services.jwt.verifier)
                validate { credential ->
                    val userId = runCatching { UUID.fromString(credential.payload.subject) }.getOrNull()
                    val sessionId = runCatching { UUID.fromString(credential.payload.getClaim("sid").asString()) }.getOrNull()
                    // Checking the session on every request makes logout and "sign out other devices" immediate.
                    if (userId != null && sessionId != null && services.sessions.isActive(sessionId, services.clock.instant())) {
                        AuthPrincipal(userId, sessionId, credential.payload.getClaim("guest").asBoolean() == true)
                    } else {
                        null
                    }
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, ApiError(ErrorBody("unauthorized", "Please sign in again.")))
                }
            }
        }
    }

    routing {
        // Step 31: admin web panel (static files, same origin as the API; every action is still checked by the API)
        staticResources("/admin-panel", "admin-panel")
        route("/api/v1") {
            get("/health") {
                call.respond(HealthResponse("ok", SERVICE_NAME, SERVICE_VERSION))
            }
            get("/health/ready") {
                if (database != null && database.ping()) {
                    call.respond(HealthResponse("ready", SERVICE_NAME, SERVICE_VERSION))
                } else {
                    call.respond(
                        HttpStatusCode.ServiceUnavailable,
                        ApiError(ErrorBody("not_ready", "Database is not reachable.")),
                    )
                }
            }
            if (services != null) {
                authRoutes(services.auth, services.authLimiter)
                userRoutes(services.auth, services.authLimiter)
                services.academic?.let { academicRoutes(it) }
                services.questions?.let { questionRoutes(it) }
                services.practice?.let { practiceRoutes(it) }
                services.quizzes?.let { quizRoutes(it) }
                services.flashcards?.let { flashcardRoutes(it) }
                services.notes?.let { noteRoutes(it) }
                services.contests?.let { contestRoutes(it) }
                services.exams?.let { examRoutes(it) }
                services.analytics?.let { analyticsRoutes(it) }
                services.search?.let { searchRoutes(it) }
                if (services.dashboard != null) dashboardRoutes(services.dashboard, services.auth)
                services.admin?.let { adminRoutes(it) }
                if (database != null) services.ai?.let { com.exambase.backend.ai.aiRoutes(it, com.exambase.backend.ai.AiGeneratedQuizStore(database)) }
                val hub = services.realtimeHub
                if (hub != null) realtimeRoutes(hub, services.jwt)
            }
        }
    }
}

/** Step 32: Gemini when GEMINI_API_KEY and GEMINI_MODEL are both set; otherwise the gateway answers 503 `ai_unavailable`. */
fun buildAiService(config: AppConfig, database: Database): com.exambase.backend.ai.AiService {
    val key = config.geminiApiKey
    val model = config.geminiModel
    val provider: com.exambase.backend.ai.AiProvider =
        if (key != null && model != null) com.exambase.backend.ai.GeminiProvider(key, model) else com.exambase.backend.ai.NotConfiguredProvider
    return com.exambase.backend.ai.AiService(
        repo = com.exambase.backend.ai.JdbcAiRepository(database),
        provider = provider,
        enabled = {
            database.withConnection { c ->
                c.prepareStatement("SELECT enabled FROM feature_flags WHERE key = 'ai_assistants'").use { ps ->
                    ps.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
                }
            }
        },
        limiter = RateLimiter(maxRequests = 10, window = Duration.ofMinutes(1)),
        config = com.exambase.backend.ai.AiGatewayConfig(
            dailyRequestLimit = config.aiDailyRequestLimit,
            dailyTokenLimit = config.aiDailyTokenLimit,
            globalDailyTokenLimit = config.aiGlobalDailyTokenLimit,
        ),
    )
}
