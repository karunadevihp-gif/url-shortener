package com.test.urlshortner.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UrlShortenRequest(
        @NotBlank(message = "longURL is required")
        @Pattern(
                regexp = "^https://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/.*)?$",
                message = "longURL must be a valid HTTPS URL"
        )
        String longURL
) {
}
