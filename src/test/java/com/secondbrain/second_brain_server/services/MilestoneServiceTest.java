package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateMilestoneRequest;
import com.secondbrain.second_brain_server.dto.response.MilestoneDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import com.secondbrain.second_brain_server.repository.MilestoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private DomainService domainService;

    @InjectMocks
    private MilestoneService milestoneService;

    private UUID userId;
    private UUID domainId;
    private Domain testDomain;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        domainId = UUID.randomUUID();
        testDomain = Domain.builder()
                .id(domainId)
                .build();
    }

    @Test
    void createMilestone_Success() {
        CreateMilestoneRequest request = CreateMilestoneRequest.builder()
                .domainId(domainId)
                .label("Bench 300 lbs")
                .metricKey("weight")
                .targetValue(300.0)
                .unit("lbs")
                .deadline(LocalDate.now().plusMonths(6))
                .build();

        Milestone savedMilestone = Milestone.builder()
                .id(UUID.randomUUID())
                .domain(testDomain)
                .label(request.getLabel())
                .metricKey(request.getMetricKey())
                .targetValue(request.getTargetValue())
                .status(MilestoneStatus.UPCOMING)
                .deadline(request.getDeadline())
                .build();

        when(domainService.assertOwnership(domainId, userId)).thenReturn(testDomain);
        when(milestoneRepository.save(any(Milestone.class))).thenReturn(savedMilestone);

        MilestoneDto result = milestoneService.createMilestone(userId, request);

        assertNotNull(result);
        assertEquals("Bench 300 lbs", result.getLabel());
        assertEquals(300.0, result.getTargetValue());
        assertEquals(MilestoneStatus.UPCOMING, result.getStatus());
        verify(milestoneRepository).save(any(Milestone.class));
    }

    @Test
    void createMilestone_ValidatesDeadlineIsDate() {
        CreateMilestoneRequest request = CreateMilestoneRequest.builder()
                .domainId(domainId)
                .label("Test Milestone")
                .metricKey("weight")
                .targetValue(100.0)
                .deadline(LocalDate.of(2026, 12, 31))
                .build();

        when(domainService.assertOwnership(domainId, userId)).thenReturn(testDomain);
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(i -> i.getArgument(0));

        MilestoneDto result = milestoneService.createMilestone(userId, request);

        assertNotNull(result.getDeadline());
        assertEquals(LocalDate.of(2026, 12, 31), result.getDeadline());
    }
}
