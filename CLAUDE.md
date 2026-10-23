# Second Brain Server

## Overview
Spring Boot 3.x backend for the Second Brain personal growth & habit tracking app. Uses PostgreSQL, Google Gemini AI (gemini-3.6-flash), JWT auth, and Flyway migrations.

## Tech Stack
- **Framework:** Spring Boot 3.x + Gradle (Kotlin DSL)
- **Database:** PostgreSQL with Flyway migrations
- **AI:** Google Gemini via REST (GeminiClient with retry-on-429)
- **Auth:** JWT (access tokens), Spring Security with custom JwtAuthFilter
- **Scheduling:** Spring @Scheduled cron jobs

## Project Structure
```
src/main/java/com/secondbrain/second_brain_server/
├── controllers/      # REST endpoints under /api/
├── service/          # Business logic
│   └── ai/          # AI services (chat, nudges, insights, task generation)
├── repository/       # Spring Data JPA repositories
├── entities/         # JPA entities (11 tables, all UUID PKs)
├── dto/request/      # Incoming DTOs with validation
├── dto/response/     # Outgoing DTOs
├── security/         # JWT filter, SecurityConfig, @CurrentUser resolver
├── scheduler/        # Cron jobs (nudges, streaks, task generation)
├── external/         # GeminiClient, GoogleTokenVerifier
├── enums/            # Domain types, statuses, feel labels
├── exception/        # Custom exceptions + GlobalExceptionHandler
├── config/           # AppConfig
└── util/             # DateUtil, StreakCalculator, MetricValidator
```

## Key Architecture Decisions
- **Domain-agnostic metrics:** No domain-specific tables. All metrics use DomainMetricDefinition + SessionMetricValue (flat key-value pairs).
- **userId from JWT:** All controllers use `@CurrentUser UUID userId` resolved from SecurityContext. Never in request body.
- **AI with fallback:** All AI calls have fallback behavior if Gemini fails.
- **Async insights:** Session AI insights generated @Async after save.

## Core Flows

### Session Log Creation (POST /api/session-logs)
1. Validate domain ownership
2. Validate metric keys against DomainMetricDefinition
3. Save SessionLog + SessionMetricValues
4. Check and update Personal Records
5. Update milestone progress
6. Update domain streak
7. Auto-mark due tasks as DONE for that domain
8. Generate AI insight (async)

### Daily Task Generation (cron 5 AM + POST /api/tasks/generate)
- For each active domain with 0 pending tasks:
  - Assembles full user context (logs, PRs, streaks, milestones, weekly stats)
  - AI generates 2-4 progressive tasks based on recent performance
  - Tasks saved with aiGenerated=true, due dates in next 7 days

### AI Chat (POST /api/ai/chat)
- Multi-turn conversations stored in ai_conversations + ai_messages
- AI can propose actions: ADD_TASK, SET_MILESTONE, ADJUST_PLAN
- User confirms via POST /api/ai/actions/apply

## Database Schema
11 tables: users, domains, domain_metric_definitions, session_logs, session_metric_values, personal_records, milestones, tasks, ai_conversations, ai_messages, ai_nudges

Schema in: `src/main/resources/db/V1__create_initial_schema.sql`
LLD in: `src/main/resources/design/lld.puml`

## API Endpoints
- `/api/auth/**` — register, login, Google OAuth (public)
- `/api/domains/**` — CRUD domains, generate/regenerate AI system
- `/api/session-logs/**` — log sessions, list logs
- `/api/dashboard/**` — home dashboard, weekly snapshot
- `/api/tasks/**` — CRUD tasks, generate tasks via AI
- `/api/milestones/**` — CRUD milestones
- `/api/metrics/**` — metric definitions per domain
- `/api/ai/**` — chat, conversations, apply actions, nudges
- `/api/insights/**` — AI-generated highlights/patterns
- `/api/progress/**` — time series, PRs per domain
- `/api/users/**` — profile, export (JSON/CSV)

