package com.secondbrain.second_brain_server.util;

import com.secondbrain.second_brain_server.dto.response.MetricDefinitionResponse;

/**
 * Deterministic resolver for a metric's persisted [minValue, maxValue].
 *
 * The AI is unreliable at consistently emitting numeric bounds, which repeatedly produced
 * "0-100 score" metrics stored as open-ended (no max) — so their weekly totals wrongly exceeded
 * the cap. This class makes the SERVER the source of truth. Resolution order (first match wins):
 *
 *   1. Explicit AI `scale` hint: BOUNDED_100 -> [0,100]; OPEN_ENDED -> [0,null].
 *   2. Explicit AI `maxValue` (with a sanity guard for score-like metrics).
 *   3. Inference from unit / label / metricKey (percentage, score, rating, quality, accuracy...).
 *   4. Default: open-ended [0,null].
 *
 * minValue defaults to 0 (values shouldn't go negative) unless the AI provided one.
 *
 * Downstream (WeeklyStatService, MetricValidator) treats a non-null maxValue as "bounded"
 * (averaged + clamped) and null as "open-ended" (summed) — so getting max right here fixes
 * aggregation everywhere.
 */
public final class MetricBounds {

    public static final double BOUNDED_MAX = 100.0;

    private MetricBounds() {}

    /** Resolved bounds for a metric. */
    public record Bounds(Double min, Double max) {}

    public static Bounds resolve(MetricDefinitionResponse dto) {
        double min = dto.getMinValue() != null ? dto.getMinValue() : 0.0;

        // 1. Explicit scale hint from the AI (preferred — easiest for the model to get right).
        String scale = dto.getScale() != null ? dto.getScale().trim().toUpperCase() : null;
        if ("BOUNDED_100".equals(scale)) {
            return new Bounds(min, BOUNDED_MAX);
        }
        if ("OPEN_ENDED".equals(scale)) {
            return new Bounds(min, null);
        }

        // 2. Explicit maxValue from the AI, with a sanity guard: if it looks like a 0-100 score
        //    (by signals) but the AI gave an absurd/implausible cap, normalize to 100.
        boolean looksBounded = looksLikeBounded(dto);
        if (dto.getMaxValue() != null) {
            double max = dto.getMaxValue();
            if (looksBounded && (max <= 0 || max > 1000)) {
                return new Bounds(min, BOUNDED_MAX);
            }
            return new Bounds(min, max);
        }

        // 3. No explicit max: infer. Score/rating/percentage -> 100; else open-ended.
        if (looksBounded) {
            return new Bounds(min, BOUNDED_MAX);
        }

        // 4. Open-ended cumulative quantity (minutes, reps, distance, rounds, count...).
        return new Bounds(min, null);
    }

    /** Convenience for callers that only need the max. */
    public static Double resolveMax(MetricDefinitionResponse dto) {
        return resolve(dto).max();
    }

    /** Convenience for callers that only need the min. */
    public static Double resolveMin(MetricDefinitionResponse dto) {
        return resolve(dto).min();
    }

    /**
     * Heuristic: does this metric represent a fixed 0-100 score / rating / quality / percentage?
     * Deliberately conservative — matches strong score cues only, and explicitly avoids
     * open-ended-but-similarly-worded metrics ("heart rate", "pace", generic "level"/"index").
     */
    private static boolean looksLikeBounded(MetricDefinitionResponse dto) {
        if (hasPercentOrScoreUnit(dto.getUnit())) {
            return true;
        }
        String text = ((safe(dto.getLabel()) + " " + safe(dto.getMetricKey()))).toLowerCase();

        // Strong "bounded score" cues.
        boolean scoreWord =
                text.contains("score")
                || text.contains("rating")       // NOT bare "rate" (heart rate / pace are open-ended)
                || text.contains("quality")
                || text.contains("accuracy")
                || text.contains("execution")
                || text.contains("proficiency")
                || text.contains("mastery")
                || text.contains("consistency")
                || text.contains("percent")
                || text.contains("/100")
                || text.contains("out of 100")
                || text.contains("(0-100)")
                || text.contains("0-100")
                || text.contains("0 to 100");

        return scoreWord;
    }

    private static boolean hasPercentOrScoreUnit(String unit) {
        if (unit == null) {
            return false;
        }
        String u = unit.trim().toLowerCase();
        return u.equals("%")
                || u.contains("percent")
                || u.equals("score")
                || u.equals("rating")
                || u.equals("/100")
                || u.contains("out of 100")
                || u.contains("0-100");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
