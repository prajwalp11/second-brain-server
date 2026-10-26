package com.secondbrain.second_brain_server.service;

import com.secondbrain.second_brain_server.repository.AiConversationRepository;
import com.secondbrain.second_brain_server.repository.AiNudgeRepository;
import com.secondbrain.second_brain_server.repository.DomainRepository;
import com.secondbrain.second_brain_server.repository.PersonalRecordRepository;
import com.secondbrain.second_brain_server.repository.SessionLogRepository;
import com.secondbrain.second_brain_server.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Wipes all data owned by a user. Kept separate from {@link AuthService} so account-lifecycle
 * data removal has a single, focused home (the many repositories it needs would otherwise bloat
 * AuthService).
 *
 * <p>Deletes in FK-safe order; DB-level {@code ON DELETE CASCADE} removes domain-scoped children
 * (metric definitions, milestones, insights), session metric values, and AI messages. The caller
 * is responsible for the user row itself (it is soft-deleted, not removed).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountDeletionService {

    private final DomainRepository domainRepository;
    private final SessionLogRepository sessionLogRepository;
    private final TaskRepository taskRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final AiNudgeRepository aiNudgeRepository;
    private final AiConversationRepository aiConversationRepository;

    @Transactional
    public void wipeUserData(UUID userId) {
        personalRecordRepository.deleteByUserId(userId);
        taskRepository.deleteByUserId(userId);
        sessionLogRepository.deleteByUserId(userId);
        aiNudgeRepository.deleteByUserId(userId);
        aiConversationRepository.deleteByUserId(userId);
        domainRepository.deleteByUserId(userId);
        log.info("Wiped all owned data for user {}", userId);
    }
}
