package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.RejectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<RejectionResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    List<String> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getDefaultMessage())
        .collect(Collectors.toList());
    LOG.warn("Validation failed: {} error(s)", errors.size());
    return new ResponseEntity<>(
        RejectionResponse.builder().status(PaymentStatus.REJECTED).errors(errors).build(),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<RejectionResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
    LOG.warn("Malformed JSON request");
    return new ResponseEntity<>(
        RejectionResponse.builder().status(PaymentStatus.REJECTED).errors(List.of("Malformed JSON request")).build(),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<RejectionResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String field = ex.getName();
    LOG.warn("Type mismatch for parameter: {}", field);
    String message = field + ": invalid value";
    return new ResponseEntity<>(
        RejectionResponse.builder().status(PaymentStatus.REJECTED).errors(List.of(message)).build(),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<RejectionResponse> handlePaymentRejected(PaymentRejectedException ex) {
    LOG.warn("Payment rejected with {} validation error(s)", ex.getErrors().size());
    return new ResponseEntity<>(
        RejectionResponse.builder().status(PaymentStatus.REJECTED).errors(ex.getErrors()).build(),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.warn("Resource not found");
    return new ResponseEntity<>(new ErrorResponse("Payment not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
    LOG.warn("No handler found for path");
    return new ResponseEntity<>(new ErrorResponse("Resource not found"), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(AcquiringBankUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBankUnavailable(AcquiringBankUnavailableException ex) {
    LOG.error("Bank unavailable", ex);
    return new ResponseEntity<>(new ErrorResponse("Payment processor unavailable, retry later"),
        HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
    LOG.warn("Unauthorized: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse("Unauthorized"), HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
    LOG.warn("Forbidden: {}", ex.getMessage());
    return new ResponseEntity<>(new ErrorResponse("Forbidden"), HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
    LOG.error("Unhandled error", ex);
    return new ResponseEntity<>(new ErrorResponse("Internal server error"),
        HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
