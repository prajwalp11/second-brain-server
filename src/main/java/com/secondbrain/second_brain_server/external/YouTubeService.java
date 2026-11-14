package com.secondbrain.second_brain_server.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds relevant YouTube videos for tasks.
 * Strategy: Try YouTube Data API v3 first (returns direct video link).
 * If API fails or key is not configured, falls back to a YouTube search URL.
 */
@Service
@Slf4j
public class YouTubeService {

    private static final String YOUTUBE_API_URL = "https://www.googleapis.com/youtube/v3/search";
    private static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final String YOUTUBE_SEARCH_URL = "https://www.youtube.com/results?search_query=";

    @Value("${youtube.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public YouTubeService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Finds a YouTube video for the given task.
     * Returns a direct video URL if API key is configured and call succeeds.
     * Falls back to a search results URL otherwise.
     */
    public VideoResult findVideo(String videoTopic, String domainName) {
        // videoTopic is an AI-generated, YouTube-friendly search phrase describing the SKILL.
        // Append the domain for disambiguation only if the topic doesn't already contain it.
        String topic = videoTopic != null ? videoTopic.trim() : "";
        String query = topic;
        if (!domainName.isBlank() && !topic.toLowerCase().contains(domainName.toLowerCase())) {
            query = topic + " " + domainName;
        }

        // Try YouTube Data API if key is configured
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return searchViaApi(query);
            } catch (Exception e) {
                log.warn("YouTube API call failed, falling back to search URL: {}", e.getMessage());
            }
        }

        // Fallback: search URL (always works, no API key needed)
        return buildSearchFallback(query, topic.isBlank() ? domainName : topic);
    }

    private VideoResult searchViaApi(String query) {
        String url = YOUTUBE_API_URL
                + "?part=snippet"
                + "&q=" + encodeQuery(query)
                + "&type=video"
                + "&order=relevance"
                + "&relevanceLanguage=en"
                + "&videoEmbeddable=true"
                + "&safeSearch=moderate"
                + "&maxResults=5"
                + "&key=" + apiKey;

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root;
            try {
                root = objectMapper.readTree(response.getBody());
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse YouTube API response", e);
            }

            JsonNode items = root.path("items");
            if (items.isArray() && !items.isEmpty()) {
                // Results are already ordered by relevance; take the first valid video id.
                for (JsonNode item : items) {
                    String videoId = item.path("id").path("videoId").asText();
                    String title = item.path("snippet").path("title").asText();
                    if (!videoId.isEmpty()) {
                        log.debug("YouTube API found video: {} ({}) for query '{}'", title, videoId, query);
                        return new VideoResult(YOUTUBE_WATCH_URL + videoId, title);
                    }
                }
            }
        }

        throw new RuntimeException("No video found via YouTube API");
    }

    private VideoResult buildSearchFallback(String query, String taskTitle) {
        String searchUrl = YOUTUBE_SEARCH_URL + encodeQuery(query);
        return new VideoResult(searchUrl, taskTitle);
    }

    // ── Richer API used by VideoResolver (curriculum video resolution) ─────────────

    /** True when the YouTube Data API key is configured (resolver falls back to a search URL if not). */
    public boolean isApiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Public search-results URL for a query (used as the resolver's last-resort fallback). */
    public String searchResultsUrl(String query) {
        return YOUTUBE_SEARCH_URL + encodeQuery(query);
    }

    /**
     * Search for up to {@code max} candidate videos (ids + snippet). Returns empty on any failure or if
     * the API key is missing — the resolver then uses a search-URL fallback. Does NOT throw.
     */
    public List<Candidate> searchCandidates(String query, int max) {
        if (!isApiConfigured()) {
            return List.of();
        }
        try {
            String url = YOUTUBE_API_URL
                    + "?part=snippet"
                    + "&q=" + encodeQuery(query)
                    + "&type=video"
                    + "&order=relevance"
                    + "&relevanceLanguage=en"
                    + "&videoEmbeddable=true"
                    + "&safeSearch=moderate"
                    + "&maxResults=" + Math.max(1, Math.min(max, 25))
                    + "&key=" + apiKey;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }
            JsonNode items = objectMapper.readTree(response.getBody()).path("items");
            List<Candidate> out = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String videoId = item.path("id").path("videoId").asText("");
                    if (videoId.isEmpty()) continue;
                    JsonNode sn = item.path("snippet");
                    out.add(new Candidate(
                            videoId,
                            sn.path("title").asText(""),
                            sn.path("description").asText(""),
                            sn.path("channelId").asText(""),
                            sn.path("publishedAt").asText(""),
                            0L, 0L, 0L, 0        // stats filled by enrich()
                    ));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("YouTube searchCandidates failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /**
     * Enrich candidates with statistics (views/likes) + duration (seconds). Best-effort: on failure the
     * candidates are returned with zeroed stats (ranking still works, just weaker signal).
     */
    public List<Candidate> enrich(List<Candidate> candidates) {
        if (candidates.isEmpty() || !isApiConfigured()) {
            return candidates;
        }
        try {
            String ids = candidates.stream().map(Candidate::videoId).collect(Collectors.joining(","));
            String url = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=statistics,contentDetails"
                    + "&id=" + ids
                    + "&key=" + apiKey;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return candidates;
            }
            JsonNode items = objectMapper.readTree(response.getBody()).path("items");
            Map<String, JsonNode> byId = new HashMap<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    byId.put(item.path("id").asText(""), item);
                }
            }
            List<Candidate> out = new ArrayList<>();
            for (Candidate c : candidates) {
                JsonNode item = byId.get(c.videoId());
                if (item == null) { out.add(c); continue; }
                JsonNode stats = item.path("statistics");
                long views = stats.path("viewCount").asLong(0);
                long likes = stats.path("likeCount").asLong(0);
                long comments = stats.path("commentCount").asLong(0);
                int durationSec = parseIsoDurationSeconds(item.path("contentDetails").path("duration").asText(""));
                out.add(new Candidate(c.videoId(), c.title(), c.description(), c.channelId(),
                        c.publishedAt(), views, likes, comments, durationSec));
            }
            return out;
        } catch (Exception e) {
            log.warn("YouTube enrich failed: {}", e.getMessage());
            return candidates;
        }
    }

    /** ISO-8601 duration (PT#H#M#S) -> seconds; 0 if unpardable. */
    private int parseIsoDurationSeconds(String iso) {
        if (iso == null || iso.isBlank()) return 0;
        try {
            return (int) java.time.Duration.parse(iso).getSeconds();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String watchUrl(String videoId) {
        return YOUTUBE_WATCH_URL + videoId;
    }

    /** A candidate video with optional enriched stats. */
    public record Candidate(
            String videoId,
            String title,
            String description,
            String channelId,
            String publishedAt,
            long views,
            long likes,
            long comments,
            int durationSeconds
    ) {}

    private String encodeQuery(String query) {
        return query.replaceAll("[^a-zA-Z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "+");
    }

    /**
     * Result containing a YouTube URL (either direct video or search) and display title.
     */
    public record VideoResult(String url, String title) {}
}
