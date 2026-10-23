package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Most recent unused token for a user (we validate expiry/hash/attempts in the service). */
    Optional<PasswordResetToken> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);

    /** Count codes issued for a user since a cutoff — used for the send-email rate limit. */
    long countByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime since);

    /** Invalidate all outstanding (unused) codes for a user when a new one is issued. */
    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.userId = :userId AND t.used = false")
    void invalidateAllForUser(@Param("userId") UUID userId);

    /** Cleanup: remove used or expired tokens. Called by the hourly scheduler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken t WHERE t.used = true OR t.expiresAt < :now")
    int deleteUsedOrExpired(@Param("now") LocalDateTime now);
}
