# Design: AI Curriculum + Structured Tasks + Vetted Video Resolution

Status: PROPOSED (design only — no code yet)
Scope: server (Spring Boot) + minor app changes. Production-targeted, fully AI-driven,
domain-agnostic (works for guitar, gym, sailing, any CUSTOM domain, all skill levels).

---

## 1. Problem being solved

Today tasks are regenerated ad-hoc every day with no persistent order, and YouTube videos are the
blind first search result for an AI phrase. Result: tasks feel disconnected and videos feel random /
irrelevant. We want:

- An **ordered curriculum per domain** (tune → notes → octaves → …; or Push/Pull/Legs for gym), that
  the user progresses through like milestones.
- **Reliable, step-specific videos** that read like a course in order — without per-domain authoring.

Constraints (from product):
- Fully **AI-driven**, **domain-agnostic**, **all skill levels**, **no curated templates**.
- Videos must be as "tried & tested" as achievable automatically — enforced by **server-side signals**,
  tuned to work even for long-tail domains (sailing) via **relative ranking**, not fixed popularity bars.
- Gemini **cannot browse the web** — it only generates text. So the AI supplies *structure + precise
  queries*; all real URLs come from the **YouTube Data API**. The AI never emits a video/playlist URL
  (prevents hallucinated links).

---

## 2. Core model

**AI generates an ordered curriculum ONCE at domain creation; the server persists it, enforces
structure, and advances through it. Each task is instantiated from a curriculum step. Videos are
resolved per step from the YouTube API with quality ranking + sanity gating + caching.**

### Locked decisions (reviewed & confirmed)
- **D1 — One ordered list of steps for EVERY domain.** A curriculum is a strictly ordered sequence
  (0,1,2,…). Domains that "repeat" (e.g. workout splits, rotating practice blocks) are expressed simply
  as **more steps further down the list** (the AI emits them in order, each cycle harder), and the course
  auto-EXTENDs when finished. No special loop/branch/parallel-track logic in v1. Fully domain-agnostic.
  (Free "jump between parallel tracks" is a possible Stage-2 enhancement, not needed now.)
- **D2 — A video is stored in ONE place: the shared `video_cache`.** A `curriculum_step` references the
  cache (by `query_key`) and reads its video/confidence from there — it does NOT keep its own copy. So
  when the feedback loop demotes/replaces a video, every step pointing at it updates automatically (no
  stale/wrong videos).
- **D3 — A step advances only when ALL of its tasks are DONE.** A step may spawn one or more tasks
  (initial + reinforcement); the CURRENT step becomes DONE and unlocks the next only when every task it
  owns is complete.

Division of responsibility (the robustness principle):

| Concern | Owner |
|---|---|
| Curriculum content, step ordering intent, per-step video query | **AI** |
| Persisted order, progression gating, "generate once / advance not rewrite" | **Server** |
| Real video/playlist resolution, ranking, sanity floors, caching, fallback | **Server + YouTube API** |

---

## 3. Data model (new + changed)

### 3.1 New table: `curriculum_step`  (ONE table, rows scoped per domain via `domain_id`)

Mirrors the existing single-table pattern of `domain_metric_definition` (one table, per-domain rows).

```
curriculum_step
──────────────────────────────────────────────────────────────────────
id                 UUID        PK
domain_id          UUID        FK -> domains(id), indexed, ON DELETE CASCADE
order_index        INT         strict sequence within the domain (0,1,2,…)
phase              VARCHAR      AI label: "Push Day" / "Fundamentals" / null (free-text, domain-agnostic)
title              VARCHAR
description         TEXT
status             VARCHAR      LOCKED | CURRENT | DONE   (server-controlled)
est_minutes        INT          AI estimate (optional)
video_query        VARCHAR      precise per-step YouTube search phrase (AI)
video_query_key    VARCHAR      normalized query_key -> FK into video_cache (D2: single source of truth)
created_at         TIMESTAMP
updated_at         TIMESTAMP

Indexes: (domain_id, order_index) unique; (domain_id, status)
```

> D2: the step holds `video_query` (what to resolve) + `video_query_key` (pointer into `video_cache`).
> The actual `video_url` / `video_title` / `confidence` live ONLY in `video_cache`, so a demote/replace
> there is reflected everywhere instantly. `TaskResponse` fills `linkedResourceUrl` by joining the
> step's `video_query_key` → `video_cache` at read time (or copies the current value when the task is
> instantiated and refreshes on read — see §7.3).

