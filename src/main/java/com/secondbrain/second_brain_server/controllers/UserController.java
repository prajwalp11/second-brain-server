package com.secondbrain.second_brain_server.controllers;

import com.secondbrain.second_brain_server.dto.request.UpdateProfileRequest;
import com.secondbrain.second_brain_server.dto.response.UserDto;
import com.secondbrain.second_brain_server.security.CurrentUser;
import com.secondbrain.second_brain_server.services.AuthService;
import com.secondbrain.second_brain_server.services.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService; // Used for profile updates, though LLD shows it in UserService
    private final ExportService exportService;

    @GetMapping("/profile")
    public ResponseEntity<UserDto> getProfile(@CurrentUser UUID userId) {
        return ResponseEntity.ok(authService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserDto> updateProfile(@RequestBody UpdateProfileRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(authService.updateProfile(userId, request));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportData(@RequestParam String format, @CurrentUser UUID userId) {
        byte[] data;
        String filename;
        String contentType;

        if ("json".equalsIgnoreCase(format)) {
            data = exportService.exportAsJson(userId).getBytes();
            filename = "secondbrain_data.json";
            contentType = MediaType.APPLICATION_JSON_VALUE;
        } else if ("csv".equalsIgnoreCase(format)) {
            data = exportService.exportAsCsv(userId);
            filename = "secondbrain_data.csv";
            contentType = "text/csv";
        } else {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }
}