## Schedulers
| Scheduler | Cron | Purpose |
|-----------|------|---------|
| DailyNudgeScheduler | midnight | Generate motivational nudges per domain |
| StreakCheckScheduler | midnight | Recalculate streaks for all active domains (resets broken streaks) |
| DailyTaskGenerationScheduler | 5 AM | Generate new tasks for domains with none pending |
| WeeklyReviewScheduler | Sunday 1 AM | (not yet implemented) |

## Running
```bash
./gradlew bootRun
```
Requires: PostgreSQL running, `application.properties` configured with DB URL and Gemini API key.

## Important Notes
- Security returns 401 (not 403) for unauthenticated requests via custom AuthenticationEntryPoint
- All AI prompts in PromptBuilder.java — single place to modify AI behavior
- UserContextAssembler gathers all user data for AI calls (~2000 token context block)
- Postman collection available: `second_brain_server_postman_collection.json`

## Session Log — Aug 16, 2026

### Changes Made
- **Auth:** Added AuthenticationEntryPoint to SecurityConfig → server now returns 401 (not 403) for expired/invalid tokens
- **Session logging:** `SessionLogService.createLog()` now auto-marks TODO/IN_PROGRESS tasks as DONE for that domain when a session is logged (via `TaskService.markDueTasksDoneForDomain`)
- **Daily task generation:** New `TaskGenerationService` + `DailyTaskGenerationScheduler` (5 AM cron). For each user's active domains with 0 pending tasks, AI generates 2-4 progressive tasks based on recent logs, PRs, streaks, milestones, and feel patterns
- **Manual task generation:** `POST /api/tasks/generate` endpoint added to TaskController
- **PromptBuilder:** New `taskGeneration()` method for daily task generation prompts
- **Dashboard filtering:** `DashboardService` now only shows tasks, streaks, and weekly stats for ACTIVE domains (paused/archived excluded)
- **Weekly stats fix:** Fixed `DashboardService` passing raw date instead of `DateUtil.getWeekStart(date)` to WeeklyStatService
- **Domain lifecycle:** Added `resumeDomain()` to DomainService + `POST /api/domains/{id}/resume` endpoint
- **TaskRepository:** Added `findByDomainIdAndDueDateLessThanEqualAndStatusIn` query method

## Session Log — Aug 23, 2026

### Changes Made
- **Streak calculation fix:** `DomainService.updateStreakForDomain()` now uses `StreakCalculator.compute()` (walks backwards through log dates vs scheduled days) instead of naive `currentStreak + 1`. Streaks now correctly reset when scheduled days are missed.
- **Streak recalculation service:** Added `DomainService.recalculateAllStreaksForUser(userId)` — recalculates streaks for all active domains of a user using actual log history
- **StreakCheckScheduler rewrite:** Now actually calls `streakService.recalculateStreak()` for ALL active domains at midnight (was previously a no-op that fetched at-risk domains but did nothing)
- **Manual recalculate API:** New `POST /api/domains/streaks/recalculate` endpoint for manual streak correction
- **DomainRepository:** Added `findAllByStatus(DomainStatus)` query method
- **AI Chat overhaul:** `AiChatRequest` now requires `domainId` (UUID) and `chatMode` (ADJUST_PLAN or DATA_QUERY). Rate limit: 3 messages/user/day (counted from ai_messages). `AiChatService.chat()` now: validates domain ownership, scopes context to selected domain only via `assembleForDomain()`, uses mode-specific strict system prompt. New `ChatMode` enum added.
- **PromptBuilder strict rewrite:** `chat()` now accepts `(UserContext, ChatMode, domainName)`. System prompt explicitly refuses off-topic questions, injects full domain data (logs, PRs, milestones, tasks, streaks, weekly stats). ADJUST_PLAN mode generates proposedActions. DATA_QUERY mode is Q&A only.
- **New endpoint:** `GET /api/ai/remaining` — returns remaining daily AI messages for the user
- **DateUtil fix:** `parseWeeklySchedule()` now handles abbreviated day names (MON, TUE, SAT, etc.) instead of requiring full Java DayOfWeek enum names

