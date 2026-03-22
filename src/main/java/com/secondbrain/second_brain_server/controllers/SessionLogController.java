package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.CreateSessionLogRequest;
import com.secondbrain.second_brain_server.dto.response.SessionLogDto;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.SessionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/session-logs")
@RequiredArgsConstructor
public class SessionLogController {

    private final SessionLogService sessionLogService;

    @PostMapping
    public ResponseEntity<SessionLogDto> createLog(@RequestBody CreateSessionLogRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(sessionLogService.createLog(userId, request));
    }

    @GetMapping
    public ResponseEntity<Page<SessionLogDto>> getLogs(@RequestParam(required = false) UUID domainId,
                                                       Pageable pageable,
                                                       @CurrentUser UUID userId) {
        if (domainId != null) {
            return ResponseEntity.ok(sessionLogService.getLogsForDomain(domainId, userId, pageable));
        }
        return ResponseEntity.ok(sessionLogService.getLogsForUser(userId, pageable));
    }

    @GetMapping("/{logId}")
    public ResponseEntity<SessionLogDto> getLog(@PathVariable UUID logId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(sessionLogService.getLogById(logId, userId));
    }

    @DeleteMapping("/{logId}")
    public ResponseEntity<Void> deleteLog(@PathVariable UUID logId, @CurrentUser UUID userId) {
        sessionLogService.deleteLog(logId, userId);
        return ResponseEntity.ok().build();
    }
}
