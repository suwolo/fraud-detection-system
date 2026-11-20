package com.example.txn.dto;

/** Flagged transaction count for a merchant, taken from the Kafka Streams window store. */
public record LiveRiskyMerchant(String merchantId, long flaggedCount) {
}
