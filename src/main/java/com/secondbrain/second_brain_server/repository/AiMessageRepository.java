package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    List<AiMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
