package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.AiMessage;
import com.secondbrain.second_brain_server.enums.MessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    List<AiMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    /**
     * Count user messages sent today (for rate limiting).
     * Joins through ai_conversations to get the user_id.
     */
    @Query("SELECT COUNT(m) FROM AiMessage m " +
            "JOIN m.conversation c " +
            "WHERE c.user.id = :userId " +
            "AND m.role = :role " +
            "AND m.createdAt >= :since")
    long countByUserIdAndRoleAndCreatedAtAfter(UUID userId, MessageRole role, LocalDateTime since);
}
