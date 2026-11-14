package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.entities.VideoCache;
import com.secondbrain.second_brain_server.enums.Confidence;
import com.secondbrain.second_brain_server.enums.VideoSource;
import com.secondbrain.second_brain_server.external.YouTubeService;
import com.secondbrain.second_brain_server.external.YouTubeService.Candidate;
import com.secondbrain.second_brain_server.repository.VideoCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves a REAL, best-available YouTube video for a precise query — domain-agnostic and
 * long-tail-safe. Pipeline: cache (skip demoted) → search → enrich → soft sanity floors →
 * relative composite ranking → pick, else search-URL fallback → cache. The AI never supplies URLs;
 * every URL here comes from the YouTube API (or a plain search link). Never throws.
 *
 * See docs/curriculum-video-design.md §5.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VideoResolver {

    private static final int SEARCH_SIZE = 10;
    private static final int MIN_DURATION_SEC = 60;        // drop Shorts-spam
    private static final int MAX_DURATION_SEC = 90 * 60;   // drop multi-hour streams

    private final YouTubeService youTubeService;
    private final VideoCacheRepository videoCacheRepository;

    /** The resolved video a curriculum step / task points at. */
    public record ResolvedVideo(String queryKey, String url, String title,
                                VideoSource source, Confidence confidence) {}

    /**
     * Resolve (and cache) a video for the given query. If a non-demoted cache entry exists it is
     * reused (no API call). Otherwise searches, ranks, caches, and returns the pick — or a
     * search-link fallback if nothing passes sanity / the API is unavailable.
     */
    public ResolvedVideo resolve(String videoQuery, String domainName) {
        String query = buildQuery(videoQuery, domainName);
        String key = normalizeKey(query);

        Optional<VideoCache> cached = videoCacheRepository.findByQueryKey(key);
        if (cached.isPresent() && !cached.get().isDemoted()) {
            VideoCache c = cached.get();
            return new ResolvedVideo(key, c.getVideoUrl(), c.getVideoTitle(), c.getSource(),
                    c.getEffectiveConfidence() != null ? c.getEffectiveConfidence() : c.getConfidence());
        }

        ResolvedVideo resolved = search(query, key);
        upsertCache(key, resolved);
        return resolved;
    }

    /** The normalized cache key for a (query, domain) pair — exposed so callers store the same key. */
    public String keyFor(String videoQuery, String domainName) {
        return normalizeKey(buildQuery(videoQuery, domainName));
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ResolvedVideo search(String query, String key) {
        if (!youTubeService.isApiConfigured()) {
            return fallback(query, key);
        }

        List<Candidate> candidates = youTubeService.enrich(youTubeService.searchCandidates(query, SEARCH_SIZE));
        Set<String> queryTerms = terms(query);

        List<Candidate> survivors = candidates.stream()
                .filter(c -> passesSanity(c, queryTerms))
                .collect(Collectors.toList());

        if (survivors.isEmpty()) {
            return fallback(query, key);
        }

        Candidate best = survivors.stream()
                .max(Comparator.comparingDouble(c -> score(c, queryTerms)))
                .orElseThrow();

        Confidence confidence = confidenceFor(best, survivors.size(), queryTerms);
        return new ResolvedVideo(key, YouTubeService.watchUrl(best.videoId()), best.title(),
                VideoSource.DIRECT, confidence);
    }

    private ResolvedVideo fallback(String query, String key) {
        return new ResolvedVideo(key, youTubeService.searchResultsUrl(query), "Search: " + query,
                VideoSource.SEARCH_FALLBACK, Confidence.LOW);
    }

    /** Soft sanity floors — reject junk, NOT "not popular enough" (keeps long-tail domains alive). */
    private boolean passesSanity(Candidate c, Set<String> queryTerms) {
        if (c.durationSeconds() > 0 && (c.durationSeconds() < MIN_DURATION_SEC || c.durationSeconds() > MAX_DURATION_SEC)) {
            return false;
        }
        // On-topic: at least one meaningful query term appears in the title/description.
        return onTopicOverlap(c, queryTerms) > 0;
    }

    /** Relative composite score — ranks WITHIN whatever exists for this niche (log-scaled signals). */
    private double score(Candidate c, Set<String> queryTerms) {
        double views = Math.log10(Math.max(1, c.views()));                       // popularity
        double likeRatio = c.views() > 0 ? (double) c.likes() / c.views() : 0.0; // reception
        double onTopic = onTopicOverlap(c, queryTerms);                          // relevance
        return 1.0 * views + 20.0 * likeRatio + 2.0 * onTopic;
    }

    private Confidence confidenceFor(Candidate best, int survivorCount, Set<String> queryTerms) {
        boolean strongSignal = best.views() >= 50_000 && best.likes() > 0;
        boolean clearlyOnTopic = onTopicOverlap(best, queryTerms) >= Math.min(2, queryTerms.size());
        if (strongSignal && clearlyOnTopic) return Confidence.HIGH;
        if (clearlyOnTopic || survivorCount >= 3) return Confidence.MEDIUM;
        return Confidence.LOW;
    }

    private void upsertCache(String key, ResolvedVideo r) {
        VideoCache cache = videoCacheRepository.findByQueryKey(key).orElseGet(VideoCache::new);
        cache.setQueryKey(key);
        cache.setVideoUrl(r.url());
        cache.setVideoTitle(r.title());
        cache.setSource(r.source());
        cache.setConfidence(r.confidence());
        // effectiveConfidence starts at the stats confidence; feedback shifts it later.
        if (cache.getEffectiveConfidence() == null) {
            cache.setEffectiveConfidence(r.confidence());
        }
        cache.setDemoted(false); // a fresh resolution replaces any demoted pick
        cache.setResolvedAt(LocalDateTime.now());
        videoCacheRepository.save(cache);
    }

    // ── text helpers ────────────────────────────────────────────────────────────

    private String buildQuery(String videoQuery, String domainName) {
        String topic = videoQuery != null ? videoQuery.trim() : "";
        if (domainName != null && !domainName.isBlank()
                && !topic.toLowerCase().contains(domainName.toLowerCase())) {
            topic = (topic + " " + domainName).trim();
        }
        return topic;
    }

    /** Deterministic key: lowercase, strip punctuation, collapse whitespace. NO word reordering. */
    private String normalizeKey(String query) {
        return query == null ? "" : query.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private Set<String> terms(String query) {
        return java.util.Arrays.stream(normalizeKey(query).split(" "))
                .filter(t -> t.length() >= 3)       // ignore tiny stopword-ish tokens
                .collect(Collectors.toSet());
    }

    private long onTopicOverlap(Candidate c, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) return 0;
        String hay = (c.title() + " " + c.description()).toLowerCase();
        return queryTerms.stream().filter(hay::contains).count();
    }
}