## Session Log — Job Execution Logging + Per-User AI Limits

### Per-user AI daily limit
- **users table:** Added `ai_daily_limit INT DEFAULT 3` and `ai_used_today INT DEFAULT 0` (in both CREATE TABLE and ALTER blocks in `V1__create_initial_schema.sql`)
- **User.java:** Added `aiDailyLimit` / `aiUsedToday` fields; `onCreate()` defaults them to 3 / 0
- **UserRepository:** Added `resetAllDailyAiUsage()` (`@Modifying @Query` UPDATE User SET aiUsedToday = 0)
- **AiLimitResetScheduler (new):** Midnight cron calls `resetAllDailyAiUsage()`
- **AiChatService:** Replaced global env-var limit with per-user DB check (`user.getAiDailyLimit()` / `getAiUsedToday()`), increments `aiUsedToday` on success; `getRemainingMessages()` uses per-user values. Injected `UserRepository`.

### Job execution logging (audit trail — DB only, no API)
- **job_execution_logs table (new):** id UUID PK, job_name, status (SUCCESS/FAILED), started_at, finished_at, error_message TEXT, details VARCHAR(500), created_at + index `idx_job_logs_name_date`. Added to `V1__create_initial_schema.sql` with a `CREATE TABLE IF NOT EXISTS` at the bottom for the prod DB. **No duration column.**
- **JobExecutionLog.java (new entity):** matching fields, `@PrePersist` sets createdAt
- **JobExecutionLogRepository.java (new):** plain JpaRepository
- **JobLogService.java (new):** `run(String jobName, Supplier<String> task)` and `run(String jobName, Runnable task)`. Records started/finished timestamps, catches exceptions, saves a log row (SUCCESS/FAILED + errorMessage + details truncated to 500). Log-write is in its own try/catch so a logging failure never crashes the job.
- **All active schedulers wrapped** with `jobLogService.run(name, () -> {...; return "detail"})`: DailyNudgeScheduler, DailyTaskGenerationScheduler, StreakCheckScheduler, AiLimitResetScheduler. WeeklyReviewScheduler left unwrapped (currently a no-op).
- **App config:** `@EnableScheduling` + `@EnableAsync` added to `SecondBrainServerApplication` so schedulers actually run.

### Deploy notes
- Run the `CREATE TABLE IF NOT EXISTS job_execution_logs ...` + AI-limit `ALTER TABLE users ...` SQL manually on prod DB via `sudo -u postgres psql -d secondbrain_db`
- Build `.\gradlew clean bootJar`, scp jar to Oracle VM, `sudo systemctl restart secondbrain`

## Session Log — Metric Value Bounds (stop % / scores exceeding 100)

### Problem
Percentage and score metrics (often AI-generated custom metrics) could store and display values above 100% because there were no value bounds anywhere — `MetricValidator` only checked metric *keys*, not ranges.

### Fix — inferred bounds, zero user setup
- **Schema:** added nullable `min_value` / `max_value` (DOUBLE PRECISION) to `domain_metric_definitions` via `ALTER ... ADD COLUMN IF NOT EXISTS` in `V1__create_initial_schema.sql`, plus a backfill `UPDATE` clamping existing `%`/percent metrics to 0..100.
- **Entity/DTO:** added `minValue`/`maxValue` to `DomainMetricDefinition` (+ `toResponse()`) and `MetricDefinitionResponse`.
- **MetricValidator:** added `clampValues(submitted, defined)` + `clamp(value, metric)`. Bounds resolution is **inferred, never required from the user**: declared min/max win → else unit `%`/`percent` gives ceiling 100 → else floor 0 with no ceiling (reps/weight/km/minutes grow freely).
- **DomainService:** added `clampMetricValues(domainId, metrics)`; `applyGeneratedSystem` now persists AI-provided `minValue`/`maxValue` with unit-based fallback (`%` → 0/100).
- **SessionLogService.createLog:** clamps the submitted metrics right after key validation and uses the clamped map for persistence, PR checks, and the response — so out-of-range values are silently capped.
- **PromptBuilder:** system-generator JSON contract now includes `minValue`/`maxValue`, with a rule telling the AI to set 0/100 for percentage/score metrics and 0/null for open-ended ones.

