package com.example.txn.controller;

import com.example.txn.service.TransactionService;
import com.example.txn.service.StatisticsService;

import com.example.txn.dto.StatisticsResponse;
import com.example.txn.exception.InvalidParameterException;
import com.example.txn.exception.NotFoundException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final TransactionService transactionService;

    public StatisticsController(StatisticsService statisticsService, TransactionService transactionService) {
        this.statisticsService = statisticsService;
        this.transactionService = transactionService;
    }

    @GetMapping("/{id}/statistics")
    public StatisticsResponse statistics(@PathVariable String id,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        Instant fromInstant = parse("from", from);
        Instant toInstant = parse("to", to);
        if (fromInstant.isAfter(toInstant)) {
            throw new InvalidParameterException("from", "Parameter 'from' must not be after 'to'");
        }
        if (!transactionService.accountExists(id)) {
            throw new NotFoundException("Account not found: " + id);
        }
        return statisticsService.statistics(id, fromInstant, toInstant);
    }

    private static Instant parse(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidParameterException(name, "Missing required parameter '" + name + "'");
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidParameterException(name,
                    "Invalid value for parameter '" + name + "', expected ISO-8601 instant");
        }
    }
}
