package com.example.txn.dto;

import java.time.Instant;

public record PrevTx(Instant eventTime, String country) {
}