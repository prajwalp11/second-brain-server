package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, UUID> {
}
