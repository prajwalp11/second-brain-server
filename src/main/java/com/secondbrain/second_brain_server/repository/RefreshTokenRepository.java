package com.secondbrain.second_brain_server.repository;

import com.secondbrain.second_brain_server.entities.RefreshToken;
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
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Looks up a token by its hash (validity — revoked/expiry — is checked in the service). */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke all of a user's refresh tokens (logout / account deletion). */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.userId = :userId AND t.revoked = false")
    void revokeAllForUser(@Param("userId") UUID userId);

    /** Cleanup: remove revoked or expired tokens. Called by the hourly scheduler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken t WHERE t.revoked = true OR t.expiresAt < :now")
    int deleteRevokedOrExpired(@Param("now") LocalDateTime now);
}
