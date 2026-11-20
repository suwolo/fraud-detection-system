package com.example.fraud.controller;

import com.example.fraud.config.FraudProperties;
import com.example.fraud.rule.FraudRule;
import com.example.fraud.service.ScoringService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validates: Requirements 3.7, 3.8. */
@WebMvcTest(ScoreController.class)
@Import({ScoringService.class, ScoreControllerTest.TestConfig.class})
class ScoreControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        List<FraudRule> noRules() {
            return List.of();
        }

        @Bean
        FraudProperties fraudProperties() {
            return new FraudProperties();
        }
    }

    @Autowired
    MockMvc mvc;

    private static final String TX = """
            {"id":"t1","accountId":"a1","amount":100.50,"currency":"IDR","merchantId":"m1",
             "merchantCategory":"RETAIL","country":"ID","eventTime":"2024-01-15T12:00:00Z"}""";

    @Test
    void validRequestReturns200WithScoreZeroWhenNoRules() throws Exception {
        mvc.perform(post("/score").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction\":" + TX + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskScore").value(0))
                .andExpect(jsonPath("$.flagged").value(false))
                .andExpect(jsonPath("$.ruleResults").isEmpty());
    }

    @Test
    void missingTransactionReturns400() throws Exception {
        mvc.perform(post("/score").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("transaction"));
    }

    @Test
    void missingTransactionFieldReturns400NamingField() throws Exception {
        String noCountry = TX.replace("\"country\":\"ID\",", "");
        mvc.perform(post("/score").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transaction\":" + noCountry + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("transaction.country"));
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mvc.perform(post("/score").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }
}
