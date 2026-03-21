package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.DomainType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DomainRepository extends JpaRepository<Domain, UUID> {
    List<Domain> findByUserId(UUID userId);
    Optional<Domain> findByUserIdAndDomainType(UUID userId, DomainType type);
    List<Domain> findByUserIdAndStatus(UUID userId, DomainStatus status);
    boolean existsByUserIdAndDomainType(UUID userId, DomainType type);

    @Query("SELECT d FROM Domain d WHERE d.status = 'ACTIVE' AND d.lastLogDate < :today")
    List<Domain> findAllActiveForStreakCheck(LocalDate today);
}
