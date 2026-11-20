package com.example.txn.controller;

import com.example.txn.service.ReportService;

import com.example.txn.dto.FlaggedAccount;
import com.example.txn.dto.LiveRiskyMerchant;
import com.example.txn.streams.MerchantFlagQueryService;
import com.example.txn.dto.RiskyMerchant;
import com.example.txn.exception.InvalidParameterException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    static final long MAX_RANGE_DAYS = 366;
    static final int MAX_LIVE_MINUTES = 60;

    private final ReportService reportService;
    private final MerchantFlagQueryService merchantFlagQueryService;

    public ReportController(ReportService reportService, MerchantFlagQueryService merchantFlagQueryService) {
        this.reportService = reportService;
        this.merchantFlagQueryService = merchantFlagQueryService;
    }

    @GetMapping("/flagged-accounts")
    public List<FlaggedAccount> flaggedAccounts(@RequestParam(required = false) String minCount) {
        if (minCount == null || minCount.isBlank()) {
            throw new InvalidParameterException("minCount", "Missing required parameter 'minCount'");
        }
        int n;
        try {
            n = Integer.parseInt(minCount.trim());
        } catch (NumberFormatException e) {
            throw new InvalidParameterException("minCount", "Parameter 'minCount' must be a positive integer");
        }
        if (n < 1) {
            throw new InvalidParameterException("minCount", "Parameter 'minCount' must be a positive integer");
        }
        return reportService.flaggedAccounts(n);
    }

    /** Live view from Kafka Streams: flagged counts per merchant over the last N minutes. */
    @GetMapping("/live-risky-merchants")
    public List<LiveRiskyMerchant> liveRiskyMerchants(@RequestParam(defaultValue = "5") int minutes) {
        if (minutes < 1 || minutes > MAX_LIVE_MINUTES) {
            throw new InvalidParameterException("minutes",
                    "Parameter 'minutes' must be between 1 and " + MAX_LIVE_MINUTES);
        }
        return merchantFlagQueryService.topMerchants(minutes);
    }

    @GetMapping("/risky-merchants")
    public List<RiskyMerchant> riskyMerchants(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        LocalDate fromDate = parseDate("from", from);
        LocalDate toDate = parseDate("to", to);
        if (fromDate.isAfter(toDate)) {
            throw new InvalidParameterException("from", "Parameter 'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_RANGE_DAYS) {
            throw new InvalidParameterException("to", "Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        return reportService.riskyMerchants(fromDate, toDate);
    }

    private static LocalDate parseDate(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidParameterException(name, "Missing required parameter '" + name + "'");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidParameterException(name,
                    "Invalid value for parameter '" + name + "', expected ISO date yyyy-MM-dd");
        }
    }
}
