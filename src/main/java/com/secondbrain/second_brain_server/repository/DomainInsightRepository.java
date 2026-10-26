package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.DomainInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DomainInsightRepository extends JpaRepository<DomainInsight, UUID> {
    Optional<DomainInsight> findByDomainId(UUID domainId);
}
