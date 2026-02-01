## Payment Gateway (Java, Spring Boot)

This service exposes a minimal Payment Gateway API that validates card payments, requests authorization from an acquiring bank (simulator), stores a safe summary, and allows retrieval by id. It is written in Java 17 using Spring Boot 3.

Key guarantees:
- Validation per requirements (PAN length, CVV, expiry month/year combo, currency whitelist, positive amount).
- No PAN/CVV in responses or logs; only last 4 digits are exposed.
- Consistent error envelopes and production‑ready logging (correlation id, key=value format).

## Table of Contents
- [Architecture & Project Structure](#architecture--project-structure)
- [Build, Run, Test](#build-run-test)
- [API](#api)
- [Validation & Security](#validation--security)
- [Logging & Observability](#logging--observability)
- [Configuration](#configuration)
- [Testing](#testing)
- [Design Decisions & Trade‑offs](#design-decisions--trade-offs)
- [Examples (curl)](#examples-curl)
 

---

## Architecture & Project Structure

Runtime flow
- POST /api/payments: validate request → call bank simulator → map Authorized/Declined → persist safe summary → return 201 with summary.
- GET /api/payments/{id}: fetch persisted summary → return 200 or 404.

Packages
- controller/ — HTTP layer (`PaymentGatewayController`).
- service/ — Orchestration and domain rules (`PaymentGatewayService`, `PaymentValidator`, `BankService`).
- repository/ — In‑memory storage (`PaymentsRepository`, thread‑safe).
- model/ — DTOs (request/response, bank request/response records, error envelopes).
- exception/ — Domain exceptions and `GlobalExceptionHandler`.
- configuration/ — Beans (RestTemplate, CorrelationIdFilter, BankService wiring).
- utils/ — Cross‑cutting helpers (`CardDataUtil`).

External dependency
- Bank simulator (Mountebank) runs via `docker-compose` and exposes `POST http://localhost:8080/payments`. Do not modify `imposters/`.

### ASCII Architecture Diagram
```
  +-------------------+                 HTTP (JSON)                  +----------------------------+
  |   Client / POS    |  ----------------------------------------->  |   Payment Gateway (8090)   |
  | (Merchant System) |                                              |        Spring Boot         |
  +-------------------+                                              +----------------------------+
                                                                     |  +----------------------+  |
                                                                     |  |   Controller (Web)   |  |
                                                                     |  +----------+-----------+  |
                                                                     |             |              |
                                                                     |   +---------v----------+   |
                                                                     |   | PaymentGatewaySvc  |   |
                                                                     |   +----+----------+----+   |
                                                                     |        |          |        |
                                                                     |   +----v----+ +---v------+ |
                           Logs (stdout key=value)                   |   |Validator| |Repository| |
  +-------------------+   corr, payment_id, events                   |   +---------+ +----------+ |
  |  Future           |                                              |                            |
  |  Log Aggregator   | <--------------------------------------------+                            |
  |  (ELK/Datadog/etc)|                                              |   +---------------------+  |
  +-------------------+                                              |   |   BankService (HTTP)|  |
                                                                     |   +----------+----------+  |
                                                                     +---------------|-------------+
                                                                                     |
                                                                                     | POST /payments
                                                                                     v
                                                                     +----------------------------+
                                                                     | Bank Simulator (Mountebank)|
                                                                     |          :8080             |
                                                                     +----------------------------+
```

### How the BankService is called (code path)
```java
// In PaymentGatewayService
public PostPaymentResponse processPayment(PostPaymentRequest request) {
  var errors = paymentValidator.validate(request);
  if (!errors.isEmpty()) {
    throw new PaymentRejectedException(errors); // bank NOT called on validation failures
  }

  var bankReq = BankPaymentRequest.builder()
      .cardNumber(request.getCardNumber())
      .expiryDate(request.getExpiryDate()) // MM/YYYY
      .currency(request.getCurrency())
      .amount(request.getAmount())
      .cvv(request.getCvv())
      .build();

  var bankResp = bankService.requestAuthorization(bankReq); // ← outbound call
  var status = bankResp.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
  // ... persist summary and return 201
}

// In BankService
public BankPaymentResponse requestAuthorization(BankPaymentRequest request) {
  var response = restTemplate.postForEntity(baseUrl + "/payments", request, BankPaymentResponse.class);
  var body = response.getBody();
  if (body == null) throw new AcquiringBankUnavailableException("Empty bank response body");
  return body; // authorized:true/false, authorizationCode
}

// Correlation propagation (configured once in ApplicationConfiguration)
restTemplateBuilder.additionalInterceptors((req, body, exec) -> {
  var corr = MDC.get("correlation_id");
  if (corr != null && !corr.isBlank()) req.getHeaders().add("X-Correlation-Id", corr);
  return exec.execute(req, body);
});
```

---

## Build, Run, Test

Prerequisites
- JDK 17, Docker, curl (for examples).

Run Modes

1) Everything in Docker (quick demo)
- Build and start both services: `docker compose up --build`
- The gateway is available at `http://localhost:8090` and the simulator at `http://localhost:8080`.
- Make API requests from your host (see Examples section).
  - In Swagger UI, click "Authorize" (top right), select "ApiKeyAuth", enter `test-key` and press "Authorize".

2) Hybrid (bank in Docker, app via Gradle)
- Build: `./gradlew clean build`
- Start bank simulator: `docker compose up bank_simulator`
- Run app: `./gradlew bootRun`
- Run tests: `./gradlew test` (unit) and `./gradlew integrationTest` (integration)
  - In Swagger UI, click "Authorize" (top right), select "ApiKeyAuth", enter `test-key` and press "Authorize".

OpenAPI/Swagger UI
- http://localhost:8090/swagger-ui/index.html

Health
- http://localhost:8090/actuator/health

---

## Bank Simulator Behavior

Endpoint: `POST http://localhost:8080/payments`

All fields are required: `card_number`, `expiry_date` (MM/YYYY), `currency`, `amount`, `cvv`.

Missing required field → 400 Bad Request
```
{
  "error_message": "Not all required properties were sent in the request"
}
```

Authorized (card_number ends with 1, 3, 5, 7, 9) → 200 OK
```
{
  "authorized": true,
  "authorization_code": "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx"
}
```

Declined (card_number ends with 2, 4, 6, 8) → 200 OK
```
{
  "authorized": false,
  "authorization_code": ""
}
```

Bank unavailable (card_number ends with 0) → 503 Service Unavailable
```
{}
```

## API

Base path: `/api/payments`

Headers
- `X-API-Key` (required) — API key authenticating the caller.
- `X-Correlation-Id` (optional) — echoed back; used to correlate logs end‑to‑end.

### POST /api/payments
Process a payment (authorize with bank) and persist a safe summary.

Request body
```
{
  "card_number": "2222405343248877",
  "expiry_month": 12,
  "expiry_year": 2030,
  "currency": "USD",
  "amount": 1050,
  "cvv": "123"
}
```

Responses
- 201 Created
```
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Authorized",
  "cardNumberLastFour": 8877,
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "USD",
  "amount": 1050
}
```
- 400 Bad Request (validation)
```
{
  "status": "Rejected",
  "errors": [
    "Card number must be 14-19 digits (numbers only).",
    "Expiry date must be in the future"
  ]
}
```
- 503 Service Unavailable (bank unavailable/timeouts)
```
{ "message": "Payment processor unavailable, retry later" }
```
- 500 Internal Server Error
```
{ "message": "Internal server error" }
```

### GET /api/payments/{id}
Retrieve a previously processed payment summary.

Responses
- 200 OK — same shape as 201 above
- 404 Not Found — `{ "message": "Page not found" }`
- 400 Bad Request (bad id format) — `{"status":"Rejected","errors":["id: invalid value"]}`

---

## Validation & Security

Input validation
- Bean Validation on DTOs with human‑readable messages: PAN 14–19 digits, CVV 3–4 digits, currency 3 letters, month 1–12, amount > 0.
- Cross‑field rules in `PaymentValidator`: YearMonth(expiry) strictly in the future; currency must be one of USD/EUR/GBP.

Authentication
- API key via header `X-API-Key`. Keys are configured as `key:merchantId` pairs (e.g., `test-key:test-merchant`) and validated in a lightweight filter.
- On success, the resolved `merchantId` is attached to the request for logging/analysis.
- On failure, the gateway returns 401 (missing) or 403 (invalid) with `{ "message": "..." }`.

Why an API key (importance)
- Prevents anonymous access to payment endpoints; simple and effective for service-to-service access.
- Enables per-merchant identification and auditing; pairs naturally with rate limits and quotas.
- Easier developer onboarding for demos; can be replaced with OAuth2/JWT/mTLS in production.

PII handling
- PAN/CVV are never stored or returned; only last4 appears in responses/logs.
- Central helper `CardDataUtil` masks PAN/CVV in any stringification.

Why masking (production rationale)
- Compliance and risk reduction: Card data is regulated (PCI DSS). Logs often leave the app boundary (centralized logging, tickets, screenshots). Masking prevents accidental exposure and reduces blast radius if logs leak.
- Operational sufficiency: Support typically needs only last4 + correlation_id/payment_id to trace issues; full PAN/CVV is never required for troubleshooting.
- Defense‑in‑depth: Even if a developer logs a DTO or an unexpected error prints object state, masked toString prevents leakage by default.

Additional data safety measures (beyond this exercise)
- Tokenization/vaulting: Store tokens, not PANs. Delegate storage to a PCI‑compliant vault/provider.
- Secrets management: Use a secret manager for credentials/keys; never hard‑code.
- Transport security: Enforce TLS for all inbound/outbound traffic; validate certificates; consider mTLS to the acquirer.
- Data minimization & retention: Persist only what’s necessary (e.g., last4, status, currency, amount) and set strict retention policies and deletion workflows.
- Access controls & audit: Role‑based access to logs and data; audit trails for access to sensitive operations.
- Secure logging: Disable HTTP wire logging in prod; add log scrubbers/mask filters as a last resort.

Error handling
- `GlobalExceptionHandler` maps:
  - 400 (annotation errors, malformed JSON, param type mismatches) → `RejectionResponse { status, errors[] }`
  - 404 (domain & route) → `ErrorResponse { message }`
  - 503 (bank down) → `ErrorResponse { message }`
  - 500 (fallback) → `ErrorResponse { message }`

---

## Logging & Observability

Logging
- Logback with key=value lines to stdout for container ingestion (ELK/Datadog friendly).
- `X-Correlation-Id` filter writes an id to MDC and response headers; RestTemplate propagates it on outbound calls.
- Service sets MDC `payment_id` on creation for deep traceability; MDC is cleared at request end.
- Levels: 4xx as WARN, 5xx as ERROR; no PAN/CVV in logs.

Why a correlation id (in this service)
- End‑to‑end traceability: One id ties together controller, service, and bank call logs for a single payment request. When the bank returns a 503 or a decline, you can instantly correlate the inbound request with the outbound call.
- Cross‑service stitching: We forward `X-Correlation-Id` to the bank simulator. In a real system, the same id would propagate to downstream services to stitch logs across the stack.
- Faster support/debugging: The id is echoed in every response. A client can report it; operators grep one id to see the full timeline without sifting through unrelated logs.
- Safe and lightweight: It contains no PII, is generated if missing, added to MDC automatically, and cleared at the request boundary so threads don’t leak state.

Cross‑cutting filters (what they do)
- CorrelationIdFilter
  - Reads `X-Correlation-Id` (or generates one), saves it in MDC for logging, echoes it in the response header, and is cleared at request end.
  - A RestTemplate interceptor forwards the same header on outbound bank calls, keeping logs stitchable across services.
- ApiKeyAuthenticationFilter
  - Enforces `X-API-Key` on all API routes (Swagger UI, API docs, and health endpoints are excluded; CORS preflight OPTIONS is allowed).
  - Resolves the API key to a `merchantId` (from `gateway.security.api-keys`) and attaches it to the request for logging/auditing and future per‑merchant policy.
  - Missing key → 401; invalid key → 403. In Swagger, use the “Authorize” button (ApiKeyAuth) to set the header.

Health
- `/actuator/health` for liveness/readiness basics. Add more endpoints as needed per environment.

---

## Configuration

`src/main/resources/application.properties`
- `server.port=8090`
- `springdoc.swagger-ui.enabled=true`
- `springdoc.api-docs.enabled=true`
- `bank.base-url=http://localhost:8080` (simulator)
- `spring.mvc.throw-exception-if-no-handler-found=true` (unified 404 handling)
- `spring.messages.basename=messages` (human‑readable validation messages)

`docker-compose.yml`
- Starts Mountebank simulator on ports 2525 (admin) and 8080 (payments).

Runtime defaults
- RestTemplate connect/read timeouts configured via `ApplicationConfiguration` (tunable per environment).

---

## Testing

Scope & tools
- Frameworks: JUnit 5, Spring Boot Test, Mockito, MockMvc, MockRestServiceServer for `RestTemplate`.
- Separation:
  - Unit tests (default): `./gradlew test`
  - Integration tests (tagged @Tag("integration")): `./gradlew integrationTest`

Unit tests (fast, no Spring context)
- `PaymentValidatorTest` — expiry YearMonth rules, USD/EUR/GBP whitelist, amount > 0, aggregates multiple errors.
- `BankServiceTest` — maps simulator responses: 200 authorized/unauthorized, 503, timeouts.
- `CardDataUtilTest` — masks PAN/CVV, extracts last 4 safely.
- `PaymentGatewayServiceTest` — orchestration with mocks: rejected path (no bank call), authorized/declined, last‑4 fallback.

Integration tests (Spring context + MockMvc; tagged @Tag("integration"))
- `PaymentGatewayControllerPostTest` — POST success (Authorized/Declined), 400 Rejected, 503 mapping.
- `PaymentGatewayControllerTest` — GET happy path and 404 not found.
- Notes:
  - `@MockBean` is used for the bank client to avoid real HTTP calls.
  - Include `X-API-Key` header in requests.

E2E (manual or optional automated)
- Start simulator: `docker-compose up`.
- Run app: `./gradlew bootRun`.
- Exercise flows with curl or Postman (see Examples section) to verify end‑to‑end behavior against the real simulator.
- Optional: add a separate `@Tag("e2e")` test suite that hits the simulator; exclude from default `test` and run on demand or nightly.

Testing guidance
- Keep controllers thin; test business rules and mappings at the service/validator level.
- Reserve E2E for contract checks with the simulator; rely on MockMvc and MockRestServiceServer for speed and determinism.

### Test Inventory (short descriptions)
- `service/PaymentValidatorTest` — Verifies expiry YearMonth rules, strict USD/EUR/GBP whitelist, positive amount, and multi‑error aggregation.
- `service/BankServiceTest` — Ensures bank client maps simulator outcomes (authorized/unauthorized), and throws on 503/timeouts.
- `service/CardDataUtilTest` — Checks PAN/CVV masking and last‑4 extraction (including non‑digit/short PAN cases).
- `service/PaymentGatewayServiceTest` — Orchestrates validate → bank → persist; asserts rejected path skips bank, and authorized/declined are persisted with correct last‑4.
- `controller/PaymentGatewayControllerPostTest` (@integration) — POST: Authorized/Declined (201), validation Rejected (400), bank unavailable (503); requires `X-API-Key`.
- `controller/PaymentGatewayControllerValidatorRejectedTest` (@integration) — POST: cross‑field validator triggers 400 Rejected; bank client is not called; response has `X-Correlation-Id`.
- `controller/PaymentGatewayControllerErrorAdviceTest` (@integration) — POST: unexpected runtime error returns 500 with generic error envelope.
- `controller/PaymentGatewayControllerTest` (@integration) — GET: returns 200 for existing payment and 404 for unknown id with expected body.

---

## Design Decisions & Trade‑offs

- Simple, explicit API: POST/GET only; 201/200 success codes; predictable error envelopes.
- In‑memory store: `ConcurrentHashMap` for this exercise; swap to a persistent store behind the same repository contract when needed.
- Exact currency whitelist: USD, EUR, GBP per requirement (not configurable to avoid drift).
- Bank integration:
  - DTOs as Java records with `@JsonProperty` to match simulator snake_case.
  - Client is a plain class (`BankService`) wired via configuration, making dependencies explicit and tests simple.
- Security & privacy: masking at the edge; never log or return PAN/CVV.
- Logging for ops: correlation id everywhere, consistent event names, no bodies or secrets.

Omissions (by intent for the exercise)
- Idempotency: would typically be supported via `Idempotency-Key` header and a short‑TTL store to avoid duplicate charges.
- AuthN/Z and multi‑tenant controls: out of scope here; add API keys/JWT/mTLS as needed.
- Resilience policies: no retries/circuit breakers to keep behavior deterministic with the simulator; add selectively in production.
- Persistent storage & migrations: repository interface allows drop‑in replacement with JPA/JDBC.
- Full metrics: basic health/logs included; add Micrometer counters/timers for payments and bank calls as needed.

---

## Production Hardening & Future Work

The current implementation intentionally focuses on the assessment scope. In production, we would add:

- API design & lifecycle
  - Versioning (e.g., `/api/v1`) and explicit deprecation policy.
  - Stronger auth: OAuth2 client credentials or signed JWTs; mTLS for service‑to‑service.
  - Idempotency for POST (Idempotency‑Key header, short‑TTL store, conflict detection).
  - Rate limiting/quotas per API key and tenant; pagination & filtering for listing endpoints.

- Payments domain breadth
  - Capture/void/refund flows in addition to authorize; partial captures/refunds.
  - 3‑D Secure / SCA support; risk checks; AVS/CVV result handling.
  - Webhooks/callbacks for async events; retry with exponential backoff and signing.
  - Reconciliation/reporting endpoints; settlement summaries.

- Resilience & reliability
  - Retries only for transient faults (tight budget, jittered backoff), guarded by circuit breakers and bulkheads.
  - Outbox pattern for external notifications; queue based async processing when needed.
  - SLOs/SLIs (latency, availability, error rate) with alerting and runbooks.

- Observability
  - Metrics: `gateway.payments{result=*}`, bank call timers, 4xx/5xx rates; dashboards and alerts.
  - Distributed tracing (trace/span ids) alongside correlation ids; synthetic checks.

- Data protection & compliance
  - Tokenization/vaulting: store tokens, not PAN; detokenization only in privileged, audited services.
  - Encryption in transit (TLS everywhere) and at rest (DB/volumes with KMS); key rotation policies.
  - Strict PCI DSS scope reduction and segmentation; least‑privilege access to data and logs; audit trails.
  - Secrets management (e.g., Vault/SM/KMS); automatic rotation; never in source control.
  - Data minimization & retention: configurable TTLs; GDPR/“right to be forgotten” workflows.

- Platform & operations
  - Blue/green or canary deployments; health‑based rollbacks; multi‑AZ/region for HA.
  - WAF/DoS protections; IP allow‑listing for management endpoints; bot detection where relevant.
  - Comprehensive penetration testing and threat modeling; secure SDLC gates.

These measures, combined with the current validation, masking, and structured logging, ensure a secure and resilient gateway suitable for production workloads.

---

## Assumptions & Constraints

- Currency support is fixed to USD/EUR/GBP by requirement (not configurable).
- No persistent database is used; an in‑memory repository stores summaries for the process lifetime only.
- Bank simulator is the single external dependency and must be reachable at `bank.base-url` (defaults to `http://localhost:8080`).
- Time validation uses the system UTC clock (YearMonth now vs. card expiry); “current month” is not accepted.
- No idempotency semantics are implemented; duplicate submissions may create multiple entries.
- No retries/circuit breakers by design for this exercise; failures propagate deterministically.

---

## Troubleshooting & FAQ

- 401 Unauthorized
  - Missing `X-API-Key`. Add the header; for local dev use `test-key` (configurable).
- 403 Forbidden
  - Invalid `X-API-Key`. Verify configuration `gateway.security.api-keys` and the header value.
- 400 Rejected
  - Input failed validation (see errors[]). Common cases: PAN length, CVV length, lowercase currency, expiry not in the future.
- 404 Not Found
  - Wrong route (unknown path) or payment id does not exist.
- 503 Service Unavailable
  - Bank simulator returned 503 (card ending 0) or is down/unreachable. Ensure `docker compose up bank_simulator` is running.
- Swagger UI not loading
  - Verify app is on 8090 and bank simulator on 8080; check port collisions.
- Logs hard to correlate
  - Provide `X-Correlation-Id` in requests; the gateway will echo it and include it in log lines.

FAQ
- Why no retries?
  - Simulator outcomes are deterministic; retries add complexity without benefit and can mislead clients.
- Why no idempotency?
  - Out of scope for the exercise. In production, add an Idempotency‑Key header and a short‑TTL store.
- Why fixed currencies?
  - The brief specifies validating against no more than three; enforcing USD/EUR/GBP avoids drift.

---

## Examples (curl)

Start simulator + app
```
docker-compose up -d
./gradlew bootRun
```

POST Authorized (odd-ending PAN)
```
curl -s -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -H 'X-Correlation-Id: demo-123' \
  -d '{
        "card_number":"2222405343248877",
        "expiry_month":12,
        "expiry_year":2030,
        "currency":"USD",
        "amount":1050,
        "cvv":"123"
      }'
```
Response (201)
```
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Authorized",
  "cardNumberLastFour": 8877,
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "USD",
  "amount": 1050
}
```

POST Declined (even-ending PAN)
```
curl -s -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -d '{"card_number":"2222405343248878","expiry_month":12,"expiry_year":2030,"currency":"USD","amount":1050,"cvv":"123"}'
```
Response (201)
```
{
  "id": "b3b2bb4a-2c3a-45a9-8d2a-1b2c3d4e5f60",
  "status": "Declined",
  "cardNumberLastFour": 8878,
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "USD",
  "amount": 1050
}
```

POST Validation Error (400 Rejected)
```
curl -s -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -d '{
        "card_number":"123",             
        "expiry_month":0,                 
        "expiry_year":2020,               
        "currency":"SEK",               
        "amount":0,                       
        "cvv":"12"                      
      }'
```
Response (400)
```
{
  "status": "Rejected",
  "errors": [
    "Card number must be 14-19 digits (numbers only).",
    "Expiry month must be between 1 and 12.",
    "Currency must be a 3-letter ISO code (e.g., USD).",
    "Amount must be a positive integer in the minor currency unit (e.g., USD 10.50 -> 1050).",
    "CVV must be 3-4 digits."
  ]
}
```

POST Bank Unavailable (503)
```
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -d '{"card_number":"2222405343248870","expiry_month":12,"expiry_year":2030,"currency":"USD","amount":100,"cvv":"123"}'
# Expected: 503
```
Response body (503)
```
{ "message": "Payment processor unavailable, retry later" }
```

GET by id
```
curl -s -H 'X-API-Key: test-key' http://localhost:8090/api/payments/<id-from-post>
```
Response (200)
```
{
  "id": "<id-from-post>",
  "status": "Authorized",
  "cardNumberLastFour": 8877,
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "USD",
  "amount": 1050
}
```

GET Not Found (404)
```
curl -s -H 'X-API-Key: test-key' http://localhost:8090/api/payments/00000000-0000-4000-8000-000000000000
```
Response (404)
```
{ "message": "Page not found" }
```

Authorization errors
- Missing API key (401)
```
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2030,"currency":"USD","amount":1050,"cvv":"123"}'
# Expected: 401
```
- Invalid API key (403)
```
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: wrong-key' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2030,"currency":"USD","amount":1050,"cvv":"123"}'
# Expected: 403
```

Lowercase currency example (rejected by format)
```
curl -s -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2030,"currency":"usd","amount":1050,"cvv":"123"}'
```
Response (400)
```
{
  "status": "Rejected",
  "errors": [
    "Currency must be a 3-letter ISO code (e.g., USD)."
  ]
}
```

Full Flow Example (Create + Retrieve)
```
# 1) Create (Authorized example)
CREATE=$(curl -s -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2030,"currency":"USD","amount":1050,"cvv":"123"}')
echo "$CREATE"
# Extract id
ID=$(echo "$CREATE" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

# 2) Retrieve
curl -s -H 'X-API-Key: test-key' http://localhost:8090/api/payments/$ID
```
Sample Create response
```
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Authorized",
  "cardNumberLastFour": 8877,
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "USD",
  "amount": 1050
}
```
Sample Retrieve response
```
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "Authorized",
  "cardNumberLastFour": 8877,
  "expiryMonth": 12,
  "expiryYear": 2030,
  "currency": "USD",
  "amount": 1050
}
```

---

## Postman Examples

Import the collection at `postman/payment-gateway.postman_collection.json` into Postman. It defines:

- Variables
  - `baseUrl` (default `http://localhost:8090`)
  - `apiKey` (default `test-key` for local dev)

- Requests (with tests)
  - Process Payment — Authorized (odd)
    - Expects 201, status `Authorized`; saves `paymentId` for retrieval.
  - Process Payment — Declined (even)
    - Expects 201, status `Declined`.
  - Process Payment — Bank Unavailable (last digit 0)
    - Expects 503.
  - Process Payment — Validation Error (400 Rejected)
    - Expects 400, status `Rejected`.
  - Retrieve Payment — By ID
    - Uses saved `paymentId`; expects 200 with matching id.
  - Retrieve Payment — Not Found (404)
    - Uses a fixed UUID; expects 404 with `{ "message": "Page not found" }`.

Before running requests, start:
- Bank simulator: `docker compose up bank_simulator`
- Application: `./gradlew bootRun`

---
