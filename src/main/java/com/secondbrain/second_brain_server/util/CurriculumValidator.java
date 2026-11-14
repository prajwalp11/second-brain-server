package com.secondbrain.second_brain_server.util;

import com.secondbrain.second_brain_server.dto.response.GeneratedCurriculumStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic server-side enforcement of the AI curriculum contract (design §4). The AI supplies
 * content; the SERVER guarantees the structure holds for every domain:
 *   - bounded size, contiguous 0..n-1 ordering,
 *   - usable title + specific videoQuery per step (derive a query from the title if the AI's is weak),
 *   - trimmed/capped phase.
 * Returns a normalized list, or an empty list if the curriculum is unusable (caller then falls back
 * to flat AI task generation — never blocks domain creation).
 */
public final class CurriculumValidator {

    private static final int MIN_STEPS = 3;   // below this, treat as unusable
    private static final int MAX_STEPS = 30;  // trim beyond this
    private static final int MIN_QUERY_WORDS = 3;

    private CurriculumValidator() {}

    public static List<GeneratedCurriculumStep> normalize(List<GeneratedCurriculumStep> raw, String domainName) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<GeneratedCurriculumStep> cleaned = new ArrayList<>();
        int order = 0;
        for (GeneratedCurriculumStep s : raw) {
            if (s == null) continue;
            String title = trimOrNull(s.getTitle());
            if (title == null) continue; // a step with no title is useless

            String videoQuery = ensureSpecificQuery(s.getVideoQuery(), title, domainName);

            cleaned.add(GeneratedCurriculumStep.builder()
                    .orderIndex(order++)                       // contiguous ordering, server-assigned
                    .phase(capPhase(s.getPhase()))
                    .title(cap(title, 255))
                    .description(trimOrNull(s.getDescription()))
                    .estMinutes(sanitizeMinutes(s.getEstMinutes()))
                    .videoQuery(videoQuery)
                    .build());

            if (cleaned.size() >= MAX_STEPS) break;
        }

        if (cleaned.size() < MIN_STEPS) {
            return List.of();
        }
        return cleaned;
    }

    public static boolean isUsable(List<GeneratedCurriculumStep> normalized) {
        return normalized != null && normalized.size() >= MIN_STEPS;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A specific query (>= MIN_QUERY_WORDS, not just the domain, not vague) or one derived from the title. */
    private static String ensureSpecificQuery(String query, String title, String domainName) {
        String q = trimOrNull(query);
        if (q != null && isSpecific(q, domainName)) {
            return q;
        }
        // Derive from the title (strip trailing punctuation); append domain for disambiguation.
        String base = title.replaceAll("[^a-zA-Z0-9\\s]", " ").trim().replaceAll("\\s+", " ");
        String derived = (domainName != null && !domainName.isBlank()
                && !base.toLowerCase().contains(domainName.toLowerCase()))
                ? base + " " + domainName
                : base;
        return cap(derived, 200);
    }

    private static boolean isSpecific(String q, String domainName) {
        String norm = q.toLowerCase().trim();
        if (norm.split("\\s+").length < MIN_QUERY_WORDS) return false;
        if (domainName != null && norm.equals(domainName.toLowerCase().trim())) return false;
        // reject purely vague queries
        String stripped = norm.replace("practice", "").replace("improve", "").replace("tutorial", "").trim();
        return !stripped.isEmpty();
    }

    private static Integer sanitizeMinutes(Integer m) {
        if (m == null || m <= 0) return null;
        return Math.min(m, 600); // cap absurd estimates at 10h
    }

    private static String capPhase(String phase) {
        String p = trimOrNull(phase);
        return p == null ? null : cap(p, 100);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
