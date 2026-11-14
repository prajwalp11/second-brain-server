package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.PushHistory;
import com.secondbrain.second_brain_server.enums.PushType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushHistoryRepository extends JpaRepository<PushHistory, UUID> {

    /** Most recent send of this type to the user — drives the per-type cooldown check. */
    Optional<PushHistory> findFirstByUserIdAndTypeOrderBySentAtDesc(UUID userId, PushType type);
}
