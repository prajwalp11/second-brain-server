package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.AiNudge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiNudgeRepository extends JpaRepository<AiNudge, UUID> {
    Optional<AiNudge> findFirstByUserIdAndIsReadFalseOrderByGeneratedAtDesc(UUID userId);
    List<AiNudge> findByUserIdAndIsReadFalse(UUID userId);
    boolean existsByUserIdAndDomainIdAndGeneratedAtAfter(UUID userId, UUID domainId, LocalDateTime since);
}
