package com.test.urlshortner.controller;

import java.time.LocalDateTime;

public record UrlAnalyticsResponse(
        String shortCode,
        String originalUrl,
        Long clickCount,
        LocalDateTime lastAccessedAt
) {
}
