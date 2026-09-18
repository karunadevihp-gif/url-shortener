package com.test.urlshortner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.urlshortner.controller.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IpRateLimitingFilter extends OncePerRequestFilter {

    private static final int LIMIT_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String SHORTEN_PATH = "/api/v1/shorten";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        return !(SHORTEN_PATH.equals(requestUri) && "POST".equalsIgnoreCase(method));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::createBucket);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(WINDOW.getSeconds()));
        response.getWriter().write(objectMapper.writeValueAsString(
                new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests: limit 10 requests per IP per minute")));
    }

    private Bucket createBucket(String ipAddress) {
        Bandwidth limit = Bandwidth.classic(LIMIT_PER_WINDOW, Refill.greedy(LIMIT_PER_WINDOW, WINDOW));
        return Bucket4j.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }

        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
