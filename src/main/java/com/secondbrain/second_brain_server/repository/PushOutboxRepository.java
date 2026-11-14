package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.PushOutbox;
import com.secondbrain.second_brain_server.enums.PushStatus;
import com.secondbrain.second_brain_server.enums.PushType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PushOutboxRepository extends JpaRepository<PushOutbox, UUID> {

    /** Oldest-first drain for the SENDER. */
    List<PushOutbox> findByStatusOrderByCreatedAtAsc(PushStatus status, Pageable pageable);

    /** One-pending-per-(user,type) guard for the PLANNER. */
    boolean existsByUserIdAndTypeAndStatus(UUID userId, PushType type, PushStatus status);
}
