> **AI assistant: read `CLAUDE.md` first. When the owner says "next", do the next row of the Work queue in `docs/PROGRESS.md`.**

# ExamBase AI — 40-step build through Step 33c AI content studio and learning tutor (see docs/PROGRESS.md)

Android-first education platform, built in 40 steps (`docs/PROGRESS.md` lists every step and its status;
the plan itself is `ExamBase_AI_40_Step_Build_Prompt.txt`). Step 31 admin web panel is built (`/admin-panel/`, see `docs/STEP-31-admin-web-panel.md`). Step 32 AI gateway backend is built (`/api/v1/ai/*`, see `docs/STEP-32-ai-gateway.md`; off until the `ai_assistants` flag is on and `GEMINI_API_KEY`/`GEMINI_MODEL` are set). Step 33 is split in three; 33a (Android AI core: consent, quota, assistant screen) is built, see `docs/STEP-33a-ai-assistant-core.md`. 33b (AI in the notes editor) is built, see `docs/STEP-33b-ai-notes-editor.md`. Step 33c now includes editable AI quiz/flashcard generation, private AI Quiz Sets, My Flashcard Sets persistence, tutor hints/steps/chat and conversation history. Step 33 source is implemented; compile and physical-device verification remain pending. AI Quiz Sets are stored separately from the official question bank and are not yet playable in the timed Quiz Engine.

## Layout
- `android/` Kotlin + Jetpack Compose (modules: `app`, `core:designsystem`)
- `backend/` Kotlin + Ktor (modular monolith), Flyway migrations, PostgreSQL
- `docs/` blueprint, `PROGRESS.md` and one note per step (`STEP-NN-*.md`)
- `docker-compose.yml` PostgreSQL 16, Redis 7, backend

## Run the backend
```bash
cp .env.example .env        # set POSTGRES_PASSWORD and JWT_SECRET (openssl rand -hex 32)
docker compose up --build
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/health/ready
```
Tests only (needs JDK 17 + Gradle 8.10): `cd backend && gradle test`

## Run the Android app
1. Install Android Studio (Koala or newer) and JDK 17.
2. Open the `android/` folder. Let Gradle sync (it downloads the wrapper/dependencies).
3. Run the `app` configuration on an emulator or phone.
4. Unit tests: `gradle :app:testDebugUnitTest` from `android/`.

If there is no `gradlew`, run `gradle wrapper --gradle-version 8.10.2` once inside `android/`.

## Steps 6-8 summary
Real email/password auth, token refresh, verification and reset codes, profile, theme, account deletion.
See `docs/STEP-06-08-auth-profile.md`. Codes appear in the backend log (`docker compose logs backend`) until an email provider is chosen.
Android emulator talks to `http://10.0.2.2:8080` (debug builds).

## Steps 9-12 summary
Browse Subject -> Category -> Subcategory -> Lesson, search/sort subjects, bookmarks, completion, recently viewed, English + Bangla.
See `docs/STEP-09-12-content.md`. Load demo content with `backend/src/main/resources/db/dev/seed_demo.sql` (development only).

## Step 13 summary
Read-only question bank API (`/api/v1/questions`, `/questions/{id}`, `/questions/tags`): six question types, filters, sorting, paging; correct answers are never returned.
See `docs/STEP-13-question-bank.md`. Load demo questions with `backend/src/main/resources/db/dev/seed_questions.sql` after `seed_demo.sql`.

## Step 14 summary
Server-side answer checking (`POST /api/v1/questions/{id}/check`), explanation reveal after answering, practice history and per-lesson summary,
and Android practice screens reachable from each lesson. See `docs/STEP-14-practice.md`. Not yet compiled or run.

## Step 15 summary
Backend only: user-owned Quiz Sets (separate entities, private to the owner) and server-timed, server-scored attempts
(`/api/v1/quiz-sets`, `/api/v1/quiz-attempts`). See `docs/STEP-15-quiz-engine.md`. Android screens come in Steps 16-17. Not yet compiled or run.

