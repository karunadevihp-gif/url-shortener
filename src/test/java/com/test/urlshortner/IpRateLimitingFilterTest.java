package com.test.urlshortner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.test.urlshortner.entity.UrlMapping;
import com.test.urlshortner.service.UrlMappingService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IpRateLimitingFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlMappingService urlMappingService;

    @Test
    void postShortenEndpointIsRateLimitedAfterTenRequests() throws Exception {
        when(urlMappingService.saveShortUrl(anyString()))
                .thenReturn(new UrlMapping("abc12345", "https://example.com", LocalDateTime.now()));

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"longURL\":\"https://example.com/" + i + "\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longURL\":\"https://example.com/blocked\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too many requests: limit 10 requests per IP per minute"));
    }

    @Test
    void getRedirectEndpointIsNotRateLimited() throws Exception {
        UrlMapping urlMapping = new UrlMapping("abc1234", "https://example.org", LocalDateTime.now());
        when(urlMappingService.findByShortCode("abc1234")).thenReturn(urlMapping);

        mockMvc.perform(get("/api/v1/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.org"));
    }

    @Test
    void actuatorHealthEndpointIsNotRateLimited() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