### 3.2 Changed: `tasks`

A task is the actionable, dated instance of the **CURRENT** curriculum step.

```
+ curriculum_step_id  UUID  FK -> curriculum_step(id), nullable (null for legacy/ad-hoc tasks)
+ order_index         INT   mirror of the step's order for display ordering
+ phase               VARCHAR  mirror of step phase for grouping in UI
```

Existing `linkedResourceUrl` / `linkedResourceTitle` continue to hold the video (copied from the step),
so the app needs no new task-video field.

### 3.3 New table: `video_cache` (cross-user reuse + API-quota protection)

```
video_cache
──────────────────────────────────────────────
id              UUID PK
query_key       VARCHAR  normalized query (see rule below) UNIQUE
video_url       VARCHAR
video_title     VARCHAR
channel_id      VARCHAR
source          VARCHAR  DIRECT | PLAYLIST_ITEM | SEARCH_FALLBACK
confidence      VARCHAR  HIGH | MEDIUM | LOW   (YouTube-stats confidence at resolve time)
effective_confidence VARCHAR  recomputed on each vote (blend, §7A.4); read this for display
demoted         BOOLEAN DEFAULT false
resolved_at     TIMESTAMP
Index: query_key UNIQUE
```

`query_key` normalization (deterministic, NO word reordering — reordering would merge different
queries and serve wrong videos): lowercase → trim → strip punctuation → collapse runs of whitespace to
a single space. That's it. Same phrase ⇒ same key; different phrasings stay distinct.

Rationale: the same step query ("guitar tuning standard eadgbe beginner") recurs across users —
resolve once, reuse. Keeps YouTube Data API usage affordable at prod scale and makes picks consistent.

### 3.4 Optional later (NOT v1): `curriculum_playlist`
If we pursue playlist continuity (see §6), a per-domain pinned playlist id + channel. Deferred.

---

## 4. AI curriculum output contract

At domain creation, `systemGenerator` is extended (or a new `curriculumGenerator` prompt) to return an
ordered curriculum. The AI returns ONLY structure + queries — never URLs.

```json
{
  "planDescription": "…",
  "weeklySchedule": "Mon,Wed,Fri",          // user-fixed (already implemented)
  "metrics": [ … ],                          // unchanged
  "milestones": [ … ],                       // unchanged
  "curriculum": [
    {
      "orderIndex": 0,
      "phase": "Fundamentals",               // or "Push Day" for gym; null allowed
      "title": "Tune the guitar to standard EADGBE",
      "description": "…1–2 sentences, ONE concept…",
      "estMinutes": 15,
      "videoQuery": "how to tune a guitar standard tuning beginner"
    },
    { "orderIndex": 1, "phase": "Fundamentals", "title": "Open-string note names", … }
    // … ordered, each step ONE concept, building on the previous
  ]
}
```

### Server validation of the AI curriculum (the "metrics that enforce it")
Applied deterministically; reject+reprompt or auto-fix on violation:

1. **Non-empty, bounded size:** 5–20 steps (reject empty; trim absurd counts).
2. **Contiguous ordering:** `orderIndex` normalized to 0..n-1 regardless of what AI emitted.
3. **Atomicity (one concept per step):** heuristic — reject/split a step whose title bundles multiple
   concepts (e.g. >1 " and "/comma-joined verbs, or title length over a cap). Re-prompt once to split.
4. **Query specificity:** `videoQuery` must be ≥3 words, must not be just the domain name, must contain
   a skill noun (not "practice"/"improve" alone). Reject vague queries → re-prompt or derive from title.
5. **Phase sanity:** phase is free-text but trimmed/capped; null allowed.
6. **Monotonic difficulty (soft):** optionally ask AI to tag difficulty and verify non-decreasing; low
   priority.

If validation fails after one reprompt → fall back to the current flat AI-task behavior for that domain
(graceful degradation; never block domain creation).

---

## 5. Video resolution (reliable, domain-agnostic, long-tail-safe)

Per step, resolve a REAL video from the YouTube Data API. **Relative ranking + soft sanity floors** —
NOT fixed popularity thresholds (those break niche domains like sailing).

