# Phase 01 — Stability fixes

This phase fixes safe, source-confirmed Android build/test configuration issues without changing database schema or user data.

## Changes

1. `AiGeneratedContentTest.kt`: corrected test fixtures to use Kotlin newline escapes (`\n`) so the parser receives multiple lines rather than literal backslash-n text.
2. Android Gradle version catalog: added `desugar_jdk_libs` 2.1.2.
3. Android app Gradle config: enabled Core Library Desugaring for the configured `minSdk = 24` while the app uses `java.time` APIs.
4. Release API configuration: removed the fake `CHANGE-ME.invalid` endpoint. Release builds now read `EXAMBASE_API_BASE_URL` from a Gradle property or environment variable.
5. Release safety check: `assembleRelease` / `bundleRelease` fail with a clear message unless the configured API endpoint starts with HTTPS.

## Build commands

Debug build (from `android/`):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Release build (replace with the real deployed backend URL):

```powershell
.\gradlew.bat assembleRelease -PEXAMBASE_API_BASE_URL=https://YOUR-REAL-API-DOMAIN
```

Alternatively, set the `EXAMBASE_API_BASE_URL` environment variable.

## Verification status

- ZIP integrity: verified.
- Kotlin test fixture source: inspected; newline escapes are now correct.
- Gradle TOML catalog: syntax checked.
- Gradle/Android build and unit tests: not executed in this environment because the project has no Gradle Wrapper scripts/JAR and no system Gradle installation. Obtain/generate the official Gradle 8.10.2 wrapper before claiming a build pass.

## Not changed in this phase

No database migrations, user records, authentication behavior, backend secrets, production email provider, AI provider, or API contracts were changed. Those require separate focused phases and deployment-specific values.
