package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.RejectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<RejectionResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
    List<String> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .collect(Collectors.toList());
    LOG.warn("Validation failed: {} error(s)", errors.size());
    return new ResponseEntity<>(
        RejectionResponse.builder().status("Rejected").errors(errors).build(),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<RejectionResponse> handlePaymentRejected(PaymentRejectedException ex) {
    LOG.warn("Payment rejected with {} validation error(s)", ex.getErrors().size());
    return new ResponseEntity<>(
        RejectionResponse.builder().status("Rejected").errors(ex.getErrors()).build(),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }
}
