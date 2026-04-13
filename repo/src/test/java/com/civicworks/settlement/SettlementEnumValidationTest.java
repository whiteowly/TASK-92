package com.civicworks.settlement;

import com.civicworks.platform.error.ErrorCode;
import com.civicworks.platform.error.GlobalExceptionHandler;
import com.civicworks.platform.idempotency.IdempotencyService;
import com.civicworks.settlement.api.SettlementController;
import com.civicworks.settlement.application.SettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * D) Invalid enum-like fields on the payment request must be rejected with
 * 400 + the project's standard error envelope (not a generic 500).
 */
class SettlementEnumValidationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SettlementService settlementService = Mockito.mock(SettlementService.class);
        IdempotencyService idempotencyService = Mockito.mock(IdempotencyService.class);
        SettlementController controller =
                new SettlementController(settlementService, idempotencyService);

        objectMapper = new ObjectMapper();
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void invalidPaymentMethod_returns400WithEnvelope() throws Exception {
        String body = "{\"billId\":1,\"amount\":\"10.00\","
                + "\"method\":\"BITCOIN\",\"settlementType\":\"FULL\"}";

        mockMvc.perform(post("/api/v1/settlements/payments")
                        .header("Idempotency-Key", "test-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR))
                .andExpect(jsonPath("$.error.message", containsString("method")))
                .andExpect(jsonPath("$.error.details[0].field").value("method"))
                .andExpect(jsonPath("$.error.details[0].issue", containsString("CASH")))
                .andExpect(jsonPath("$.error.requestId").exists());
    }

    @Test
    void invalidSettlementType_returns400WithEnvelope() throws Exception {
        String body = "{\"billId\":1,\"amount\":\"10.00\","
                + "\"method\":\"CASH\",\"settlementType\":\"PARTIAL\"}";

        mockMvc.perform(post("/api/v1/settlements/payments")
                        .header("Idempotency-Key", "test-key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR))
                .andExpect(jsonPath("$.error.details[0].field").value("settlementType"))
                .andExpect(jsonPath("$.error.details[0].issue", containsString("EVEN_SPLIT")));
    }
}
