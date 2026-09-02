package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    @Query("SELECT u.id FROM User u")
    List<UUID> findAllIds();

    /**
     * Resets the daily AI usage counter for all users. Called by the midnight scheduler.
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.aiUsedToday = 0")
    void resetAllDailyAiUsage();
}
