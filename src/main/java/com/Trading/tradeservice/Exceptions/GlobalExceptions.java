package com.Trading.tradeservice.Exceptions;

import com.Trading.tradeservice.dtos.Response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptions {


    @ExceptionHandler(TradeValidationException.class)
    public ResponseEntity<ErrorResponse> handleTradeException(TradeValidationException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse( "Trade_Validation_Failed", e.getMessage())
                );
    }
    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyException(IdempotencyException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("Idempotency_Failed", e.getMessage())
        );
    }

    @ExceptionHandler(TradeNotFoundException.class)
    public ResponseEntity<String> tradeNotFound(TradeNotFoundException e){
        return ResponseEntity.notFound().build();
    }


    @ExceptionHandler(TradeConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            TradeConflictException ex) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "TRADE_CONCURRENT_MODIFICATION",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<String> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Trade was modified by another user. Please retry.");
    }
}
