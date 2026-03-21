package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.Milestone;
import com.secondbrain.second_brain_server.enums.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findByDomainId(UUID domainId);
    List<Milestone> findByDomainIdAndStatus(UUID domainId, MilestoneStatus status);
    Optional<Milestone> findFirstByDomainIdAndStatusOrderByDeadlineAsc(UUID domainId, MilestoneStatus status);
    List<Milestone> findByDomainIdAndMetricKey(UUID domainId, String metricKey);
}