## What worked in Steps 1-5
Splash, onboarding, login, register and forgot-password screens (UI + local validation only),
placeholder home, English and Bangla strings, light and dark theme, `/health` endpoints,
roles table migration. 

## Build an APK from GitHub and install it on your phone

The repository includes `.github/workflows/android-debug-apk.yml`. After you push the project to GitHub, it builds an installable **debug APK** and attaches it to the workflow run as the `ExamBaseAI-debug-apk` artifact.

### 1. Configure the backend URL first (recommended)

The Android app needs a reachable backend for login, lessons, quizzes, notes and AI. A phone cannot reach the emulator-only address `http://10.0.2.2:8080`.

1. Deploy the `backend/` service and PostgreSQL database to a public host, and confirm `https://YOUR-BACKEND-DOMAIN/api/v1/health` responds.
2. In GitHub, open **Settings → Secrets and variables → Actions → Variables → New repository variable**.
3. Set the name to `EXAMBASE_API_BASE_URL` and the value to the backend origin, for example `https://your-backend.example.com` (no `/api/v1` suffix).

This is a public URL, not a password, so use a repository **Variable**, not a Secret. Never put database passwords, JWT secrets, SMTP credentials or Gemini API keys in this variable or in Android source code.

If you skip this setting, GitHub can still build an APK, but it will use `http://10.0.2.2:8080`, which is only reachable from an Android emulator. Online features will not connect from a physical phone.

### 2. Upload the project to GitHub (Windows PowerShell)

Extract the ZIP so the `ExamBaseAI` folder contains `README.md`, `android`, `backend` and `.github`. Open PowerShell in that folder. Create an empty GitHub repository first, then run (replace the URL with your own repository URL):

```powershell
git init
git add .
git commit -m "Prepare ExamBaseAI Android APK build"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/YOUR-REPOSITORY.git
git push -u origin main
```

If this folder is already a Git repository with a remote, do not run `git init` or `git remote add` again. Use `git status`, then `git add .`, `git commit -m "Prepare Android APK build"`, and `git push`.

### 3. Download the APK

1. Open the GitHub repository and select **Actions**.
2. Open the latest **Build Android APK** run. The run starts automatically after you push changes to `android/` or the workflow file. You can also use **Run workflow**.
3. Wait until the job is green, then find **Artifacts** at the bottom of the run page.
4. Download and extract `ExamBaseAI-debug-apk`. Inside it is `app-debug.apk`.

### 4. Install on Android

1. Send `app-debug.apk` to your phone (USB, Drive or another trusted transfer method).
2. Open the APK on the phone and allow **Install unknown apps** for the file manager/browser if Android asks.
3. Tap **Install**, then open **ExamBase AI**.

This debug APK is for testing and direct installation. It is not a Play Store release; a signed release APK/AAB and signing-key setup will be prepared separately before publishing.

### Build configuration notes

- GitHub Actions installs JDK 17, Android SDK 34 and Gradle 8.10.2; the repository currently has no Gradle Wrapper scripts/JAR.
- Debug APK API URL is configurable through `EXAMBASE_API_BASE_URL`; the default emulator URL is only a fallback.
- The workflow uploads the APK for 14 days. Download it before the artifact expires.
- The workflow has not been executed from this environment. A green GitHub Actions run is the first actual compilation/build verification; fix any compiler errors shown there before relying on the APK.

## Phase 04 — AI performance and integration

The AI gateway now uses cancellable asynchronous Gemini HTTP calls with bounded timeouts and one retry for transient provider failures. The Android API client has explicit request/socket timeouts, and the AI Assistant / Note AI screens expose a cancel action while a request is running. AI MCQ output instructions now match the app's question parser format. See `docs/PHASE-04-AI-PERFORMANCE-INTEGRATION.md` for implementation details and known integration boundaries.

Builds and tests must be confirmed by the GitHub Actions CI workflow; a ZIP integrity check is not a substitute for compilation.
#   h e l l o b a n g l a d e s h  
 