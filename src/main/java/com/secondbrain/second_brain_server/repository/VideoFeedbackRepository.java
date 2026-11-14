package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.VideoFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoFeedbackRepository extends JpaRepository<VideoFeedback, UUID> {
    Optional<VideoFeedback> findByQueryKeyAndUserId(String queryKey, UUID userId);
    long countByQueryKeyAndHelpful(String queryKey, boolean helpful);
}
