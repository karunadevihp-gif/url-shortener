package com.test.urlshortner.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.urlshortner.controller.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IpRateLimitingFilter extends OncePerRequestFilter {

    private static final int LIMIT_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String SHORTEN_PATH = "/api/v1/shorten";
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    private final Map<String, Bucket> fallbackBuckets = new ConcurrentHashMap<>();
    private final ProxyManager<byte[]> proxyManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IpRateLimitingFilter(@Value("${spring.redis.host:localhost}") String redisHost,
            @Value("${spring.redis.port:6379}") int redisPort) {
        this.proxyManager = createProxyManager(redisHost, redisPort);
    }

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
        Bucket bucket = getBucket(clientIp);

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

    private Bucket getBucket(String ipAddress) {
        if (proxyManager != null) {
            return proxyManager.builder()
                    .build((RATE_LIMIT_PREFIX + ipAddress).getBytes(StandardCharsets.UTF_8), this::bucketConfiguration);
        }
        return fallbackBuckets.computeIfAbsent(ipAddress, this::createBucket);
    }

    private BucketConfiguration bucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(LIMIT_PER_WINDOW, Refill.greedy(LIMIT_PER_WINDOW, WINDOW)))
                .build();
    }

    private Bucket createBucket(String ipAddress) {
        return Bucket.builder().addLimit(Bandwidth.classic(LIMIT_PER_WINDOW, Refill.greedy(LIMIT_PER_WINDOW, WINDOW))).build();
    }

    private ProxyManager<byte[]> createProxyManager(String redisHost, int redisPort) {
        try {
            RedisClient redisClient = RedisClient.create(RedisURI.Builder.redis(redisHost).withPort(redisPort).build());
            return LettuceBasedProxyManager.builderFor(redisClient).build();
        } catch (Exception ex) {
            return null;
        }
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
