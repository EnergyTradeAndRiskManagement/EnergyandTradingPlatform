package com.Trading.tradeservice.Exceptions;

public class InvalidTradeLifecycleException extends RuntimeException {
    public InvalidTradeLifecycleException(String message) {
        super(message);
    }
}
