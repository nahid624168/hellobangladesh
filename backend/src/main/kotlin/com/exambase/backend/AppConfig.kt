package com.exambase.backend

/**
 * All configuration comes from environment variables. No secrets are hard-coded.
 * If DB_URL is not set the server starts without a database (health endpoints only).
 */
data class AppConfig(
    val environment: String,
    val port: Int,
    val dbUrl: String?,
    val dbUser: String?,
    val dbPassword: String?,
    val jwtSecret: String? = null,
    val jwtIssuer: String = "exambase",
    val accessTtlMinutes: Long = 15,
    val refreshTtlDays: Long = 30,
    /** HMAC key for attachment signed URLs. Falls back to JWT_SECRET when unset. */
    val signedUrlSecret: String? = null,
    /** How long a signed attachment URL remains valid (minutes). */
    val signedUrlTtlMinutes: Long = 60,
    /** Public base URL used when rewriting storage keys into absolute signed URLs. */
    val publicBaseUrl: String = "http://localhost:8080",
    /** Gemini API key (Step 32). Unset means the AI gateway answers 503 `ai_unavailable`. Never logged. */
    val geminiApiKey: String? = null,
    /** Gemini model id, e.g. from Google's model list. No default is guessed: AI stays off until it is set. */
    val geminiModel: String? = null,
    val aiDailyRequestLimit: Int = 30,
    val aiDailyTokenLimit: Int = 60_000,
    /** Cost guard across all users per UTC day (prompt + completion tokens). */
    val aiGlobalDailyTokenLimit: Int = 2_000_000,
    /** SMTP settings. Password must come from the deployment secret store. */
    val smtpHost: String? = null,
    val smtpPort: Int = 587,
    val smtpUsername: String? = null,
    val smtpPassword: String? = null,
    val smtpFrom: String? = null,
    val smtpStartTls: Boolean = true,
    /** Set TRUST_PROXY=true only behind a reverse proxy you control; then client IPs come from X-Forwarded-For. */
    val trustProxy: Boolean = false,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = AppConfig(
            environment = env["APP_ENV"] ?: "development",
            port = env["PORT"]?.toIntOrNull() ?: 8080,
            dbUrl = env["DB_URL"],
            dbUser = env["DB_USER"],
            dbPassword = env["DB_PASSWORD"],
            jwtSecret = env["JWT_SECRET"]?.takeIf { it.isNotBlank() },
            jwtIssuer = env["JWT_ISSUER"] ?: "exambase",
            accessTtlMinutes = env["ACCESS_TTL_MINUTES"]?.toLongOrNull() ?: 15,
            refreshTtlDays = env["REFRESH_TTL_DAYS"]?.toLongOrNull() ?: 30,
            signedUrlSecret = env["SIGNED_URL_SECRET"]?.takeIf { it.isNotBlank() }
                ?: env["JWT_SECRET"]?.takeIf { it.isNotBlank() },
            signedUrlTtlMinutes = env["SIGNED_URL_TTL_MINUTES"]?.toLongOrNull() ?: 60,
            publicBaseUrl = env["PUBLIC_BASE_URL"] ?: "http://localhost:8080",
            geminiApiKey = env["GEMINI_API_KEY"]?.takeIf { it.isNotBlank() },
            geminiModel = env["GEMINI_MODEL"]?.takeIf { it.isNotBlank() },
            aiDailyRequestLimit = env["AI_DAILY_REQUEST_LIMIT"]?.toIntOrNull() ?: 30,
            aiDailyTokenLimit = env["AI_DAILY_TOKEN_LIMIT"]?.toIntOrNull() ?: 60_000,
            aiGlobalDailyTokenLimit = env["AI_GLOBAL_DAILY_TOKEN_LIMIT"]?.toIntOrNull() ?: 2_000_000,
            smtpHost = env["SMTP_HOST"]?.takeIf { it.isNotBlank() },
            smtpPort = env["SMTP_PORT"]?.toIntOrNull() ?: 587,
            smtpUsername = env["SMTP_USERNAME"]?.takeIf { it.isNotBlank() },
            smtpPassword = env["SMTP_PASSWORD"]?.takeIf { it.isNotBlank() },
            smtpFrom = env["SMTP_FROM"]?.takeIf { it.isNotBlank() },
            smtpStartTls = env["SMTP_STARTTLS"]?.toBooleanStrictOrNull() ?: true,
            trustProxy = env["TRUST_PROXY"]?.toBooleanStrictOrNull() ?: false,
        )

        fun forTests() = AppConfig(
            environment = "test",
            port = 0,
            dbUrl = null,
            dbUser = null,
            dbPassword = null,
            jwtSecret = "test-secret-for-signing-urls-only",
            signedUrlSecret = "test-secret-for-signing-urls-only",
        )
    }
}
