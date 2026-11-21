# Step 27: Examination system

**Status: PARTIAL (source added; not compiled / not run).**

**Checkpoint:** Tournament/team features are **not** implemented. They wait on an explicit owner decision.

## Scope

### Backend
- Schema `V13__exams.sql`: exams, sections, section questions, attempts, answers, **audit events**
- Rules: open window, duration deadline, max attempts, email-verified eligibility, shuffle, result policies (`immediate` / `after_close` / `manual`)
- Server scoring via `Grader`; interruption recovery = resume in-progress attempt
- Audit: `attempt.started`, `attempt.submitted`, `attempt.expired`, `exam.created`
- API:
  - `GET/POST /exams`, `GET /exams/{id}`, `POST /exams/{id}/start`
  - `POST /exam-attempts/{id}/answers`, `POST …/submit`, `GET …/result`

### Android
- Network DTOs + `ExamApi`
- Repository / ViewModels / screens: list, detail (instructions + sections), player (timer + auto-submit), result
- Home → Exams entry
- EN + BN strings

## Acceptance criteria

1. Starting while an attempt is in progress resumes it (same attempt id).
2. Deadline expiry marks attempt expired and is audited.
3. Scores/results withheld when policy is `after_close` until close time.
4. Clients cannot set score fields on submit.

## Not verified

Nothing compiled or run.

## Run it

```bash
cd backend && ./gradlew test --tests com.exambase.backend.exams.ExamServiceTest
cd android && ./gradlew :app:assembleDebug
```
