package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainMetricDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricDefinitionServiceTest {

    @Mock
    private DomainMetricDefinitionRepository metricDefinitionRepository;

    @Mock
    private DomainService domainService;

    @InjectMocks
    private MetricDefinitionService metricDefinitionService;

    private UUID userId;
    private UUID domainId;
    private DomainMetricDefinition metric1;
    private DomainMetricDefinition metric2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        domainId = UUID.randomUUID();
        
        metric1 = DomainMetricDefinition.builder()
                .id(UUID.randomUUID())
                .metricKey("weight")
                .displayOrder(0)
                .build();
        
        metric2 = DomainMetricDefinition.builder()
                .id(UUID.randomUUID())
                .metricKey("reps")
                .displayOrder(1)
                .build();
    }

    @Test
    void reorderMetrics_Success() {
        List<UUID> newOrder = List.of(metric2.getId(), metric1.getId());
        
        when(metricDefinitionRepository.findById(metric1.getId())).thenReturn(Optional.of(metric1));
        when(metricDefinitionRepository.findById(metric2.getId())).thenReturn(Optional.of(metric2));
        when(metricDefinitionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        metricDefinitionService.reorderMetrics(domainId, newOrder, userId);

        verify(domainService).assertOwnership(domainId, userId);
        assertEquals(0, metric2.getDisplayOrder());
        assertEquals(1, metric1.getDisplayOrder());
        verify(metricDefinitionRepository, times(2)).save(any());
    }

    @Test
    void reorderMetrics_MetricNotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        List<UUID> newOrder = List.of(nonExistentId);
        
        when(metricDefinitionRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
            metricDefinitionService.reorderMetrics(domainId, newOrder, userId)
        );
    }
}
