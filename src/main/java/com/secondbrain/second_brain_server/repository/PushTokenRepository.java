package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {

    List<PushToken> findByUserIdAndEnabledTrue(UUID userId);

    Optional<PushToken> findByExpoToken(String expoToken);
}
