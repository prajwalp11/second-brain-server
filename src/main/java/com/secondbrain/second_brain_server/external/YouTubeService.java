package com.secondbrain.second_brain_server.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
