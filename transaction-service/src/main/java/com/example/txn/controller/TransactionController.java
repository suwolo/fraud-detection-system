package com.example.txn.controller;

import com.example.txn.service.TransactionService;

import com.example.txn.dto.CreateTransactionRequest;
import com.example.txn.dto.PageResponse;
import com.example.txn.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TransactionResponse submit(@Valid @RequestBody CreateTransactionRequest request) {
        return service.submit(request);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable String id) {
        return service.getById(id);
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(accountId, status, page, size);
    }
}
