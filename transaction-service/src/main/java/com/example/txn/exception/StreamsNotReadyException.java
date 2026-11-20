package com.example.txn.exception;

/** The Kafka Streams state store cannot be queried yet (not running or rebalancing). */
public class StreamsNotReadyException extends RuntimeException {
    public StreamsNotReadyException(String message, Throwable cause) {
        super(message, cause);
    }

    public StreamsNotReadyException(String message) {
        super(message);
    }
}