### Design note
Keeps the "no setup, just take off" principle: the AI fills bounds during generation, and any omission falls back to unit-based inference. A future manual-metric feature stays zero-config — typing unit `%` gets 0..100 protection for free. Out-of-range values are clamped silently.

## Session Log — AI Chat Response Auditing

### Goal
Know when the AI (Gemini) is down — record every chat response outcome durably, not just to app logs.

### Design decision — separation of concerns
Initially recorded chat outcomes in `job_execution_logs` (job_name='AiChat'), but that table's concern is *scheduler/cron runs*, and a conversation row is the wrong grain for per-call health (only holds the latest state). Final approach: track outcome on the **`ai_messages` row itself** — a message IS one AI call, so it's the correct grain. Reverted the `job_execution_logs` overloading (removed `JobLogService.log(...)`; JobLogService is scheduler-only again).

### Fix
- **New enum `MessageStatus`:** SUCCESS / DEGRADED / FAILED.
- **`ai_messages`:** added `status VARCHAR(20)` + `error_message TEXT` (CREATE block + `ALTER ... ADD COLUMN IF NOT EXISTS`). Set on the ASSISTANT row.
- **AiMessage entity:** added `status` (@Enumerated STRING) + `errorMessage` fields.
- **AiChatService.chat():**
  - `SUCCESS` — good parsed reply.
  - `DEGRADED` — Gemini responded but JSON didn't parse (raw text used as fallback).
  - `FAILED` — Gemini call threw. **Now still persists a FAILED assistant row** (with fallback text + error) instead of persisting nothing, so outages are visible. Still not counted against the user's daily limit.
- **Privacy:** message/reply content stays in `ai_messages` as before; status/error are additional columns on the same row.

### Monitoring query
`SELECT created_at, status, error_message FROM ai_messages WHERE role = 'ASSISTANT' AND status <> 'SUCCESS' ORDER BY created_at DESC LIMIT 50;`
A run of `FAILED` rows = Gemini outage.

### Deploy
Run the two `ALTER TABLE ai_messages ADD COLUMN IF NOT EXISTS ...` on prod DB, then deploy the jar.

## Session Log — Domain Context Field + Add-Domain UX + Log Validation

### Domain context (tackle ambiguous domains, e.g. LANGUAGE → "which language?")
- **Problem:** domain creation only had type + skillLevel + optional customName. Ambiguous types (LANGUAGE, READING, GYM) gave the AI too little to generate a good plan.
- **Schema:** added `context VARCHAR(500)` to `domains` (CREATE block + `ALTER ... ADD COLUMN IF NOT EXISTS`). Optional; blank still yields a generic plan.
- **Entity/DTOs:** `Domain.context` (+ toResponse); added `context` to `CreateDomainRequest`, `UpdateDomainRequest` (editable), and `DomainResponse`.
- **Service:** `createDomain` sets context and passes it to generation; `updateDomain` updates it (Optional.ifPresent).
- **AI wiring:** `AiSystemGeneratorService.generateSystem(...)` gained a `context` param; `regenerateSystem` passes `domain.getContext()`. `PromptBuilder.systemGenerator(...)` appends the context line ("tailor the plan, metrics, milestones to this") when non-blank.

### App — add-domain.tsx
- **Custom is now a button** (part of the preset grid) instead of an always-visible text field that hijacked selection. Selecting Custom reveals a "Name your domain" field.
- **Context field:** optional "Anything specific?" input shown once a type is chosen (placeholder "e.g. Spanish, conversational focus"), passed as `context`.
- Types: added `context?` to `CreateDomainRequest`, `context` to `DomainResponse`.

