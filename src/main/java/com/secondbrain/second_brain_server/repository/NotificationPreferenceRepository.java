package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserId(UUID userId);
    List<NotificationPreference> findAllByDailyReminderEnabledTrue();
    List<NotificationPreference> findAllByWeeklyReviewEnabledTrueAndWeeklyReviewDayOfWeek(Integer dow);
}
