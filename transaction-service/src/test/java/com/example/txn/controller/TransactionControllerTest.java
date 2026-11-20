package com.example.txn.controller;

import com.example.txn.dto.Tx;
import com.example.txn.exception.GlobalExceptionHandler;
import com.example.txn.service.TransactionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.txn.dto.CreateTransactionRequest;
import com.example.txn.dto.PageResponse;
import com.example.txn.dto.TransactionResponse;
import com.example.txn.dto.TransactionStatus;
import com.example.txn.exception.FraudUnavailableException;
import com.example.txn.exception.InvalidParameterException;
import com.example.txn.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TransactionService service;

    private static String body(String amount, String currency, boolean withAccount) {
        StringBuilder sb = new StringBuilder("{");
        if (withAccount) {
            sb.append("\"accountId\":\"acc-1\",");
        }
        sb.append("\"amount\":").append(amount).append(",")
                .append("\"currency\":\"").append(currency).append("\",")
                .append("\"merchantId\":\"m-1\",\"merchantCategory\":\"RETAIL\",\"country\":\"US\",")
                .append("\"eventTime\":\"2024-01-01T10:00:00Z\"}");
        return sb.toString();
    }

    private static TransactionResponse sample() {
        return new TransactionResponse("tx-1", "acc-1", new BigDecimal("10.00"), "USD", "m-1", "RETAIL", "US",
                Instant.parse("2024-01-01T10:00:00Z"), TransactionStatus.APPROVED, 5, List.of());
    }

    private void expectBadPost(String json) throws Exception {
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitReturns202() throws Exception {
        when(service.submit(any(CreateTransactionRequest.class))).thenReturn(sample());
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", "USD", true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("tx-1"));
    }

    @Test
    void zeroAmountReturns400() throws Exception {
        expectBadPost(body("0", "USD", true));
    }

    @Test
    void threeDecimalAmountReturns400() throws Exception {
        expectBadPost(body("10.001", "USD", true));
    }

    @Test
    void lowercaseCurrencyReturns400() throws Exception {
        expectBadPost(body("10.00", "usd", true));
    }

    @Test
    void missingFieldReturns400() throws Exception {
        expectBadPost(body("10.00", "USD", false));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        when(service.submit(any(CreateTransactionRequest.class))).thenThrow(new NotFoundException("Account not found"));
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", "USD", true)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        when(service.getById("nope")).thenThrow(new NotFoundException("Transaction not found: nope"));
        mvc.perform(get("/api/v1/transactions/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fraudUnavailableReturns503() throws Exception {
        when(service.submit(any(CreateTransactionRequest.class))).thenThrow(new FraudUnavailableException("down"));
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content(body("10.00", "USD", true)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void listReturns200() throws Exception {
        when(service.list(any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sample()), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pageSize101Returns400() throws Exception {
        when(service.list(any(), any(), any(), eq(101)))
                .thenThrow(new InvalidParameterException("size", "size must be between 1 and 100"));
        mvc.perform(get("/api/v1/transactions").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("size"));
    }

    @Test
    void negativePageReturns400() throws Exception {
        when(service.list(any(), any(), eq(-1), any()))
                .thenThrow(new InvalidParameterException("page", "page must be >= 0"));
        mvc.perform(get("/api/v1/transactions").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badStatusReturns400() throws Exception {
        when(service.list(any(), eq("BOGUS"), any(), any()))
                .thenThrow(new InvalidParameterException("status", "invalid status"));
        mvc.perform(get("/api/v1/transactions").param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericPageReturns400() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }
}