### Algorithm `VideoResolver.resolve(videoQuery, domainName)`
1. **Cache check:** normalized `query_key` in `video_cache`? → return it. (No API call.)
2. **Search:** YouTube Data API `search.list` (`type=video`, `maxResults=10`, relevance, English,
   embeddable, safeSearch). Collect candidate video ids.
3. **Enrich:** `videos.list` for `statistics` + `contentDetails` (views, likes, duration) and
   `channels.list`/snippet for channel subs/age where available. (Batched to limit quota.)
4. **Soft sanity floors (reject junk, NOT niceness):**
   - on-topic: title/description contains the skill noun(s) from the query; else drop.
   - duration sane: 60s–90min (drop Shorts-spam and multi-hour streams unless nothing else).
   - not spam-signal: drop brand-new (<X days) AND tiny-channel AND comments-disabled combos.
5. **Relative composite score** over the SURVIVORS (rank within what exists for this niche):
   `score = w1*log(views) + w2*log(subs) + w3*likeRatio + w4*ageTrust + w5*onTopicMatch`
   (weights tuned; log-scaled so sailing's smaller numbers still rank sensibly among themselves).
6. **Pick best survivor.** Confidence:
   - HIGH if strong absolute signals (popular domain) OR clear winner.
   - MEDIUM if thin field but on-topic.
   - LOW if only weak/borderline survivors.
7. **Fallback:** if NO survivor passes sanity → store a **YouTube search-results URL** for the query,
   `source=SEARCH_FALLBACK`, `confidence=LOW`. Never emit a confidently-wrong video.
8. **Cache** the resolved pick by `query_key`.

### Why this works for sailing
Popular domains → high absolute signals → HIGH confidence real videos. Niche domains (sailing) → the
same algorithm ranks **within the smaller sailing universe** and returns the best legitimate result, or
degrades to a search link if nothing is on-topic. One algorithm, scales both ways; "tested" = crowd
engagement where it exists, best-available + sanity elsewhere.

### Quota
`search.list` is the expensive call (100 units). Caching + cross-user reuse + resolving lazily (only the
CURRENT and next step, not all 20 up front) keeps it within the daily budget. See §7.

---

## 6. Course-like continuity (PHASE 2, optional, deferred)
To make consecutive steps feel like one course: after step 0 resolves to a strong channel, bias later
steps' `search.list` with that `channelId` and/or discover the channel's matching **playlist**
(`playlistItems.list`) to pull lessons in order. Deferred to keep v1 shippable; the §5 per-step resolver
already removes the "random" feel. Documented so we don't design v1 in a way that blocks it.

---

## 7. Generation & progression flow

### 7.1 Domain creation (once)
1. `createDomain` → AI returns system incl. `curriculum[]` (§4).
2. Validate curriculum (§4). On fail → reprompt once → else flat-task fallback.
3. Persist `curriculum_step` rows (ordered). Mark step 0 `CURRENT`, rest `LOCKED`.
4. **Resolve video for the CURRENT step only** (lazy; §5). Later steps resolve when they unlock.
5. Instantiate the first Task(s) from the CURRENT step (copy title/desc/video; set due dates against the
   user's `weeklySchedule`).

### 7.2 Task generation is HYBRID (curriculum = topic; logs/progress = calibration)

IMPORTANT: tasks are NOT made from the curriculum alone, and NOT free-form as today. They are a hybrid:

- **Curriculum decides the WHAT (the spine):** the current step sets the topic/skill — e.g. "barre
  chords", "Push Day progression". This supplies the ordering/structure missing today.
- **Existing rich context decides the HOW (calibration):** the generator still receives recent logs,
  PRs, milestones, streak, feel, weekly stats (today's `taskGeneration` context) and uses them to shape
  THIS step's task — lighter/reinforcement if the user is struggling or felt rough, harder/stretch if
  they are excelling, gentle re-entry if a streak broke.

Prompt change: from "generate 2–4 tasks based on progress" (AI picks topic) to "the user is on
curriculum step {title/description}; using their recent logs/PRs/feel/streak, generate the task(s) for
THIS step, calibrated to their demonstrated pace; do not jump to other topics." → structure from the
curriculum, personalization preserved from the logs.

### 7.3 Progression (replaces "regenerate daily")
`DailyTaskGenerationScheduler` / on-demand generation changes from *invent tasks* to *advance curriculum*:
1. **(D3) When ALL tasks owned by the CURRENT step are DONE** (Stage-1 gate; Stage-2 optionally also a
   metric threshold) → mark step `DONE`, unlock next step `CURRENT`, resolve its video, instantiate its
   Task(s) (per §7.2).
2. Not all done → optionally add ONE gentle reinforcement task for the SAME step (no topic drift); this
   new task also counts toward the step's "all done" gate.
3. Never rewrite `DONE` steps. Only status + new tasks advance.
4. Video display reads through to `video_cache` by the step's `video_query_key` (D2), so a demoted/
   replaced video shows the current pick without touching the step row.

### 7.4 Three kinds of curriculum change (the "does it evolve?" rule)
- **ADVANCE** — finishing a step. Not a content change; only `status` LOCKED→CURRENT→DONE.
- **EXTEND** — all steps DONE → AI appends the *next block* (higher `orderIndex`), informed by progress
  so far. Course never ends / has no fixed ceiling.
- **ADAPT** — the upcoming (**LOCKED**) tail can be regenerated from how the user is actually doing
  (insert a remedial step if repeatedly failing; skip ahead if excelling; goal change via AI chat).
  **DONE steps are immutable** — history is never rewritten.

Rule of thumb: **DONE = frozen; LOCKED tail = adaptable; course = extensible.** This is the middle ground
between today's daily drift (bad) and a frozen stone tablet (ignores reality).

ADAPT aggressiveness:
- **Stage 1 (recommended): conservative** — the LOCKED tail changes only on explicit trigger (user asks
  via AI chat, or on EXTEND). Predictable, feels like a real course.
- **Stage 2: adaptive** — server periodically re-evaluates the LOCKED tail from recent performance.
  Deferred (more moving parts / less predictable).

### 7.5 Edits
Schedule/plan/curriculum changes go through AI chat (existing edit surface). A regenerate path rebuilds
the curriculum from the current point: keeps DONE history, replaces the LOCKED tail (an ADAPT).

### 7.6 "What happens when the whole curriculum is finished?" + user changes via AI chat

**When the LAST step is marked DONE (whole curriculum complete):**
1. The server triggers **EXTEND**: the AI generates the *next block* of steps (appended with higher
   `order_index`), using everything the user has done so far (completed steps, logs, PRs, level) so the
   course keeps climbing in difficulty. The first new step becomes `CURRENT`.
2. This repeats indefinitely → the course **never dead-ends**; a committed user always has a next step.
3. Guard: EXTEND runs once per completion (not spammed); if the AI/validation fails, the domain simply
   shows "all caught up — great work" until the next generation attempt (graceful, never an error).
   Optionally the app can show a short "course complete, generating your next block…" state.

**Can the user change the curriculum via AI chat? — Yes.**
AI chat is the single edit surface (consistent with schedule/plan edits). The user can ask things like
"make this harder", "I don't care about X, focus on Y", "skip ahead", "add more theory". The server
applies these as an **ADAPT**:
- **DONE steps stay frozen** (history is never rewritten).
- Only the **LOCKED tail** (and the CURRENT step if not yet started) is regenerated to honor the request.
- Re-numbered contiguously; videos for changed steps resolve lazily as they become CURRENT.
This keeps user control high while preventing the plan from losing coherence or erasing progress.

---

## 7A. Video feedback loop (user-rated confidence)

Goal: make "confidence" learn from the people who actually did the task — the real "tried & tested"
signal — not just YouTube's generic engagement. Because videos are cached and reused across users
(`video_cache`), feedback aggregates and compounds.

### 7A.1 Capture (unobtrusive, specific, honest)
- After a user **completes a task that had a real (non-fallback) video**, show a one-tap prompt:
  **"Was this video helpful?"** 👍 / 👎 — dismissible, asked once per (video, user), changeable.
- Do NOT poll on `SEARCH_FALLBACK` videos. Do NOT nag.
- `POST /api/tasks/{id}/video-feedback { helpful: boolean }`.

### 7A.2 Store
```
video_feedback
──────────────────────────────────────────
id          UUID PK
query_key   VARCHAR   -- keys to video_cache so votes aggregate across all users of that video
user_id     UUID
helpful     BOOLEAN
created_at  TIMESTAMP
UNIQUE(query_key, user_id)   -- one vote per user per video, updatable
```

### 7A.3 Score — volume-aware, NOT raw "% helpful"
Use the **Wilson lower-bound** of the helpful proportion (standard for "is this good given how many
votes"): 1 up / 0 down does NOT read as 100%/HIGH; 50 up / 3 down reads as a stable high score. This
fixes cold-start (unproven ≠ bad) and low-volume noise (one grumpy vote can't tank a good video).

### 7A.4 Blend with the YouTube-stats confidence (§5)
```
effectiveConfidence = weightedBlend(
    youtubeStatsConfidence,   // from §5 ranking (dominant when few votes)
    wilson(userFeedback),     // dominant as vote volume grows
    weight = f(voteCount)     // shifts toward user feedback as votes accrue
)
```
Day 1 behaves exactly like §5; over time it self-corrects toward real-user experience.

### 7A.5 Close the loop — demote + auto-replace (the real payoff)
When a cached video's blended confidence falls **below a threshold with enough votes**, mark it
**demoted** in `video_cache`. Next resolution of that `query_key` **skips the demoted pick and
re-resolves** (next-best §5 candidate), which gets its own fresh trial. Bad videos are not just
flagged — they are **auto-replaced for future users**, so the catalog trends toward vetted.

### 7A.6 Display (honest)
Show aggregate only above a minimum vote count (e.g. "👍 92% helpful (41)"); below that show "new" or
nothing — never a fake percentage from 1 vote. LOW effectiveConfidence shows the "tap to search"
affordance from §5.

### 7A.7 Deferred add-ons (Stage 2)
- 👎 reason second-tap ("off-topic / too advanced / low quality") → biases the re-pick (e.g. "too
  advanced" → prefer beginner on re-resolve).
- Cross-domain network effect already falls out of keying feedback on `query_key`.

---

## 8. LLD (PlantUML) — new/changed elements


```plantuml
' ───────── Entities ─────────
class CurriculumStep {
  - id : UUID <<PK>>
  - domainId : UUID <<FK, ON DELETE CASCADE>>
  - orderIndex : int
  - phase : String  <<AI label: "Push Day" / "Fundamentals" / null>>
  - title : String
  - description : String
  - status : StepStatus  <<LOCKED | CURRENT | DONE>>
  - estMinutes : Integer
  - videoQuery : String     <<AI precise query; server never trusts AI for URLs>>
  - videoQueryKey : String  <<D2: pointer into VideoCache; video/confidence live THERE only>>
  - createdAt / updatedAt
}
enum StepStatus { LOCKED \n CURRENT \n DONE }
enum VideoSource { DIRECT \n PLAYLIST_ITEM \n SEARCH_FALLBACK }
enum Confidence { HIGH \n MEDIUM \n LOW }

class VideoCache {
  - id : UUID <<PK>>
  - queryKey : String <<unique, normalized (lowercase/trim/strip-punct/collapse-space, NO reorder)>>
  - videoUrl / videoTitle / channelId : String
  - source : VideoSource
  - confidence : Confidence  <<YouTube-stats confidence at resolve time>>
  - effectiveConfidence : Confidence <<recomputed on each vote: blend(stats, wilson, f(votes)) §7A.4>>
  - demoted : boolean  <<true -> resolver skips + re-resolves (feedback loop §7A.5)>>
  - resolvedAt : Instant
}

class VideoFeedback {
  - id : UUID <<PK>>
  - queryKey : String  <<aggregates votes across users of the same cached video>>
  - userId : UUID
  - helpful : boolean
  - createdAt : Instant
  .. UNIQUE(queryKey, userId) ..
}

class Task {
  .. existing ..
  - curriculumStepId : UUID <<FK, nullable>>
  - orderIndex : int
  - phase : String
}

' ───────── Services ─────────
class CurriculumService {
  + generateAndPersist(domain, generatedCurriculum) : void   <<validate + order + persist, step0=CURRENT>>
  + advance(domain) : void   <<DONE current, unlock next, resolve video, make task (hybrid §7.2)>>
  + extend(domain) : void     <<all done -> AI appends next block>>
  + adaptLockedTail(domain) : void  <<Stage 2 — regenerate LOCKED tail; DONE immutable>>
  - validate(curriculum) : ValidatedCurriculum  <<§4 rules>>
}
class VideoResolver {
  + resolve(videoQuery, domainName) : ResolvedVideo  <<cache(skip demoted) -> search -> enrich -> sanity -> rank -> pick/fallback -> cache>>
  - score(candidate) : double
  - passesSanity(candidate, query) : boolean
}
class VideoFeedbackService {
  + record(userId, taskId, helpful) : void   <<upsert vote keyed by queryKey>>
  + effectiveConfidence(queryKey) : Confidence  <<blend(youtubeStats, wilson(feedback), f(voteCount)) §7A.4>>
  - maybeDemote(queryKey) : void  <<below threshold w/ enough votes -> video_cache.demoted=true §7A.5>>
}
class YouTubeService {
  + searchVideos(query, max) : List<Candidate>     <<search.list>>
  + enrich(ids) : List<CandidateStats>             <<videos.list + channels.list>>
  + searchUrlFallback(query) : String
}

CurriculumStep "0..*" --> "1" Domain
CurriculumStep "0..*" --> "0..1" VideoCache : by videoQueryKey (D2)
Task "0..*" --> "0..1" CurriculumStep
VideoFeedback "0..*" --> "1" VideoCache : by queryKey
CurriculumService ..> CurriculumStep
CurriculumService ..> VideoResolver
VideoFeedbackService ..> VideoFeedback
VideoFeedbackService ..> VideoCache : demote
VideoResolver ..> YouTubeService
VideoResolver ..> VideoCache
DomainService ..> CurriculumService : on create / regenerate
DailyTaskGenerationScheduler ..> CurriculumService : advance() ; extend() when all DONE
```

(These blocks get merged into `src/main/resources/design/lld.puml` when we build.)

---

## 9. Migration
- Flyway/SQL: create `curriculum_step` (with `video_query`, `video_query_key`; FK `domain_id`
  **ON DELETE CASCADE** so deleting a domain removes its steps), `video_cache` (incl.
  `effective_confidence`, `demoted`), `video_feedback`;
  `ALTER TABLE tasks ADD curriculum_step_id, order_index, phase` (nullable — legacy tasks unaffected).
- `curriculum_step` holds NO video_url/title/confidence of its own (D2 — those live only in
  `video_cache`, joined via `video_query_key`).
- No backfill required; existing domains keep flat tasks until regenerated.
- Feedback path: `POST /tasks/{id}/video-feedback` → task → `curriculum_step` → `video_query_key` →
  `video_feedback`/`video_cache` (so a vote aggregates across all users of that cached video).

---

## 10. API / app changes
- `GET /api/domains/{id}/curriculum` → ordered steps with status (for a "course map" UI).
- `POST /api/tasks/{id}/video-feedback { helpful }` → records a 👍/👎 vote (one per user per video).
- Tasks response already carries video via `linkedResourceUrl`; add `phase`/`orderIndex` for grouped,
  ordered display (gym split days / guitar fundamentals); add `videoConfidence` + aggregate helpful%.
- App: show tasks grouped by `phase`, ordered by `orderIndex`; lightweight course-progress view; on task
  completion with a real video, show a one-tap "Was this video helpful?" prompt (once per video per
  user). LOW effectiveConfidence shows a "tap to search" affordance; aggregate helpful% shown only above
  a vote threshold.

---

## 11. Rollout stages
- **Stage 1 (core):** curriculum table + AI curriculum contract + validation + **hybrid task generation**
  (curriculum topic + logs calibration, §7.2) + advance-not-regenerate (§7.3) + EXTEND on completion +
  per-step VideoResolver (ranking + sanity + cache + fallback) + **video feedback loop** (§7A: votes,
  Wilson-blend confidence, demote + auto-replace). Conservative ADAPT only (explicit trigger). Kills
  "random" for all domains.
- **Stage 2 (polish):** playlist/channel continuity (§6); adaptive LOCKED-tail regeneration (§7.4);
  metric-threshold progression gating; 👎-reason capture; course-map UI.
- CUSTOM / un-templated / validation-failure domains: graceful fallback to current flat AI tasks.

---

## 12. Honest limitations
- Video quality is **best-achievable-automatically**, not human-guaranteed. Niche domains get
  best-available + sanity, or a search link — never a confidently-wrong video. User feedback (§7A)
  improves it over time but needs vote volume to matter.
- Course continuity is best-effort until Stage 2.
- Curriculum ADAPT is conservative in Stage 1 (explicit trigger); automatic performance-based adaptation
  is Stage 2.
- Depends on YouTube Data API quota; caching + cross-user reuse + lazy per-step resolution is the
  mitigation.
```
