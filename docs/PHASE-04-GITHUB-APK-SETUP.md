# Phase 04 — GitHub APK build and phone installation

## Goal

Make the current Android source build an installable APK from GitHub Actions without requiring the developer to install Gradle locally.

## Changes

- Added `.github/workflows/android-debug-apk.yml`.
  - Runs on pushes affecting `android/` or the workflow and on manual dispatch.
  - Installs JDK 17, Android SDK 34 and Gradle 8.10.2 on the GitHub runner.
  - Runs `:app:assembleDebug` and uploads `app-debug.apk` as `ExamBaseAI-debug-apk` for 14 days.
- Made the debug API URL configurable with `EXAMBASE_API_BASE_URL`.
- Kept the emulator-only `http://10.0.2.2:8080` as the debug fallback; the workflow warns that this fallback cannot be reached by a physical phone.
- Documented GitHub upload, repository variable setup, artifact download and Android installation in the root README.
- Added ignore rules for local signing keystores and properties.

## Required owner setup

1. Deploy the backend and database to a public HTTPS host.
2. Set GitHub Actions repository variable `EXAMBASE_API_BASE_URL` to the backend origin (for example `https://api.example.com`, without `/api/v1`).
3. Push the project and wait for the GitHub Actions workflow.
4. Download and install `app-debug.apk` from the workflow artifact.

## Verification status

- Source and workflow files reviewed locally.
- No local Gradle installation or Android SDK is available in this environment, so Android compilation and APK installation were not run here.
- GitHub Actions must complete successfully before treating the APK as build-verified.
- The backend is not deployed by this Android workflow. App installation is possible without a backend, but login and online APIs require the public backend URL and a healthy deployed backend.

## Deliberately not included

- No fake backend URL or credentials were added.
- No release signing key was generated or committed.
- No APK is claimed to have been built locally.
