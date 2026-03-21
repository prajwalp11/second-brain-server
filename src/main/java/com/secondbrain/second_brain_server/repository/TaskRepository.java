package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.Task;
import com.secondbrain.second_brain_server.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByUserIdAndStatusIn(UUID userId, List<TaskStatus> statuses);
    List<Task> findByUserIdAndDueDateAndStatusIn(UUID userId, LocalDate date, List<TaskStatus> statuses);
    List<Task> findByDomainId(UUID domainId);
    Long countByUserIdAndStatusAndDueDateBetween(UUID userId, TaskStatus status, LocalDate from, LocalDate to);
}
