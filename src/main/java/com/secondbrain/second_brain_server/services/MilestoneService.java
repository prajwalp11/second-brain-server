package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateMilestoneRequest;
import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final PersonalRecordRepository prRepository;
    private final SessionMetricValueRepository sessionMetricValueRepository;

    public MilestoneDto createMilestone(UUID userId, CreateMilestoneRequest request) {
        // Placeholder
        return null;
    }

    public List<MilestoneDto> getMilestonesForDomain(UUID domainId) {
        // Placeholder
        return null;
    }

    public void updateProgress(UUID domainId) {
        // Placeholder
    }

    public Optional<MilestoneDto> getNextMilestone(UUID domainId) {
        // Placeholder
        return Optional.empty();
    }

    private Double resolveCurrentValue(Milestone milestone) {
        // Placeholder
        return null;
    }

    private boolean checkAndComplete(Milestone milestone) {
        // Placeholder
        return false;
    }
}