### App — log.tsx metric input validation (matches server clamp)
- Metric inputs now clamp to `minValue`/`maxValue` as the user types (e.g. typing 112 for a 0–100 metric becomes 100 immediately, instead of silently returning 100 after save). Shows "(max N)" hint next to the unit.
- Types: added `minValue`/`maxValue` to `MetricDefinitionResponse`.

### Known pre-existing issue (not caused here)
`tsc` reports 4 errors in `src/app/(auth)/setup-review.tsx` referencing `linkedResourceUrl`/`linkedResourceTitle` — dead code from the earlier domain-level linked-resource removal. Not touched (out of scope); flagged for cleanup.

### Deploy
`ALTER TABLE domains ADD COLUMN IF NOT EXISTS context VARCHAR(500);` on prod, then jar + restart. App via Expo.

## Session Log — Auto Milestone Top-Up (scheduled)

### Goal
When a user completes all their milestones, they were left with none — no automatic "what's next." Added a scheduled top-up that generates the next milestone.

### Implementation
- **PromptBuilder.nextMilestone(domain, metrics, lastCompleted):** builds a prompt for ONE progressive milestone — feeds domain info + context, available metricKeys, and the most-recently-completed milestone so the AI raises the bar. Returns single-milestone JSON (label, metricKey, targetValue, unit, future deadline).
- **AiSystemGeneratorService.generateNextMilestone(domain, metrics, lastCompleted):** calls Gemini, parses into `MilestoneResponse`; returns null on failure (caller skips, retries next run).
- **MilestoneService.generateNextMilestone(domain):** returns boolean. Logic:
  - skip if any milestone is UPCOMING/IN_PROGRESS (still has goals),
  - **guard:** skip unless ≥1 DONE milestone (only tops up domains the user *completed* their way to empty, not ones cleared deliberately),
  - finds last-completed (by completedAt, fallback createdAt), calls AI, saves the new milestone UPCOMING + aiGenerated=true, runs updateProgress.
- **MilestoneTopUpScheduler (new):** daily `cron 0 0 2 * * ?` (2 AM, distinct from midnight/5 AM jobs). Iterates ACTIVE domains, calls generateNextMilestone, per-domain try/catch. Wrapped in `jobLogService.run("MilestoneTopUpScheduler", ...)` → audited in job_execution_logs. Returns "Generated N next milestones across M active domains".

### Decisions (per user)
1 milestone at a time; guard on ≥1 DONE; AI picks the deadline.

### No schema change
Reuses existing milestones table. No new DB columns.

### Dependency note
MilestoneService now injects AiSystemGeneratorService + (existing) DomainService. Verified no Spring bean cycle: neither dependency injects MilestoneService back.

## Session Log — Remove Export, Add Profile Update

### Removed data export
- Server: deleted `ExportService`; `UserController` no longer has `exportData` (removed GET /api/users/export).
- App: removed `exportData()` from services/user.ts, removed Data/export section from settings, removed `USERS.EXPORT` endpoint. (expo-file-system/expo-sharing left installed but unused.)

### Added profile update
- **PATCH /api/users/profile** → `AuthService.updateProfile(userId, UpdateProfileRequest)`: updates firstName/lastName; optional password change requires currentPassword (verified via passwordEncoder.matches) + newPassword (min 8). New `UpdateProfileRequest` DTO.
- `UserResponse` + `User.toResponse()` now include `lastName`.
- App: `updateProfile()` service (api.patch), settings.tsx rewritten with editable name fields + change-password fields + Save; loads firstName/lastName from profile.

### No schema change
Uses existing users columns (first_name/last_name/password already exist).

### Deploy
Just rebuild jar + restart (no ALTER). Export endpoint removal is backward-safe.

## Session Log — Task Generation Cap (fix over-generation)

### Bug found
`TaskGenerationService.generateTasksForDomain` guard only checked tasks due within the next 30 days (`findByUserIdAndDueDateBetween...`). Tasks due >30 days out (or overdue) were invisible to the guard, so it kept generating even when a domain already had many open tasks. Logs showed daily task generation producing tasks despite large pending backlogs. (`pendingTasks` var was also computed but never used — dead code.)

