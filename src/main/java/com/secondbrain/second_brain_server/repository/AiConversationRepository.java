package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<AiConversation> findByIdAndUserId(UUID id, UUID userId);
}
