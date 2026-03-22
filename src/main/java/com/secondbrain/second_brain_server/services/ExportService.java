package com.secondbrain.second_brain_server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.entities.PersonalRecord;
import com.secondbrain.second_brain_server.entities.SessionLog;
import com.secondbrain.second_brain_server.entities.SessionMetricValue;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.SessionMetricValueRepository;
import com.secondbrain.second_brain_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final SessionLogRepository sessionLogRepository;
    private final SessionMetricValueRepository sessionMetricValueRepository;
    private final PersonalRecordRepository prRepository;
    private final MilestoneRepository milestoneRepository;
    private final DomainRepository domainRepository;
    private final UserRepository userRepository; // Needed to confirm user exists
    private final ObjectMapper objectMapper;

    public String exportAsJson(UUID userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Map<String, Object> userData = new HashMap<>();
        userData.put("domains", domainRepository.findByUserId(userId));
        userData.put("sessionLogs", sessionLogRepository.findByUserIdOrderByLogDateDesc(userId, null).getContent()); // Pass null for Pageable to get all
        userData.put("personalRecords", prRepository.findByUserId(userId));
        userData.put("milestones", milestoneRepository.findByDomainId(null)); // Need to fetch milestones for user's domains

        try {
            // Configure ObjectMapper for pretty printing and Java 8 Date/Time API
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            return objectMapper.writeValueAsString(userData);
        } catch (Exception e) {
            log.error("Error exporting data as JSON for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to export data as JSON", e);
        }
    }

    public byte[] exportAsCsv(UUID userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos, true, StandardCharsets.UTF_8);

        // Export Domains
        List<Domain> domains = domainRepository.findByUserId(userId);
        writer.println("Domains");
        writer.println("id,domainType,customName,skillLevel,status,planDescription,weeklySchedule,linkedResourceUrl,linkedResourceTitle,currentStreak,longestStreak,lastLogDate,createdAt,updatedAt");
        domains.forEach(d -> writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%s,%s,%s\n",
                d.getId(), d.getDomainType(), d.getCustomName(), d.getSkillLevel(), d.getStatus(),
                d.getPlanDescription() != null ? d.getPlanDescription().replace(",", ";") : "", // Handle commas in text fields
                d.getWeeklySchedule() != null ? d.getWeeklySchedule().replace(",", ";") : "",
                d.getLinkedResourceUrl(), d.getLinkedResourceTitle(), d.getCurrentStreak(), d.getLongestStreak(),
                d.getLastLogDate(), d.getCreatedAt(), d.getUpdatedAt()));
        writer.println();

        // Export Session Logs and Metrics
        List<SessionLog> sessionLogs = sessionLogRepository.findByUserIdOrderByLogDateDesc(userId, null).getContent();
        writer.println("SessionLogs");
        writer.println("id,domainId,sessionType,logDate,durationMinutes,feelScore,feelLabel,notes,linkedReferenceUrl,aiInsight,createdAt");
        sessionLogs.forEach(sl -> writer.printf("%s,%s,%s,%s,%d,%d,%s,%s,%s,%s,%s\n",
                sl.getId(), sl.getDomain().getId(), sl.getSessionType(), sl.getLogDate(), sl.getDurationMinutes(),
                sl.getFeelScore(), sl.getFeelLabel(),
                sl.getNotes() != null ? sl.getNotes().replace(",", ";") : "",
                sl.getLinkedReferenceUrl(),
                sl.getAiInsight() != null ? sl.getAiInsight().replace(",", ";") : "",
                sl.getCreatedAt()));
        writer.println();

        writer.println("SessionMetricValues");
        writer.println("id,sessionLogId,metricKey,numericValue,unit");
        sessionLogs.forEach(sl -> {
            List<SessionMetricValue> metrics = sessionMetricValueRepository.findBySessionLogId(sl.getId());
            metrics.forEach(smv -> writer.printf("%s,%s,%s,%f,%s\n",
                    smv.getId(), smv.getSessionLog().getId(), smv.getMetricKey(), smv.getNumericValue(), smv.getUnit()));
        });
        writer.println();

        // Export Personal Records
        List<PersonalRecord> prs = prRepository.findByUserId(userId);
        writer.println("PersonalRecords");
        writer.println("id,userId,domainId,sessionLogId,metricKey,value,unit,achievedAt,previousValue");
        prs.forEach(pr -> writer.printf("%s,%s,%s,%s,%s,%f,%s,%s,%f\n",
                pr.getId(), pr.getUser().getId(), pr.getDomain().getId(), pr.getSessionLog().getId(),
                pr.getMetricKey(), pr.getValue(), pr.getUnit(), pr.getAchievedAt(), pr.getPreviousValue()));
        writer.println();

        // Export Milestones
        List<Milestone> milestones = domains.stream()
                .flatMap(d -> milestoneRepository.findByDomainId(d.getId()).stream())
                .collect(Collectors.toList());
        writer.println("Milestones");
        writer.println("id,domainId,label,metricKey,targetValue,currentValue,unit,status,deadline,completedAt,aiGenerated,createdAt");
        milestones.forEach(m -> writer.printf("%s,%s,%s,%s,%f,%f,%s,%s,%s,%s,%b,%s\n",
                m.getId(), m.getDomain().getId(), m.getLabel(), m.getMetricKey(), m.getTargetValue(),
                m.getCurrentValue(), m.getUnit(), m.getStatus(), m.getDeadline(), m.getCompletedAt(),
                m.isAiGenerated(), m.getCreatedAt()));
        writer.println();

        writer.flush();
        return baos.toByteArray();
    }
}
