package com.test.urlshortner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.test.urlshortner.controller.GlobalExceptionHandler;
import com.test.urlshortner.controller.UrlMappingController;
import com.test.urlshortner.entity.UrlMapping;
import com.test.urlshortner.service.UrlMappingService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class UrlControllerTest {

    @Mock
    private UrlMappingService urlMappingService;

    @InjectMocks
    private UrlMappingController urlMappingController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(urlMappingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void shouldCreateShortUrlFromValidLongUrl() throws Exception {
        UrlMapping saved = new UrlMapping("abc12345", "https://example.com", LocalDateTime.now());
        when(urlMappingService.saveShortUrl("https://example.com")).thenReturn(saved);

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longURL\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortURL").value("http://localhost:8080/abc12345"));
    }

    @Test
    void shouldRejectInvalidLongUrl() throws Exception {
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longURL\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("invalid format for longURL"));
    }

    @Test
    void shouldRedirectToOriginalUrlWhenShortCodeExists() throws Exception {
        UrlMapping urlMapping = new UrlMapping("abc12345", "https://example.org", LocalDateTime.now());
        when(urlMappingService.resolveRedirect("abc12345")).thenReturn(urlMapping);

        mockMvc.perform(get("/api/v1/abc12345"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.org"));
    }

    @Test
    void shouldReturnAnalyticsForShortCode() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        UrlMapping urlMapping = new UrlMapping("abc12345", "https://example.org", now, 5L, now);
        when(urlMappingService.findByShortCode("abc12345")).thenReturn(urlMapping);

        mockMvc.perform(get("/api/v1/analytics/abc12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc12345"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.org"))
                .andExpect(jsonPath("$.clickCount").value(5));
    }

    @Test
    void shouldReturnNotFoundWhenShortCodeDoesNotExistInDb() throws Exception {
        when(urlMappingService.findByShortCode(anyString()))
                .thenThrow(new IllegalStateException("couldn't find a matching long URL to redirect"));

        mockMvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("couldn't find a matching long URL to redirect"));
    }
}
