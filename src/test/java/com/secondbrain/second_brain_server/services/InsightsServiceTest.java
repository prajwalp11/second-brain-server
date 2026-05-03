package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.InsightsResponse;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.service.ai.UserContextAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private SessionLogRepository sessionLogRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private UserContextAssembler userContextAssembler;

    @InjectMocks
    private InsightsService insightsService;

    private UUID userId;
    private Domain testDomain;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testDomain = Domain.builder()
                .id(UUID.randomUUID())
                .domainType(DomainType.GYM)
                .customName("My Gym")
                .currentStreak(7)
                .lastLogDate(LocalDate.now())
                .build();
    }

    @Test
    void getInsights_FallbackWhenAiFails() {
        when(domainRepository.findByUserId(userId)).thenReturn(List.of(testDomain));
        when(userContextAssembler.assemble(userId)).thenThrow(new RuntimeException("AI error"));

        InsightsResponse result = insightsService.getInsights(userId);

        assertNotNull(result);
        assertNotNull(result.getHighlights());
        assertNotNull(result.getPatterns());
        assertNotNull(result.getSuggestions());
        assertFalse(result.getHighlights().isEmpty());
    }

    @Test
    void getInsights_GeneratesStreakHighlight() {
        when(domainRepository.findByUserId(userId)).thenReturn(List.of(testDomain));
        when(userContextAssembler.assemble(userId)).thenThrow(new RuntimeException("Force fallback"));

        InsightsResponse result = insightsService.getInsights(userId);

        assertTrue(result.getHighlights().stream()
                .anyMatch(h -> h.contains("7-day streak")));
    }

    @Test
    void getInsights_GeneratesConsistencyPattern() {
        when(domainRepository.findByUserId(userId)).thenReturn(List.of(testDomain));
        when(userContextAssembler.assemble(userId)).thenThrow(new RuntimeException("Force fallback"));

        InsightsResponse result = insightsService.getInsights(userId);

        assertTrue(result.getPatterns().stream()
                .anyMatch(p -> p.contains("most consistent")));
    }

    @Test
    void getInsights_GeneratesSuggestionForInactiveDomain() {
        Domain inactiveDomain = Domain.builder()
                .id(UUID.randomUUID())
                .domainType(DomainType.RUNNING)
                .customName("Running")
                .lastLogDate(LocalDate.now().minusDays(5))
                .build();

        when(domainRepository.findByUserId(userId)).thenReturn(List.of(inactiveDomain));
        when(userContextAssembler.assemble(userId)).thenThrow(new RuntimeException("Force fallback"));

        InsightsResponse result = insightsService.getInsights(userId);

        assertTrue(result.getSuggestions().stream()
                .anyMatch(s -> s.contains("haven't logged") && s.contains("5 days")));
    }
}
