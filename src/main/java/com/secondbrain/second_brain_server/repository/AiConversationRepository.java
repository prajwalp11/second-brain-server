package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    Optional<AiConversation> findByIdAndUserId(UUID id, UUID userId);

    /** Deletes all of a user's conversations. DB-level ON DELETE CASCADE removes their ai_messages. */
    @Modifying
    @Transactional
    @Query("DELETE FROM AiConversation c WHERE c.user.id = :userId")
    void deleteByUserId(UUID userId);
}
