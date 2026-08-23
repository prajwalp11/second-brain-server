package com.secondbrain.second_brain_server.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.second_brain_server.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class GeminiClient {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.model}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("GeminiClient initialized with model: {}, key starts with: {}", model, apiKey != null ? apiKey.substring(0, Math.min(10, apiKey.length())) : "NULL");
    }

    public String complete(String systemPrompt, List<GeminiMessage> messages) {
        return retryOnRateLimit(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            List<GeminiMessage> fullMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                fullMessages.add(new GeminiMessage("user", List.of(Map.of("text", systemPrompt))));
                fullMessages.add(new GeminiMessage("model", List.of(Map.of("text", "Okay, I understand.")))); // Acknowledge system prompt
            }
            fullMessages.addAll(messages);

            Map<String, Object> requestBody = buildRequest(fullMessages);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = String.format("%s%s:generateContent?key=%s", baseUrl, model, apiKey);
            log.debug("Sending request to Gemini API: {}", url);
            log.debug("Request body: {}", requestBody);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.debug("Received response from Gemini API: {}", response.getBody());

            return parseText(response);
        });
    }

    public String completeWithJson(String systemPrompt, List<GeminiMessage> messages) {
        return retryOnRateLimit(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            List<GeminiMessage> fullMessages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                fullMessages.add(new GeminiMessage("user", List.of(Map.of("text", systemPrompt))));
                fullMessages.add(new GeminiMessage("model", List.of(Map.of("text", "Okay, I understand. I will respond in JSON format."))));
            }
            fullMessages.addAll(messages);

            Map<String, Object> requestBody = buildRequest(fullMessages);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = String.format("%s%s:generateContent?key=%s", baseUrl, model, apiKey);
            log.debug("Sending request to Gemini API (JSON): {}", url);
            log.debug("Request body (JSON): {}", requestBody);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.debug("Received response from Gemini API (JSON): {}", response.getBody());

            return parseJson(response);
        });
    }

    private Map<String, Object> buildRequest(List<GeminiMessage> messages) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (GeminiMessage message : messages) {
            contents.add(Map.of(
                    "role", message.getRole(),
                    "parts", message.getParts()
            ));
        }
        return Map.of("contents", contents);
    }

    private String parseText(ResponseEntity<String> response) {
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (!textNode.isMissingNode()) {
                    return textNode.asText();
                }
            } catch (Exception e) {
                log.error("Error parsing Gemini text response: {}", response.getBody(), e);
                throw new AiServiceException("Failed to parse Gemini text response", e);
            }
        }
        log.error("Gemini API text response error: Status={}, Body={}", response.getStatusCode(), response.getBody());
        throw new AiServiceException("Gemini API text call failed with status: " + response.getStatusCode());
    }

    private String parseJson(ResponseEntity<String> response) {
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (!textNode.isMissingNode()) {
                    String jsonString = textNode.asText();

                    // Strip markdown code fences if present (```json ... ``` or ``` ... ```)
                    jsonString = stripMarkdownFences(jsonString);

                    // Attempt to parse the extracted text as JSON to ensure it's valid
                    objectMapper.readTree(jsonString);
                    return jsonString;
                }
            } catch (Exception e) {
                log.error("Error parsing Gemini JSON response: {}", response.getBody(), e);
                throw new AiServiceException("Failed to parse Gemini JSON response", e);
            }
        }
        log.error("Gemini API JSON response error: Status={}, Body={}", response.getStatusCode(), response.getBody());
        throw new AiServiceException("Gemini API JSON call failed with status: " + response.getStatusCode());
    }

    private String stripMarkdownFences(String text) {
        if (text == null) return null;
        String stripped = text.trim();
        if (stripped.startsWith("```")) {
            // Remove opening fence (```json or ``` with optional language tag)
            stripped = stripped.replaceFirst("^```[a-zA-Z]*\\s*\\n?", "");
            // Remove closing fence
            stripped = stripped.replaceFirst("\\n?\\s*```\\s*$", "");
        }
        return stripped.trim();
    }

    private String retryOnRateLimit(Supplier<String> supplier) {
        int maxRetries = 3;
        long delayMs = 2000; // Start at 2 seconds

        for (int i = 0; i < maxRetries; i++) {
            try {
                return supplier.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("Gemini API rate limit (429). Attempt {}/{}. Retrying in {}ms...", i + 1, maxRetries, delayMs);
                sleepOrThrow(delayMs);
                delayMs *= 2;
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                log.warn("Gemini API unavailable (503). Attempt {}/{}. Retrying in {}ms...", i + 1, maxRetries, delayMs);
                sleepOrThrow(delayMs);
                delayMs *= 2;
            } catch (HttpServerErrorException e) {
                log.error("Gemini API server error: Status={}, Body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new AiServiceException("Gemini API server error (" + e.getStatusCode() + "). Please try again later.", e);
            } catch (HttpClientErrorException e) {
                log.error("Gemini API client error: Status={}, Body={}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new AiServiceException("Gemini API request failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
            } catch (Exception e) {
                log.error("Unexpected error during Gemini API call", e);
                throw new AiServiceException("AI service temporarily unavailable. Please try again.", e);
            }
        }
        throw new AiServiceException("AI service is experiencing high demand. Please try again in a few minutes.");
    }

    private void sleepOrThrow(long delayMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("AI request was interrupted", ie);
        }
    }
}
