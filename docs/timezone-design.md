# Design: Per-User Timezone

Status: **proposal for review** (no code written yet). Author notes from the audit of every
date/time site in the server as of this session.

---

## 1. The problem
All server date/time logic uses `LocalDate.now()` / `LocalTime.now()` with the **JVM default
timezone**, which is the VM's OS timezone — currently **UTC**. For a user in IST (UTC+5:30) this
means the server's idea of "today", "this week", and "what hour is it" is ~5.5h behind the user.

Observed symptoms:
- A task due "today" still shows in **Today's Focus** after the user's local midnight (server still
  thinks it's the previous day).
- Push send windows ("morning 08:00–10:00") fire at ~13:30 IST.
- Streaks, weekly stats, and AI-injected "today's date" are all off by the offset.

The app does **NOT** send or control timezone today; it is entirely server-side (JVM default).

## 2. Core principle (the key insight)
Two independent questions, do not conflate them:

1. **"What day/week/hour is it FOR THIS USER?"** — data/display logic. Must use the user's zone.
2. **"When should a background JOB fire?"** — scheduling. Only matters if the user *directly
   experiences* the firing moment (i.e. a notification lands). Background refreshes can stay on
   server time.

Rule of thumb:
- The user **sees/receives** something at that instant → needs the user's timezone.
- The job just **refreshes data** in the background → server timezone is fine; only its *date math*
  (if it computes "today") needs the user's zone.

Also: **duration-based logic is already correct and must NOT change** — anything "within N hours /
ms" (OTP expiry, OTP rate window, token expiry, push cooldowns) is timezone-independent.

## 3. The fix — capture + use the user's zone
- Add `users.timezone VARCHAR` (IANA id, e.g. `Asia/Kolkata`); nullable → fall back to a configured
  app-default zone when null.
- **App sends** its IANA zone (`Intl.DateTimeFormat().resolvedOptions().timeZone` / Expo
  Localization) on login / register / token-refresh (and/or push-token register). Server upserts it
  on the user row. Keeps it current, survives travel, handles DST automatically (IANA ids, not fixed
  offsets).
- Central helper (single seam) — e.g. `ClockService` / `TimeZones`:
  `zoneOf(user)`, `today(user)`, `weekStart(user)`, `nowTime(user)`, `endOfToday(user)`.
  Every date-boundary site calls this instead of bare `LocalDate.now()`.
- Store instants in UTC (already done via `TIMESTAMP`); convert to the user's zone only at the edges
  (computing "their today" / "their hour"). Never store local times.

---

## 4. COMPLETE site inventory (from a full grep audit)

### Category 1 — MUST use the user's timezone (day-boundary: "today" / "this week" / greeting)
Each becomes `...now(zoneOf(user))` (via the helper).

| # | Location | What it decides |
|---|----------|-----------------|
| 1 | `DashboardController:31` getDashboard(userId, LocalDate.now()) | the "today" for Today's Focus |
| 2 | `DashboardService:85` LocalTime.now() | morning/afternoon/evening greeting |
| 3 | `DashboardService:59` getWeekStart(date) | weekly-stats window (flows from #1) |
| 4 | `TaskService:75` LocalDate.now() | due/overdue + today bucketing |
| 5 | `CurriculumService:299` nextScheduledOnOrAfter(..., LocalDate.now()) | curriculum task due date |
| 6 | `StreakService:34,45` LocalDate.now() | "logged today?" + streak break |
| 7 | `DomainService:190,211` StreakCalculator.compute(..., LocalDate.now()) | streak recompute "today" |
| 8 | `DomainService:353` endDate = LocalDate.now() | chart/range end |
| 9 | `UserContextAssembler:113` getWeekStart(LocalDate.now()) | AI context "this week" |
| 10 | `AiNudgeService:49,104,129` LocalDate.now()/atStartOfDay() | nudge "today" + already-sent-today guard |
| 11 | `PromptBuilder:36,238,240,247,335,388` LocalDate.now() | "today's date" injected into AI prompts |
| 12 | `PushService:128,143` LocalDate today | TASK_DUE "due today" + STREAK "logged today" |
| 13 | `PushService:311` endOfToday() | outbox expiry = user-zone end-of-day |
| 14 | **App** session log `logDate` + task-card "today" label | keep device-local; must agree with server zone |

Note: #10/#11/#9 take context objects, not the `User`/zone — their method signatures must be
threaded with the zone (or user). This is the most invasive part of Category 1.

### Category 2 — Jobs the user DIRECTLY EXPERIENCES (need per-user timezone scheduling)
After review, this shrank to ONLY the two push *windows*. They deliver a notification to the user's
phone, so the local hour matters (a reminder at the wrong local time is useless).

| # | Job | Change |
|---|-----|--------|
| 15 | `PushService:114` LocalTime.now() (window check) | `LocalTime.now(zoneOf(user))` — the planner already loops users, so this is cheap and is the ONLY real per-user-scheduling change |
| 16 | `PushPlannerScheduler` (every 5 min) | no cron change needed; #15 makes it correct for any zone |

WeeklyInsightPush was CONSIDERED here but moved to Category 3 — see below (its content isn't
time-sensitive, so server-time delivery is acceptable).

### Category 3 — Jobs that STAY on server timezone (background; user doesn't feel the hour)
These keep their current cron. If they compute "today/this week" they use the Category-1 helper for
the *date math* only — the firing hour stays server-time.

| Job | Why server-tz is fine |
|-----|-----------------------|
| `DailyNudgeScheduler` (midnight) | nudge is generated, not delivered at that instant; user sees it later |
| `InsightsRefreshScheduler` (3 AM) | pure cache warm; user never experiences 3 AM |
| `MilestoneTopUpScheduler` (2 AM) | background generation |
| `DailyTaskGenerationScheduler` (5 AM) | safety-net top-up; only the DUE DATE it stamps needs user-zone (Category 1 #5), not the cron |
| `AiLimitResetScheduler` (midnight) | low-stakes quota reset; acceptable at server midnight (optional future: per-user) |
| `WeeklyInsightPushScheduler` (Sun 18:00) | the push is only a NUDGE to open the Progress tab — it carries no time-sensitive content (the insight data is refreshed nightly and viewable anytime). A weekly recap arriving at the server-local-equivalent hour is acceptable; keep the fixed cron. Only revisit if going multi-timezone and you care about not pinging at an odd local hour. |

Context for the above (why these are background, confirmed from the app):
- **InsightsRefresh** writes the cached `highlights/patterns/suggestions` shown on the **Progress tab**
  (app calls `GET /insights` → cached row, no live AI). The 3 AM job just keeps that cache fresh.
- **MilestoneTopUp** generates ONE new milestone when a domain has none active but >=1 done; shown in
  the **Progress tab** milestones section. Pure background generation.
- **DailyNudge** generates the nudge row; surfaced in-app later (and/or via push), not at midnight.

### Category 4 — LEAVE ALONE (instants / durations — already correct; changing would add bugs)
- All entity `@PrePersist/@PreUpdate` `LocalDateTime.now()` (createdAt/updatedAt across ~20 entities).
- OTP: `AuthService:358` countBy...CreatedAtAfter(windowStart), OTP expiry, token expiry.
- Push **cooldowns** (`minusHours`), `OtpTokenCleanupScheduler` (hourly, relative).
- `PushSenderScheduler` (every minute, drains queue — not time-of-day).
- `GlobalExceptionHandler` timestamps; completedAt / generatedAt / sentAt / resolvedAt.

---

## 5. Dedup (now essentially a non-issue)
"Dedup" = a per-user "already did this on their local day" record so a job that runs frequently
acts once per user per local day. After the refinement above, **no job moves to hourly**, so no new
dedup work is required:
- Push TASK_DUE/STREAK_AT_RISK already dedup via `push_history` + the 12h cooldown; making the window
  check per-user (#15) doesn't change that.
- WeeklyInsightPush stays a fixed weekly cron (server time) — its existing weekly cooldown already
  prevents repeats; no hourly loop, no new guard.
- All Category 3 jobs stay once-a-day on server time, so they keep their implicit "runs once" dedup.

## 6. Scope decision (refined — "nudge/weekly can stay server-tz" insight applied)
The risky per-user-scheduling work collapses to **exactly one thing**: the push window check
(`LocalTime.now(zoneOf(user))`), inside the planner that already loops users.
- Per-user timezone needed: **TASK_DUE + STREAK_AT_RISK window check only** (#15).
- Everything else is either Category-1 date-math (safe, mechanical) or stays server-tz unchanged
  (DailyNudge, InsightsRefresh, MilestoneTopUp, DailyTaskGen, AiLimitReset, WeeklyInsightPush).

## 7. Recommended staging
- **Stage 0 (now, zero code):** set `TZ=Asia/Kolkata` on the VM (systemd `Environment=TZ=...`).
  Instantly correct for the current single-region (India) user base. Buys time.
- **Stage 1 (the real fix):** `users.timezone` column + app sends it + `ClockService` helper + fix
  **all of Category 1** + the one push-window check (#15). This is effectively the WHOLE
  per-user fix now — there is no heavy job-rework stage left.
- **Stage 2 (optional, only if truly needed):** per-user scheduling for low-priority jobs
  (WeeklyInsightPush at a nice local hour, per-user AI-limit reset). Low value; defer indefinitely
  unless multi-timezone politeness becomes a concern.

## 8. Gotchas
- Thread the zone into `PromptBuilder` / `AiNudgeService` / `UserContextAssembler` (they don't
  currently receive the user/zone) — biggest Category-1 change.
- App must start sending the IANA zone; server upserts it. Null → app-default.
- Keep storing UTC instants; convert at edges only.
- Do NOT touch Category 4 (duration/instant) logic.
- `AiLimitResetScheduler` at server midnight is a minor wart for off-zone users (quota resets
  mid-day); acceptable for now, per-user later if needed.
