package com.secondbrain.second_brain_server.services;

import com.secondbrain.second_brain_server.dto.request.CreateDomainRequest;
import com.secondbrain.second_brain_server.dto.response.DomainDto;
import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.entities.User;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.DomainType;
import com.secondbrain.second_brain_server.enums.SkillLevel;
import com.secondbrain.second_brain_server.exception.DomainAlreadyExistsException;
import com.secondbrain.second_brain_server.exception.ResourceNotFoundException;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.service.ai.AiSystemGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainServiceTest {

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private AiSystemGeneratorService aiSystemGeneratorService;

    @InjectMocks
    private DomainService domainService;

    private UUID userId;
    private Domain testDomain;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testDomain = Domain.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(userId).build())
                .domainType(DomainType.GYM)
                .skillLevel(SkillLevel.INTERMEDIATE)
                .status(DomainStatus.ACTIVE)
                .currentStreak(0)
                .longestStreak(0)
                .build();
    }

    @Test
    void createDomain_Success() {
        CreateDomainRequest request = CreateDomainRequest.builder()
                .domainType(DomainType.GYM)
                .skillLevel(SkillLevel.INTERMEDIATE)
                .build();

        when(domainRepository.existsByUserIdAndDomainType(userId, DomainType.GYM)).thenReturn(false);
        when(domainRepository.save(any(Domain.class))).thenReturn(testDomain);

        DomainDto result = domainService.createDomain(userId, request);

        assertNotNull(result);
        assertEquals(DomainType.GYM, result.getDomainType());
        verify(domainRepository).save(any(Domain.class));
    }

    @Test
    void createDomain_AlreadyExists_ThrowsException() {
        CreateDomainRequest request = CreateDomainRequest.builder()
                .domainType(DomainType.GYM)
                .skillLevel(SkillLevel.INTERMEDIATE)
                .build();

        when(domainRepository.existsByUserIdAndDomainType(userId, DomainType.GYM)).thenReturn(true);

        assertThrows(DomainAlreadyExistsException.class, () -> 
            domainService.createDomain(userId, request)
        );
    }

    @Test
    void assertOwnership_Success() {
        UUID domainId = testDomain.getId();
        when(domainRepository.findById(domainId)).thenReturn(Optional.of(testDomain));

        Domain result = domainService.assertOwnership(domainId, userId);

        assertNotNull(result);
        assertEquals(domainId, result.getId());
    }

    @Test
    void assertOwnership_NotFound_ThrowsException() {
        UUID domainId = UUID.randomUUID();
        when(domainRepository.findById(domainId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
            domainService.assertOwnership(domainId, userId)
        );
    }
}
