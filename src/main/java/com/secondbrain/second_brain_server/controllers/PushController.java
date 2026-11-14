package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.DeletePushTokenRequest;
import com.secondbrain.second_brain_server.dto.request.PushPrefs;
import com.secondbrain.second_brain_server.dto.request.RegisterPushTokenRequest;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.service.PushService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Device registration + per-type push preferences. Thin: everything delegates to PushService. */
@RestController
@Slf4j
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    /** Register/upsert this device's Expo token (call on login / app start). */
    @PostMapping("/token")
    public ResponseEntity<Void> registerToken(@Valid @RequestBody RegisterPushTokenRequest request,
                                              @CurrentUser UUID userId) {
        pushService.registerToken(userId, request.getExpoToken(), request.getPlatform());
        return ResponseEntity.ok().build();
    }

    /** Unregister this device (call on logout). */
    @DeleteMapping("/token")
    public ResponseEntity<Void> deleteToken(@Valid @RequestBody DeletePushTokenRequest request,
                                            @CurrentUser UUID userId) {
        pushService.disableToken(userId, request.getExpoToken());
        return ResponseEntity.ok().build();
    }

    /** Update per-type notification toggles. */
    @PutMapping("/prefs")
    public ResponseEntity<Void> updatePrefs(@RequestBody PushPrefs prefs, @CurrentUser UUID userId) {
        pushService.updatePrefs(userId, prefs);
        return ResponseEntity.ok().build();
    }
}
