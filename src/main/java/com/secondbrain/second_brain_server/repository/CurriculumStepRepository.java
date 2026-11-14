package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.CurriculumStep;
import com.secondbrain.second_brain_server.enums.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CurriculumStepRepository extends JpaRepository<CurriculumStep, UUID> {

    List<CurriculumStep> findByDomainIdOrderByOrderIndexAsc(UUID domainId);

    Optional<CurriculumStep> findFirstByDomainIdAndStatus(UUID domainId, StepStatus status);

    /** Next LOCKED step after a given order index (to unlock on advance). */
    Optional<CurriculumStep> findFirstByDomainIdAndStatusAndOrderIndexGreaterThanOrderByOrderIndexAsc(
            UUID domainId, StepStatus status, int orderIndex);

    long countByDomainId(UUID domainId);

    long countByDomainIdAndStatus(UUID domainId, StepStatus status);

    @Modifying
    @Transactional
    void deleteByDomainId(UUID domainId);
}
