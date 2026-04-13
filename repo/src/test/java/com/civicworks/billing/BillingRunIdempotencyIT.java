package com.civicworks.billing;

import com.civicworks.billing.api.BillingController;
import com.civicworks.billing.application.BillingService;
import com.civicworks.platform.error.ErrorCode;
import com.civicworks.platform.error.GlobalExceptionHandler;
import com.civicworks.platform.idempotency.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test verifying that POST /api/v1/billing/runs with no
 * Idempotency-Key header returns a normalized 4xx client error using
 * the project's error envelope — not a generic 500 from an unhandled
 * MissingRequestHeaderException.
 */
class BillingRunIdempotencyIT {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        BillingService billingService = Mockito.mock(BillingService.class);
        IdempotencyService idempotencyService = Mockito.mock(IdempotencyService.class);

        BillingController controller = new BillingController(billingService, idempotencyService);
        GlobalExceptionHandler advice = new GlobalExceptionHandler();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(advice)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void billingRunRequiresIdempotencyKey() throws Exception {
        Map<String, Object> body = Map.of(
                "cycleDate", LocalDate.of(2026, 4, 1).toString(),
                "cycleType", "MONTHLY"
        );

        mockMvc.perform(post("/api/v1/billing/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.MISSING_IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.error.message", containsString("Idempotency-Key")))
                .andExpect(jsonPath("$.error.requestId").exists());
    }
}
