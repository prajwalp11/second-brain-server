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

## Session Log — Insights Caching, AI Cost Cleanup, Dead-Code Removal

### Removed per-session AI insight (dead cost)
`session_logs.ai_insight` was generated via Gemini on every session log but never displayed or used in any metric/chart/PR. Fully removed: call site in SessionLogService, the field (entity + toResponse + SessionLogResponse DTO + app type), and the column (`ALTER TABLE session_logs DROP COLUMN IF EXISTS ai_insight`). Deleted the now-orphaned `AiInsightService`, `PromptBuilder.sessionInsight`, `PromptBuilder.weeklyInsight`, and the dead `WeeklyReviewScheduler` (empty no-op that was scaffolding for an unbuilt weekly-review feature).

### Progress-tab insights now cached (no per-load AI call)
Previously `InsightsService.getInsights` called Gemini on EVERY Progress tab load (slow + costly).
- New `user_insights` table (one row/user: highlights/patterns/suggestions as JSON TEXT + generated_at) + `UserInsight` entity + `UserInsightRepository`.
- `InsightsService.getInsights` is now READ-ONLY: returns cached row (no AI) else short computed fallbacks.
- `InsightsService.generateAndStore` does the Gemini call + caches — called only by the cron.
- **Shortened output:** AI prompt now requires each item ≤ 8 words, punchy; all fallback strings rewritten short (e.g. "Gym idle 5d — jump back in").
- New `InsightsRefreshScheduler` — 3 AM daily, iterates users, `generateAndStore`, wrapped in `JobLogService` (audited in job_execution_logs like all schedulers).

### Deploy (prod SQL)
`ALTER TABLE session_logs DROP COLUMN IF EXISTS ai_insight;` + `CREATE TABLE user_insights (...)` (both in schema file). Then jar + restart. First Progress loads show fallback text until the 3 AM cron populates user_insights.

### Dead code removed (prod hygiene)
AiInsightService, WeeklyReviewScheduler, PromptBuilder.sessionInsight/weeklyInsight — all deleted. Verified no remaining references.

## Session Log — Insights Manual Refresh + Setup Note Simplified

- **Manual insights refresh:** added `POST /api/insights/refresh` (InsightsController) → `insightsService.generateAndStore(userId)` then returns the fresh insights. Lets you regenerate on demand without waiting for the 3 AM cron (useful right after deploy / testing).
- App: `refreshInsights()` service fn (timeout 0, since it triggers a Gemini call) + a refresh icon on the Progress tab's AI Insights section header (spinner while running).
- **Insights input is per-USER, one call per user** (not per-domain): prompt feeds aggregate counts (domains, recent logs, PRs) from UserContextAssembler.assemble(userId). NOTE: this is intentionally light input — insights are generic. Enriching the prompt with per-domain detail (streaks/progress/feel) is a future improvement.
- **Setup-review note** simplified to: "You can edit your plan, schedule, and tasks anytime from the AI chat."

## Session Log — Insights Reworked to PER-DOMAIN (correction)

Earlier insights were account-wide (one row/user, fed only aggregate counts) — wrong, since the Progress tab is per-domain. Reworked to per-domain:
- **Table:** `user_insights` → **`domain_insights`** (PK domain_id, FK→domains ON DELETE CASCADE). JSON columns highlights/patterns/suggestions + generated_at. (user_id not stored — ownership derived via domain.)
- **InsightsService:** now `getInsights(userId, domainId)` (read-only, ownership-checked) and `generateAndStore(userId, domainId)`. Prompt built from that domain's REAL data via `UserContextAssembler.assembleForDomain` — recent logs (duration/feel/metrics), streak, PRs, active milestones, weekly stats. Items ≤8 words. Fallback is short + domain-specific.
- **Endpoints:** `GET /api/insights?domainId=` and `POST /api/insights/refresh?domainId=` (manual, per domain per user).
- **InsightsRefreshScheduler (3 AM):** iterates every ACTIVE domain, generateAndStore per domain. Cost note: now 1 Gemini call per active domain/day (was per user).
- Deleted UserInsight entity + UserInsightRepository.
- App: Progress tab loads/refreshes insights for the SELECTED domain (moved out of loadInitial into the per-domain effect; refresh + pull-to-refresh pass domainId).

### Deploy SQL
`CREATE TABLE domain_insights (...)` (replaces user_insights — if you already created user_insights on prod, drop it: `DROP TABLE IF EXISTS user_insights;`).

## Session Log — Email Verification (OTP) on Signup

### Flow
Signup creates an UNVERIFIED account + emails a 6-digit OTP → app routes to verify screen → `POST /auth/verify-email` flips `email_verified=true`. **Login is gated:** an unverified EMAIL account can't log in — `login` throws `EmailNotVerifiedException` (HTTP 403, body message `EMAIL_NOT_VERIFIED`) AND re-sends a code, so the app routes to the OTP screen on every unverified login attempt (handles the back-button-after-signup case).

