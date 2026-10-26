package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.OtpToken;
import com.secondbrain.second_brain_server.enums.OtpPurpose;
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
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    /** Most recent unused token for a user + purpose (expiry/hash/attempts validated in the service). */
    Optional<OtpToken> findFirstByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);

    /** Count codes issued for a user + purpose since a cutoff — used for the send-email rate limit. */
    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, OtpPurpose purpose, LocalDateTime since);

    /** Invalidate all outstanding (unused) codes for a user + purpose when a new one is issued. */
    @Modifying
    @Transactional
    @Query("UPDATE OtpToken t SET t.used = true WHERE t.userId = :userId AND t.purpose = :purpose AND t.used = false")
    void invalidateAllForUser(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose);

    /** Cleanup: remove used or expired tokens (all purposes). Called by the hourly scheduler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM OtpToken t WHERE t.used = true OR t.expiresAt < :now")
    int deleteUsedOrExpired(@Param("now") LocalDateTime now);
}