### Fix
- **Repository:** added `countByDomainIdAndStatusIn(domainId, statuses)`.
- **Guard rewritten:** counts ALL open (TODO/IN_PROGRESS) tasks for the domain, no date window; skips generation if `count >= maxTasksPerDomain`.
- **Config:** `tasks.generation.max-per-domain=${MAX_TASKS_PER_DOMAIN:5}` in application.properties, injected via `@Value` into TaskGenerationService. Default 5.
- Decision: kept as a config property (env-overridable) for now rather than a DB column / user_preferences table — will move to per-user/plan when the subscription model is built.

### No schema change
Config-only + a derived count query. No DB migration.

### Deploy
Rebuild jar + restart. Set `MAX_TASKS_PER_DOMAIN` env if you want a non-default cap.

## Session Log — YouTube Relevance Fix + Env Docs

### YouTube video relevance (tasks had irrelevant videos)
Root cause: `YouTubeService.findVideo` searched `taskTitle + domain + "tutorial"` with `maxResults=1` and no filters — vague task titles produced off-topic #1 results. **No extra AI call** used for the fix; reused the existing task-generation call.
- **PromptBuilder.taskGeneration:** task JSON now includes a `videoTopic` field — the AI emits a clean 3-6 word YouTube search phrase for the SKILL behind the task (not the literal task text), with examples.
- **TaskGenerationService:** `GeneratedTask.videoTopic` added; passes it to `findVideo` (falls back to title if blank).
- **YouTubeService.findVideo:** first arg is now the topic; appends domain only if not already present; search adds `order=relevance&relevanceLanguage=en&videoEmbeddable=true&safeSearch=moderate&maxResults=5` and picks the first valid result. Search-URL fallback unchanged.
- Other callers (`TaskService.createTask`, `DomainService.applyGeneratedSystem`) unchanged — still pass a title (no videoTopic there yet); they benefit from the new relevance filters. Could add videoTopic to AI-generated milestones/initial tasks later.

### Env documentation
- Added `.env.example` (server) documenting all vars: JWT_SECRET, DATABASE_URL, DB_USERNAME, DB_PASSWORD, GEMINI_API_KEY, AI_DAILY_LIMIT, MAX_TASKS_PER_DOMAIN, YOUTUBE_API_KEY.
- README env section rewritten to env-var style; corrected the false "Flyway auto-migrates" claim (schema is applied manually; ddl-auto=none).

### No schema change. Deploy = rebuild jar + restart.

## Session Log — Auth Phase 1: Forgot Password (Resend) + SSO prep

