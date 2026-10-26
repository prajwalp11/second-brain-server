package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.PersonalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, UUID> {
    List<PersonalRecord> findByDomainId(UUID domainId);
    Optional<PersonalRecord> findByDomainIdAndMetricKey(UUID domainId, String metricKey);
    List<PersonalRecord> findByUserId(UUID userId);
    Page<PersonalRecord> findByUserIdOrderByAchievedAtDesc(UUID userId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM PersonalRecord pr WHERE pr.user.id = :userId")
    void deleteByUserId(UUID userId);
}
