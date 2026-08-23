package com.secondbrain.second_brain_server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter for authentication endpoints.
 * Limits requests per IP to prevent brute-force attacks.
 *
 * Config: 10 attempts per IP per 15-minute window.
 * After limit hit, returns 429 Too Many Requests.
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS = 15 * 60 * 1000; // 15 minutes

    // IP → (attempt count, window start time)
    private final Map<String, RateWindow> ipAttempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = getClientIp(request);
        String path = request.getRequestURI();

        // Only rate-limit auth endpoints
        if (!path.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only rate-limit POST (login/register), not OPTIONS
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        RateWindow window = ipAttempts.compute(ip, (key, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                // New window
                return new RateWindow(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > MAX_ATTEMPTS) {
            log.warn("Rate limit exceeded for IP: {} on {}", ip, path);
            response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"message\":\"Too many attempts. Please try again in 15 minutes.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Check common proxy headers
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private static class RateWindow {
        final long windowStart;
        final AtomicInteger count;

        RateWindow(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