### Backend
- **users.email_verified BOOLEAN DEFAULT FALSE** (CREATE + ALTER). Existing users grandfathered: `UPDATE users SET email_verified = TRUE`.
- **email_verification_tokens** table + `EmailVerificationToken` entity + repo — identical OTP pattern to password reset (hash, expiry, single-use, attempts).
- **AuthService:** `register` → unverified + `sendVerificationCodeFor`; `verifyEmail(email, code)`; `resendVerification(email)`; `login` gate. Reuses the `hashCode` SHA-256 helper.
- **EmailService.sendVerificationCode** (reuses Resend path).
- **Exception:** `EmailNotVerifiedException` → 403 `EMAIL_NOT_VERIFIED` in GlobalExceptionHandler.
- **Endpoints:** `POST /auth/verify-email`, `POST /auth/resend-verification` (resend reuses ForgotPasswordRequest {email}). New DTO `VerifyEmailRequest {email, code}`.
- **Cleanup:** the hourly PasswordResetTokenCleanupScheduler now also purges email_verification_tokens.

### Config — CONSOLIDATED
Password-reset + email-verification now share ONE config namespace **auth.otp.*** (AUTH_OTP_EXPIRY_MS / MAX_REQUESTS / WINDOW_HOURS / MAX_ATTEMPTS). Replaced the separate password-reset.* and email-verification.* props (8 → 4). NOTE: password reset send cap changed 3 → 5 as a side effect of sharing AUTH_OTP_MAX_REQUESTS=5.

### App
New `(auth)/verify-email.tsx` OTP screen; register routes to it; login catches 403 EMAIL_NOT_VERIFIED → routes to it; root `_layout` gate allows verify-email for logged-out users. Services: `verifyEmail`, `resendVerification`.

### SDK 58 bump fixes (unrelated but required for green build)
`(tabs)/_layout.tsx` tabBarIcon color cast to string; `ai.tsx` scrollRef typed `ComponentRef<typeof ScrollView>`. App tsc now fully green.

### Deploy SQL
`ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;` + `UPDATE users SET email_verified = TRUE;` (grandfather) + `CREATE TABLE email_verification_tokens (...)`.

## Session Log — Log Flow Rework, Weekly Snapshot Fix, Prompt Hardening

### Session log no longer auto-completes tasks (bug fix)
- **Problem:** `SessionLogService.createLog` called `taskService.markDueTasksDoneForDomain(domainId, logDate)`, which marked **every** TODO/IN_PROGRESS task due ≤ logDate as DONE — so logging one session wrongly completed unrelated tasks (e.g. logging a workout completed "buy equipment").
- **Fix:** removed that call from `createLog` and **deleted** `TaskService.markDueTasksDoneForDomain` (now dead) + the unused `taskService` field from `SessionLogService`.
- **New model:** task completion is now explicit — the app's Log tab shows today's open tasks for the picked domain and the user chooses which one they're logging; that single task is marked DONE via the existing `PUT /tasks/{id}/status`. No server change needed for completion beyond removing the auto-sweep.

### Weekly snapshot: average bounded metrics instead of summing (bug fix)
- **Problem:** `WeeklyStatService.aggregateMetric` always SUMMED a metric over the week. For bounded score/rating metrics (0–100) this pushed the weekly value far past the cap (e.g. a technique score summing to 400+).
- **Fix:** `aggregateMetric` now branches on the metric's existing `maxValue`:
  - `maxValue != null` (bounded/score/percentage) → **AVERAGE** over the week, then clamp to `[minValue, maxValue]`.
  - `maxValue == null` (open-ended: time, counts, distance…) → **SUM** (unchanged).
- **Repo:** added `SessionMetricValueRepository.avgMetricForPeriod(...)` mirroring `sumMetricForPeriod`.
- Uses the **existing** per-metric `minValue`/`maxValue` columns — no new fields. Applies to both `/dashboard/weekly-snapshot` and the Home dashboard (same service).
- **Note:** this only self-heals metrics that actually have `maxValue` set. Legacy score metrics created with a NULL `maxValue` still sum — fix by either regenerating the domain (prompt now reliably sets bounds) or a one-off `UPDATE domain_metric_definitions SET max_value = 100, min_value = 0 WHERE metric_key = '<key>';`.

### PromptBuilder hardening (domain-agnostic)
- **Metric bounds rule** rewritten as CRITICAL + annotated inline in the JSON spec so the AI reliably sets `maxValue: 100` for any score/rating/percentage metric (never null), and `null` only for truly open-ended additive quantities.
- **Metric selection:** must be the STANDARD, conventional measures a practitioner/coach in that domain actually tracks — never contrived/gimmicky metrics.
- **Task generation:** must follow a STRUCTURED curriculum (syllabus-style next step building on fundamentals), and calibrate step size to the user's demonstrated pace/skill (fast learner → harder next step; struggling → consolidate).
- Removed all hardcoded domain-specific examples from the prompt (no "Push Pull Legs", "bench 3x8", "Spanish vocab", "breathing technique score", "rep ranges") — the app is domain-agnostic.

