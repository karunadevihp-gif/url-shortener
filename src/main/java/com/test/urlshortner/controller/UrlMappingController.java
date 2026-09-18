package com.test.urlshortner.controller;

import com.test.urlshortner.entity.UrlMapping;
import com.test.urlshortner.service.UrlMappingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/v1"})
@Validated
public class UrlMappingController {

    private static final Logger log = LoggerFactory.getLogger(UrlMappingController.class);

    private final UrlMappingService urlMappingService;

    public UrlMappingController(UrlMappingService urlMappingService) {
        this.urlMappingService = urlMappingService;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortUrlResponse> createShortUrl(@Valid @RequestBody UrlShortenRequest request) {
        log.info("Received request to shorten URL: {}", request.longURL());
        UrlMapping saved = urlMappingService.saveShortUrl(request.longURL());
        String shortUrl = "http://localhost:8080/api/v1/" + saved.getShortCode();
        log.info("Short URL generated successfully for input {}: {}", request.longURL(), shortUrl);
        return ResponseEntity.ok(new ShortUrlResponse(shortUrl));
    }

    @GetMapping({"/{shortCode}"})
    public ResponseEntity<Void> redirectToOriginalUrl(@PathVariable String shortCode) {
        log.info("Redirect request received for shortCode: {}", shortCode);
        UrlMapping urlMapping = urlMappingService.resolveRedirect(shortCode);

        if (urlMapping == null || urlMapping.getOriginalUrl() == null || urlMapping.getOriginalUrl().isBlank()) {
            log.warn("No matching long URL found for shortCode: {}", shortCode);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "couldn't find a matching long URL to redirect");
        }

        // Validate URL before redirect to prevent open redirect attacks
        String originalUrl = urlMapping.getOriginalUrl();
        if (!isValidRedirectUrl(originalUrl)) {
            log.warn("Attempted redirect to invalid URL: {}", originalUrl);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid redirect URL");
        }

        log.info("Redirecting shortCode {} to original URL {}", shortCode, originalUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", originalUrl)
                .build();
    }

    private boolean isValidRedirectUrl(String url) {
        // Only allow https:// URLs to prevent javascript:, data:, file:// redirects
        return url != null && url.startsWith("https://");
    }

    @GetMapping("/analytics/{shortCode}")
    public ResponseEntity<UrlAnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        UrlMapping urlMapping = urlMappingService.findByShortCode(shortCode);
        return ResponseEntity.ok(new UrlAnalyticsResponse(
                urlMapping.getShortCode(),
                urlMapping.getOriginalUrl(),
                urlMapping.getClickCount(),
                urlMapping.getLastAccessedAt()));
    }
}