### Forgot password (6-digit code via Resend)
- **SQL:** new `password_reset_tokens` table (user_id FK, token_hash, expires_at, used, created_at) + index. Added to schema file with `CREATE TABLE IF NOT EXISTS`.
- **Entity/Repo:** `PasswordResetToken` + `PasswordResetTokenRepository` (`findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc`).
- **EmailService (new):** sends via Resend HTTP API (`RESEND_API_KEY`, `MAIL_FROM` envs, default sender `onboarding@resend.dev`). Failures logged, never thrown.
- **AuthService:** `forgotPassword(email)` — no enumeration (always succeeds to caller); if a LOCAL account exists, stores SHA-256 hash of a random 6-digit code (30-min expiry) and emails the code. SSO/no-password accounts are skipped. `resetPassword(email, code, newPassword)` — validates latest unused token (expiry + hash match), BCrypt-updates password, marks token used.
- **Endpoints:** `POST /api/auth/forgot-password`, `POST /api/auth/reset-password` (both public under /auth/**). New DTOs ForgotPasswordRequest, ResetPasswordRequest.
- **Config:** `resend.api.key`, `mail.from` in application.properties + .env.example.

### SSO prep (for Phase 2 Google)
- **users:** added `auth_provider VARCHAR(20) DEFAULT 'LOCAL'` (CREATE + ALTER); made `password` nullable (`ALTER COLUMN password DROP NOT NULL`) so SSO users are valid.
- **User entity:** `authProvider` field, defaults to LOCAL in onCreate.
- Duplicate prevention relies on existing unique email; Phase 2 will find-or-create by email (account linking).

### Deploy (run on prod before restarting the new jar)
ALTER users add auth_provider + drop NOT NULL on password; CREATE password_reset_tokens.
Set RESEND_API_KEY (and MAIL_FROM) env. No Apple/Google yet.

### Verified: backend compileJava BUILD SUCCESSFUL; app tsc clean on new files.

## Session Log — Password Reset Hardening (rate limit, brute-force, cleanup)

Builds on Phase 1 forgot-password. All limits are config-driven (no hardcoding).

### Config (application.properties + .env.example)
- `auth.password-reset.expiry-ms` (PASSWORD_RESET_EXPIRY_MS, default 1800000 = 30 min) — code lifetime, now configurable in ms.
- `auth.password-reset.max-requests` (default 3) + `auth.password-reset.request-window-hours` (default 12) — send-email rate limit.
- `auth.password-reset.max-attempts` (default 10) — wrong-code guesses before a code is invalidated.

### Schema
- `password_reset_tokens.attempts INT DEFAULT 0` added (CREATE block + `ALTER ... IF NOT EXISTS`). Brute-force counter per code. No new table.

### Behavior
- **Send throttle:** `forgotPassword` counts codes created in the last `request-window-hours`; if >= `max-requests`, skips silently (still 200, no enumeration) — protects Resend/API cost.
- **Invalidate old:** issuing a new code marks all prior unused codes for that user `used=true` (repo `invalidateAllForUser`), so only the newest is valid.
- **Configurable expiry:** `expiresAt = now + resetExpiryMs`.
- **Brute-force cap:** `resetPassword` increments `attempts` on wrong code; at `max-attempts` the code is marked used (invalidated) and the user must request a new one.
- **Single-use on success:** unchanged — token marked used after a successful reset.

### Cleanup scheduler (new, logged like all others)
- `PasswordResetTokenCleanupScheduler` — hourly `cron 0 0 * * * ?`; `deleteUsedOrExpired(now)` removes used/expired rows. Wrapped in `jobLogService.run("PasswordResetTokenCleanupScheduler", ...)` → audited in job_execution_logs.

### Repo additions
`countByUserIdAndCreatedAtAfter`, `invalidateAllForUser` (@Modifying), `deleteUsedOrExpired` (@Modifying).

### Deploy
Run `ALTER TABLE password_reset_tokens ADD COLUMN IF NOT EXISTS attempts INT DEFAULT 0;` on prod with the other pending ALTERs. Optionally set the PASSWORD_RESET_* envs (all have defaults).

## Session Log — File Logging (rolling + auto-cleanup)

- **logback-spring.xml:** added a RollingFileAppender alongside the existing console appender. Writes to `${LOG_DIR:-logs}/app.log` (folder auto-created; on prod that's `/home/ubuntu/logs/`).
- **Rolling + cleanup:** keeps newest, deletes oldest — rolls daily and past `LOG_MAX_FILE_SIZE` (10MB); keeps `LOG_MAX_HISTORY` days (14); hard cap `LOG_TOTAL_SIZE_CAP` (200MB), oldest deleted beyond it; `cleanHistoryOnStart=true`.
- **Configurable:** LOG_DIR, LOG_MAX_FILE_SIZE, LOG_MAX_HISTORY, LOG_TOTAL_SIZE_CAP env vars (defaults in .env.example). Console logging unchanged (journalctl still works).
- Purpose: persistent, greppable logs for debugging email/AI issues without relying on the IntelliJ console or journal scrollback.
  - `tail -f /home/ubuntu/logs/app.log` or `grep -i email /home/ubuntu/logs/app.log`.
