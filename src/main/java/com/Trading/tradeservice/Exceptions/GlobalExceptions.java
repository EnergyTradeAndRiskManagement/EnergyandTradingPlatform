package com.Trading.tradeservice.Exceptions;

import com.Trading.tradeservice.dtos.Response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptions {

    @ExceptionHandler(TradeValidationException.class)
    public ResponseEntity<ErrorResponse> handleTradeValidationException(TradeValidationException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("TRADE_VALIDATION_FAILED", e.getMessage())
        );
    }

    @ExceptionHandler(InvalidTradeLifecycleException.class)
    public ResponseEntity<ErrorResponse> handleLifecycleException(InvalidTradeLifecycleException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ErrorResponse("INVALID_LIFECYCLE_TRANSITION", e.getMessage())
        );
    }

    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyException(IdempotencyException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("IDEMPOTENCY_CONFLICT", e.getMessage())
        );
    }

    @ExceptionHandler(TradeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTradeNotFound(TradeNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse("TRADE_NOT_FOUND", e.getMessage())
        );
    }

    @ExceptionHandler(TradeConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(TradeConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse("TRADE_CONCURRENT_MODIFICATION", ex.getMessage())
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ErrorResponse("OPTIMISTIC_LOCK_FAILURE", "Trade was updated or cancelled by another user concurrently. Please reload.")
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(
                new ErrorResponse("ARGUMENT_VALIDATION_FAILED", errors)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse("INTERNAL_SERVER_ERROR", ex.getMessage())
        );
    }
}