### Insights manual refresh (app button removed)
- The app's manual "refresh insights" button was a dev convenience. Removed from the app (button + handler + service fn). Insights now come **only** from the 3 AM scheduler. The backend `POST /api/insights/refresh?domainId=` endpoint is **kept** for manual/Postman calls.

### Postman
- Fixed Insights "Get AI Insights" to include required `?domainId=`; added "Refresh AI Insights (manual)" request.

### Streaks (already instant — no change)
- Confirmed `createLog` → `DomainService.updateStreakForDomain` recomputes via `StreakCalculator` and **saves immediately** on every log. Streak is correct in the DB the instant a session is logged; any perceived lag is a client refresh timing issue, not server.

### Deploy SQL
No new tables this batch. If fixing a legacy unbounded score metric, run the `UPDATE domain_metric_definitions SET max_value = 100 ...` noted above.

## Session Log — Account Deletion (OTP + password) + OTP Token Consolidation

### Shared OTP token (refactor — removed 3x duplication)
- Replaced the three separate tables/entities/repos (`password_reset_tokens`, `email_verification_tokens`, and a briefly-added `account_deletion_tokens`) with ONE `otp_tokens` table + `OtpToken` entity + `OtpPurpose` enum (`EMAIL_VERIFICATION|PASSWORD_RESET|ACCOUNT_DELETION`) + `OtpTokenRepository`.
- Collapsed the duplicated issue/verify logic into two shared `AuthService` helpers: `issueOtp(user, purpose)` (rate-limit + invalidate-old + store-hashed, returns raw code or null if limited) and `consumeOtp(userId, purpose, code)` (expiry/hash/attempt-cap validation + mark used). forgot/reset/verify/resend/request-delete/delete all route through these.
- Deleted old entities/repos + `PasswordResetTokenCleanupScheduler`; new `OtpTokenCleanupScheduler` (hourly, one `deleteUsedOrExpired` over all purposes).

### Account deletion (soft-delete user, wipe data; OTP + password gated)
- `users.is_deleted` BOOLEAN flag (CREATE + idempotent ALTER + grandfather). `login` rejects soft-deleted accounts.
- `AuthService.requestAccountDeletion(userId)` emails an OTP; `deleteAccount(userId, code, password)` verifies **current password AND OTP**, then wipes data and soft-deletes (is_deleted=true, password cleared, **email retained on the row**).
- **Email re-registration:** email uniqueness is enforced by a **partial unique index** `UNIQUE(email) WHERE is_deleted = false` (replaces the plain UNIQUE constraint), so a soft-deleted row keeps its exact original email yet the address can be registered again. All email lookups (`register` existence check, `login`, reset/verify/resend, and `UserDetailsServiceImpl.loadUserByUsername`) use the active-only `...AndIsDeletedFalse` repo methods; the plain `findByEmail`/`existsByEmail` were removed. A soft-deleted account therefore cannot log in, reset, or verify.
- **Data wipe extracted to `AccountDeletionService.wipeUserData(userId)`** (SRP — keeps AuthService from injecting 6 extra delete repos; AuthService went 11→5 beans). Deletes PRs, tasks, session logs, nudges, AI conversations, domains; DB `ON DELETE CASCADE` handles metric defs/milestones/insights/metric-values/ai-messages.
- Added bulk `deleteByUserId` (`@Modifying @Query`) to Domain/Task/PersonalRecord/SessionLog/AiNudge/AiConversation repos.
- `EmailService.sendAccountDeletionCode`; `DeleteAccountRequest {code, password}` DTO; endpoints `POST /auth/request-account-deletion` + `POST /auth/delete-account` (both @CurrentUser).

### Deploy SQL
`CREATE TABLE otp_tokens (...)` + `ALTER TABLE users ADD COLUMN IF NOT EXISTS is_deleted ...` + grandfather UPDATE + `DROP TABLE IF EXISTS password_reset_tokens / email_verification_tokens / account_deletion_tokens` (OTPs are disposable) + `ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key` + `CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_active ON users(email) WHERE is_deleted = FALSE`. All idempotent in V1.

## Session Log — Weekly Snapshot + Streak + Prompt (earlier this session)
- **Weekly snapshot bug:** `WeeklyStatService.aggregateMetric` now AVERAGES bounded metrics (maxValue != null) over the week and clamps to [min,max]; SUMS open-ended ones. Added `SessionMetricValueRepository.avgMetricForPeriod`. Fixes score metrics (0-100) exceeding cap.
- **Streak logic:** `StreakCalculator.compute` now counts ANY logged day toward the streak; the weekly schedule only BREAKS it (a past scheduled day with no log). Today never counts as a missed day. Streak remains cached in `domains.current_streak/longest_streak`, recomputed on log / midnight scheduler / manual recalc.
- **PromptBuilder hardening (domain-agnostic):** metric bounds rule made CRITICAL + annotated in JSON spec (score/rating/% ⇒ min 0/max 100, never null); metrics must be STANDARD conventional measures for the domain (no contrived metrics); tasks must follow a STRUCTURED curriculum calibrated to the user's pace. Removed all hardcoded domain examples.
