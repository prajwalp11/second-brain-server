package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.SessionMetricValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionMetricValueRepository extends JpaRepository<SessionMetricValue, UUID> {
    List<SessionMetricValue> findBySessionLogId(UUID sessionLogId);
    List<SessionMetricValue> findBySessionLogIdIn(List<UUID> logIds);

    @Query("SELECT sl.logDate as date, smv.numericValue as value " +
            "FROM SessionMetricValue smv JOIN smv.sessionLog sl " +
            "WHERE sl.domain.id = :domainId AND smv.metricKey = :metricKey " +
            "AND sl.logDate BETWEEN :from AND :to ORDER BY sl.logDate ASC")
    List<TimeSeriesProjection> findMetricTimeSeries(UUID domainId, String metricKey, LocalDate from, LocalDate to);

    @Query("SELECT MAX(smv.numericValue) FROM SessionMetricValue smv JOIN smv.sessionLog sl WHERE sl.domain.id = :domainId AND smv.metricKey = :metricKey")
    Optional<Double> findMaxValueForMetric(UUID domainId, String metricKey);

    @Query("SELECT SUM(smv.numericValue) FROM SessionMetricValue smv JOIN smv.sessionLog sl " +
            "WHERE sl.domain.id = :domainId AND smv.metricKey = :metricKey " +
            "AND sl.logDate BETWEEN :from AND :to")
    Optional<Double> sumMetricForPeriod(UUID domainId, String metricKey, LocalDate from, LocalDate to);

    interface TimeSeriesProjection {
        LocalDate getDate();
        Double getValue();
    }
}
