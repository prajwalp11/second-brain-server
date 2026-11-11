package com.secondbrain.second_brain_server.external;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.secondbrain.second_brain_server.exception.UnauthorizedException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies Google ID tokens (from the mobile app's Google Sign-In) against Google's public
 * certificates. The underlying {@link GoogleIdTokenVerifier} fetches and caches Google's signing
 * keys automatically, honouring their rotation — so the certs never go stale and are not stored.
 * Validates signature, expiry, issuer, and that the audience is one of our configured client IDs.
 */
@Component
@Slf4j
public class GoogleTokenVerifier {

    /** Accepted audiences (OAuth client IDs) — comma-separated to allow Web/iOS/Android. */
    @Value("${google.sso.client-id:}")
    private String clientIds;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        List<String> audiences = Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();
    }

    /** Verifies the token and returns the user info, or throws if invalid/expired/wrong audience. */
    public GoogleUserInfo verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new UnauthorizedException("Invalid Google token");
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleUserInfo(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    Boolean.TRUE.equals(payload.getEmailVerified())
            );
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new UnauthorizedException("Invalid Google token");
        }
    }
}
