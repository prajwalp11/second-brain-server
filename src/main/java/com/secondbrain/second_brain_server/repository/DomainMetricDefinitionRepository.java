package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DomainMetricDefinitionRepository extends JpaRepository<DomainMetricDefinition, UUID> {
    List<DomainMetricDefinition> findByDomainIdOrderByDisplayOrder(UUID domainId);
    Optional<DomainMetricDefinition> findByDomainIdAndMetricKey(UUID domainId, String key);

    @Query("SELECT d FROM DomainMetricDefinition d WHERE d.domain.id = :domainId AND d.isPR = true")
    List<DomainMetricDefinition> findPrMetricsByDomainId(UUID domainId);

    void deleteByDomainId(UUID domainId);
}
