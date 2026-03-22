package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final SessionLogRepository sessionLogRepository;
    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final PersonalRecordRepository prRepository;
    private final MilestoneRepository milestoneRepository;
    private final DomainRepository domainRepository;

    public String exportAsJson(UUID userId) {
        // Placeholder
        return null;
    }

    public byte[] exportAsCsv(UUID userId) {
        // Placeholder
        return null;
    }
}
