package com.secondbrain.second_brain_server.service.ai;

/**
 * Static prompt text blocks, extracted from {@link PromptBuilder} to keep that class focused on
 * weaving dynamic data into prompts. These are the large, fixed instruction/schema sections —
 * the dynamic parts (dates, domain fields, loops over logs/metrics) stay in PromptBuilder.
 *
 * Keep text here byte-for-byte equivalent to what the AI previously received unless intentionally
 * tuning behavior (any change here changes model output).
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    /**
     * JSON object schema for system generation. Includes the metric `scale` hint
     * (BOUNDED_100 | OPEN_ENDED), which the server (MetricBounds) uses to resolve min/max
     * deterministically — far more reliable than asking the model for numeric bounds.
     */
    public static final String SYSTEM_GENERATOR_SCHEMA = """
            The output MUST be a valid JSON object with the following structure:
            {
              "planDescription": "string",
              "weeklySchedule": "string (MUST be comma-separated 3-letter days ONLY, e.g. Mon,Wed,Fri — no spaces after commas, no full day names)",
              "metrics": [
                {
                  "metricKey": "string",
                  "label": "string",
                  "unit": "string",
                  "isTrackedPerSession": boolean,
                  "isPR": boolean,
                  "isHigherBetter": boolean,
                  "scale": "BOUNDED_100 | OPEN_ENDED",   // BOUNDED_100 = a 0-100 score/rating/quality/percentage; OPEN_ENDED = a cumulative quantity (minutes, reps, distance, count)
                  "minValue": number or null,   // ALWAYS 0 for scores, percentages, counts, and most metrics
                  "maxValue": number or null,   // 100 for any BOUNDED_100 metric; null for OPEN_ENDED
                  "displayOrder": integer
                }
              ],
              "milestones": [
                {
                  "label": "string",
                  "metricKey": "string",
                  "targetValue": double,
                  "unit": "string",
                  "deadline": "YYYY-MM-DD"
                }
              ],
              "tasks": [
                {
                  "title": "string",
                  "description": "string",
                  "dueDate": "YYYY-MM-DD"
                }
              ]
            }

            """;

    /** Fixed guidelines for system generation (domain-agnostic). */
    public static final String SYSTEM_GENERATOR_GUIDELINES = """
            IMPORTANT GUIDELINES:
            - planDescription MUST be SHORT: 1-2 sentences max, summarizing cadence and focus for THIS domain (e.g. frequency per week + the main emphasis). Do NOT write paragraphs.
            - weeklySchedule: Use short 3-letter day names like 'Mon,Wed,Fri' NOT full day names.
            - All deadlines and due dates MUST be in the future relative to today's date.
            - For metrics, provide 3-5 metrics that are the STANDARD, conventional measures a serious practitioner, coach, or teacher in this specific domain would genuinely track to gauge progress. Use metrics that are widely recognized for this activity — NEVER invent contrived, gimmicky, or oddly-specific metrics just to have a number. If unsure, prefer the obvious, commonly-used metric over a clever one.
            - Metric scale (CRITICAL): set "scale" for EVERY metric. Use "BOUNDED_100" for anything that is a score, rating, quality, accuracy, or percentage (conceptually a fixed 0-100 or 0-10 scale) and give it minValue: 0, maxValue: 100. Use "OPEN_ENDED" for truly additive quantities with no natural ceiling (minutes, reps, distance, counts) and give it minValue: 0, maxValue: null. When in doubt for a score/rating-type metric, choose BOUNDED_100 — never leave a score unbounded.
            - For milestones, provide 2-3 achievable milestones with realistic deadlines.
            - For tasks, provide 2-3 initial tasks with due dates in the next 1-2 weeks.

            """;

    /** Fixed instruction list for incremental task generation. */
    public static final String TASK_GENERATION_INSTRUCTIONS = """
            INSTRUCTIONS:
            - Tasks MUST follow a structured curriculum, like a well-designed syllabus for this domain: each task is the next logical step that builds on the fundamentals the user has already covered, in the order a good teacher would sequence them. Do NOT invent random, disconnected activities.
            - Calibrate the step size to the user's demonstrated pace and skill level: if recent progress is strong/fast, advance to a more challenging next step; if progress is slow or they are struggling, reinforce or consolidate the current level before advancing. The same domain should produce different next steps for a fast learner vs. a struggling beginner.
            - Tasks should align with their weekly schedule days.
            - Due dates MUST be within the next 7 days, starting from tomorrow.
            - Each task should be specific and actionable, with concrete targets drawn from this domain's own metrics and skill level — not vague (prefer a measurable, named action over 'do some practice').
            - If the user has been feeling tired/rough, suggest lighter recovery sessions.
            - If streak is 0 or low, prioritize getting them back on track with achievable tasks.

            """;

    /** Fixed JSON output schema for incremental task generation. */
    public static final String TASK_GENERATION_OUTPUT = """
            Output MUST be a valid JSON array:
            [
              {
                "title": "string (concise, max 60 chars)",
                "description": "string (1-2 sentences with specifics)",
                "dueDate": "YYYY-MM-DD",
                "videoTopic": "string: a short, generic YouTube search phrase (3-6 words) for a tutorial that teaches the SKILL or concept behind this task — NOT the literal task text. Strip any personal numbers/targets and describe the underlying technique a learner would search for."
              }
            ]
            """;
}
