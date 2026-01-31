package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.ErrorResponse;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.model.RejectionResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Process and retrieve payments")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @GetMapping("/{id}")
  @Operation(summary = "Retrieve a payment by ID")
  @ApiResponse(responseCode = "200", description = "Payment found",
      content = @Content(schema = @Schema(implementation = GetPaymentResponse.class)))
  @ApiResponse(responseCode = "400", description = "Bad request",
      content = @Content(schema = @Schema(implementation = RejectionResponse.class)))
  @ApiResponse(responseCode = "404", description = "Payment not found",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "500", description = "Internal server error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<GetPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }

  @PostMapping
  @Operation(summary = "Process a payment", description = "Validate, authorize via bank, persist and return payment summary")
  @ApiResponse(responseCode = "201", description = "Payment processed",
      content = @Content(schema = @Schema(implementation = PostPaymentResponse.class)))
  @ApiResponse(responseCode = "400", description = "Payment rejected (validation)",
      content = @Content(schema = @Schema(implementation = RejectionResponse.class)))
  @ApiResponse(responseCode = "503", description = "Bank unavailable",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "500", description = "Internal server error",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<PostPaymentResponse> processPayment(@Valid @RequestBody PostPaymentRequest request) {
    return new ResponseEntity<>(paymentGatewayService.processPayment(request), HttpStatus.CREATED);
  }
}
