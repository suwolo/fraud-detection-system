package com.example.txn.exception;

public class FraudUnavailableException extends RuntimeException {
    public FraudUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public FraudUnavailableException(String message) {
        super(message);
    }
}