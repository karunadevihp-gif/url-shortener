package com.test.urlshortner.service;

import com.test.urlshortner.dao.UrlMappingRepository;
import com.test.urlshortner.entity.UrlMapping;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UrlMappingService {

    private static final Logger log = LoggerFactory.getLogger(UrlMappingService.class);
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final UrlMappingRepository urlMappingRepository;

    public UrlMappingService(UrlMappingRepository urlMappingRepository) {
        this.urlMappingRepository = urlMappingRepository;
    }

    public UrlMapping saveShortUrl(String originalUrl) {
        String trimmedUrl = originalUrl.trim();
        log.info("Saving short URL mapping for original URL: {}", trimmedUrl);
        String shortCode = generateBase62ShortCode(trimmedUrl);

        return urlMappingRepository.findByShortCode(shortCode)
                .orElseGet(() -> {
                    UrlMapping saved = urlMappingRepository.save(
                            new UrlMapping(shortCode, trimmedUrl, LocalDateTime.now()));
                    log.info("Short code {} saved for original URL {}", shortCode, trimmedUrl);
                    return saved;
                });
    }

    public UrlMapping findByShortCode(String shortCode) {
        log.info("Lookup request for shortCode: {}", shortCode);
        return urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    log.warn("No long URL found for shortCode: {}", shortCode);
                    return new IllegalStateException("couldn't find a matching long URL to redirect");
                });
    }

    public UrlMapping resolveRedirect(String shortCode) {
        UrlMapping urlMapping = findByShortCode(shortCode);
        long updatedClickCount = (urlMapping.getClickCount() == null ? 0 : urlMapping.getClickCount()) + 1;
        urlMapping.setClickCount(updatedClickCount);
        urlMapping.setLastAccessedAt(LocalDateTime.now());
        return urlMappingRepository.save(urlMapping);
    }

    private String generateBase62ShortCode(String originalUrl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(originalUrl.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, digest);
            StringBuilder shortCode = new StringBuilder();

            while (number.compareTo(BigInteger.ZERO) > 0) {
                BigInteger[] divideAndRemainder = number.divideAndRemainder(BigInteger.valueOf(BASE62.length()));
                shortCode.insert(0, BASE62.charAt(divideAndRemainder[1].intValue()));
                number = divideAndRemainder[0];
            }

            while (shortCode.length() < 8) {
                shortCode.insert(0, '0');
            }

            String generatedCode = shortCode.length() > 8 ? shortCode.substring(shortCode.length() - 8) : shortCode.toString();
            log.debug("Generated short code {} for original URL {}", generatedCode, originalUrl);
            return generatedCode;
        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to generate short code for original URL: {}", originalUrl, e);
            throw new IllegalStateException("Unable to generate short code", e);
        }
    }
}
