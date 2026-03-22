package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.response.DashboardResponse;
import com.secondbrain.second_brain_server.dto.response.StreakDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.repository.AiNudgeRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DomainRepository domainRepository;
    private final TaskService taskService;
    private final WeeklyStatService weeklyStatService;
    private final AiNudgeRepository aiNudgeRepository;
    private final UserRepository userRepository;

    public DashboardResponse getDashboard(UUID userId, LocalDate date) {
        // Placeholder
        return null;
    }

    private String buildGreeting(String name, LocalDate date) {
        // Placeholder
        return null;
    }

    private Map<UUID, StreakDto> buildStreakMap(List<Domain> domains) {
        // Placeholder
        return null;
    }
}
