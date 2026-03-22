package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.SessionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SessionLogRepository extends JpaRepository<SessionLog, UUID> {
    Page<SessionLog> findByUserIdOrderByLogDateDesc(UUID userId, Pageable pageable);
    Page<SessionLog> findByDomainIdOrderByLogDateDesc(UUID domainId, Pageable pageable);
    List<SessionLog> findByDomainIdAndLogDateBetween(UUID domainId, LocalDate from, LocalDate to);
    List<SessionLog> findTopNByDomainIdOrderByLogDateDesc(UUID domainId, Pageable pageable);
    @Query("SELECT sl.logDate FROM SessionLog sl WHERE sl.domain.id = :domainId")
    List<LocalDate> findLogDatesByDomainId(UUID domainId);
    Long countByDomainIdAndLogDateBetween(UUID domainId, LocalDate from, LocalDate to);
}
