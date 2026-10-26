package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.Domain;
import com.secondbrain.second_brain_server.enums.DomainStatus;
import com.secondbrain.second_brain_server.enums.DomainType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    boolean existsByUserIdAndCustomName(UUID userId, String customName);

    @Query("SELECT d FROM Domain d WHERE d.status = 'ACTIVE' AND d.lastLogDate < :today")
    List<Domain> findAllActiveForStreakCheck(LocalDate today);

    List<Domain> findAllByStatus(DomainStatus status);

    /** Bulk-delete all domains for a user. DB-level ON DELETE CASCADE removes domain-scoped
     *  children (metric definitions, milestones, insights). Used by account deletion. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Domain d WHERE d.user.id = :userId")
    void deleteByUserId(UUID userId);
}
