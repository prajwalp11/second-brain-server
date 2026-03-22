package com.secondbrain.second_brain_server.service.ai;

import com.secondbrain.second_brain_server.dto.response.PersonalRecordDto;
import com.secondbrain.second_brain_server.dto.response.WeeklyStatDto;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.external.GeminiClient;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiInsightService {

    private final GeminiClient geminiClient;
    private final PromptBuilder promptBuilder;
    private final SessionLogRepository sessionLogRepository;
    private final PersonalRecordRepository prRepository;

    public void generateSessionInsight(SessionLog log, List<PersonalRecordDto> newPrs) {
        // Placeholder for AI session insight generation logic
    }

    public String generateWeeklyInsight(UUID userId, UUID domainId) {
        // Placeholder for AI weekly insight generation logic
        return null;
    }
}
