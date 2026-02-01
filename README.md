## Payment Gateway (Java 17, Spring Boot 3)

This service exposes a minimal Payment Gateway API that validates card payments, requests authorization from an acquiring bank (simulator), stores a summary, and allows retrieval by payment ID.

### Table of Contents
- [Architecture & Project Structure](#architecture--project-structure)
- [Build, Run, Test](#build-run-test)
- [Bank Simulator Behavior](#bank-simulator-behavior)
- [API](#api)
- [Validation & Security](#validation--security)
- [Logging & Observability](#logging--observability)
- [Configuration](#configuration)
- [Examples (curl)](#examples-curl)
- [Postman Examples](#postman-examples)
- [Testing](#testing)
- [Production Hardening & Future Work](#production-hardening--future-work)
- [Troubleshooting & FAQ](#troubleshooting--faq)

---

## Architecture & Project Structure

Runtime flow
- POST `/api/v1/payments`: validate request → call bank simulator → map authorized/declined status → persist summary → return status with summary.
- GET `/api/v1/payments/{id}`: fetch persisted summary → return 200 or 404.

Packages
- controller/ — HTTP layer (`PaymentGatewayController`).
- service/ — Orchestration and domain rules (`PaymentGatewayService`, `PaymentValidator`, `BankService`).
- repository/ — In‑memory storage (`PaymentsRepository`, thread‑safe).
- model/ — DTOs (request/response, bank request/response records, error envelopes).
- exception/ — Domain exceptions and `GlobalExceptionHandler`.
- configuration/ — Beans (RestTemplate, CorrelationIdFilter, BankService wiring).
- utils/ — Helpers (`CardDataUtil`).

External dependency
- Bank simulator (Mountebank) runs via `docker-compose up bank_simulator` and exposes `POST http://localhost:8080/payments`.

### Architecture Diagram
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
  |  Hypothetical     |                                              |                            |
  |  Log Aggregator   | <--------------------------------------------+                            |
  |  (ELK/Datadog/etc)|                                              |   +---------------------+  |
  +-------------------+                                              |   |   BankService (HTTP)|  |
                                                                     |   +----------+----------+  |
                                                                     +--------------|-------------+
                                                                                    |
                                                                                    | POST /payments
                                                                                    v
                                                                     +----------------------------+
                                                                     | Bank Simulator (Mountebank)|
                                                                     |          :8080             |
                                                                     +----------------------------+
```

---

## Build, Run, Test

Prerequisites
- JDK 17, Docker, curl (for examples).

Run Modes

1) Everything run in Docker (quick demo)
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

Base path: `/api/v1/payments`

Headers
- `X-API-Key` (required) — API key authenticating the caller.
- `X-Correlation-Id` (optional) — used to correlate logs end‑to‑end.

### POST /api/v1/payments
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

### GET /api/v1/payments/{id}
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
- API key via header `X-API-Key`.
- On success, the resolved `merchantId` is attached to the request for logging/analysis.
- On failure, the gateway returns 401 (missing) or 403 (invalid) with `{ "message": "..." }`.

Why an API key
- Prevents anonymous access to payment endpoints; simple and effective for service-to-service access.
- Enables per-merchant identification and auditing; pairs naturally in production with rate limits and quotas.

PII handling
- PAN/CVV are never stored or returned; only last 4 digits appear in responses/logs.
- Central helper `CardDataUtil` masks PAN/CVV in any stringification.

Why masking
- Compliance and risk reduction: Logs often leave the app boundary (centralized logging, tickets, screenshots). Masking prevents accidental exposure and reduces blast radius if logs leak.
- Operational sufficiency: Support typically needs only last 4 digits + correlation_id/payment_id to trace issues; full PAN/CVV is never required for troubleshooting.
- Defense‑in‑depth: Even if a developer logs a DTO or an unexpected error prints object state, masked toString prevents leakage by default.

Possible additional data safety measures for future (beyond this demo application)
- Tokenization/vaulting: Store tokens, not PANs.
- Secrets management: Use a secret manager for credentials/keys; never hard‑code.
- Data minimization & retention: Persist only what’s necessary (e.g., last 4 digits, status, currency, amount) and set strict retention policies and deletion workflows.
- Access controls & audit: Role‑based access to logs and data; audit trails for access to sensitive operations.

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
- `X-Correlation-Id` filter writes an ID to MDC and response headers; RestTemplate propagates it on outbound calls.
- Service sets MDC `payment_id` on creation for deep traceability; MDC is cleared at request end.
- Levels: 4xx as WARN, 5xx as ERROR; no PAN/CVV in logs.

Why a correlation ID in this service
- End‑to‑end traceability: One ID ties together controller, service, and bank call logs for a single payment request. When the bank returns a 503 or a decline, you can instantly correlate the inbound request with the outbound call.
- Cross‑service stitching: We forward `X-Correlation-Id` to the bank simulator. In a real system, the same ID would propagate to downstream services to stitch logs across the stack.
- Faster support/debugging: The ID is echoed in every response. A client can report it; operators grep one ID to see the full timeline without sifting through unrelated logs.
- Safe and lightweight: It contains no PII, is generated if missing, added to MDC automatically, and cleared at the request boundary so threads don’t leak state.

Cross‑cutting filters (what they do)
- CorrelationIdFilter
  - Reads `X-Correlation-Id` (or generates one), saves it in MDC for logging, echoes it in the response header, and is cleared at request end.
  - A RestTemplate interceptor forwards the same header on outbound bank calls, keeping logs stitchable across services.
- ApiKeyAuthenticationFilter
  - Enforces `X-API-Key` on all API routes (Swagger UI, API docs, and health endpoints are excluded; CORS preflight OPTIONS is allowed).
  - Resolves the API key to a `merchantId` (from `gateway.security.api-keys`) and attaches it to the request for logging/auditing and future per‑merchant policy.
  - Missing key → 401; invalid key → 403. In Swagger, use the “Authorize” button (ApiKeyAuth) to set the header.

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

## Examples (curl)

Start simulator + app
```
docker compose up bank_simulator
./gradlew bootRun
```

POST Authorized (odd-ending PAN)
```
curl -s -X POST http://localhost:8090/api/v1/payments \
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
CREATE=$(curl -s -X POST http://localhost:8090/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -d '{"card_number":"2222405343248877","expiry_month":12,"expiry_year":2030,"currency":"USD","amount":1050,"cvv":"123"}')
echo "$CREATE"
# Extract id
ID=$(echo "$CREATE" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

# 2) Retrieve
curl -s -H 'X-API-Key: test-key' http://localhost:8090/api/v1/payments/$ID
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

## Testing

- Unit: `./gradlew test`
- Integration (@Tag("integration")): `./gradlew integrationTest`
- Manual E2E: start simulator (`docker compose up bank_simulator`), run app (`./gradlew bootRun`), then use curl/Postman.

Test inventory (by class)
- `service/PaymentValidatorTest` — expiry YearMonth must be future; USD/EUR/GBP only; amount > 0; aggregates errors.
- `service/BankServiceTest` — simulator mapping: 200 authorized/unauthorized; 503 and HTTP timeouts map to `AcquiringBankUnavailableException`.
- `service/CardDataUtilTest` — masks PAN/CVV correctly; last‑4 extraction safe for invalid/short PAN.
- `service/PaymentGatewayServiceTest` — validation errors throw and skip bank/persist; authorized/declined persist with correct last‑4.
- `model/PostPaymentRequestTest` — `toString()` never leaks PAN/CVV; `expiry_date` formatted as `MM/YYYY`.
- `controller/ApiKeyAuthFilterTest` (@integration) — missing API key → 401; invalid API key → 403.
- `controller/PaymentGatewayControllerPostTest` (@integration) — POST: 201 Authorized/Declined; 400 Rejected; 503 bank unavailable.
- `controller/PaymentGatewayControllerValidatorRejectedTest` (@integration) — cross‑field validator triggers 400; bank not called; correlation header present.
- `controller/PaymentGatewayControllerErrorAdviceTest` (@integration) — unexpected runtime error → 500 ErrorResponse.
- `controller/PaymentGatewayControllerTest` (@integration) — GET: 200 for existing; 404 not found; invalid UUID → 400 Rejected.

---

## Production Hardening & Future Work

The current implementation intentionally focuses on the assessment scope. In production, we would add:

- API design & lifecycle
  - Versioning (e.g., `/api/v1`).
  - Stronger auth: OAuth2 client credentials or signed JWTs; mTLS for service‑to‑service.
  - Idempotency for POST (Idempotency‑Key header, short‑TTL store, conflict detection).
  - Rate limiting/quotas per API key and tenant; pagination & filtering for listing endpoints.

- Resilience & reliability
  - Retries only for transient faults (tight budget, jittered backoff), guarded by circuit breakers and bulkheads.
  - Outbox pattern for external notifications; queue based async processing when needed.

- Observability
  - Metrics: `gateway.payments{result=*}`, bank call timers, 4xx/5xx rates; dashboards and alerts.
  - Distributed tracing (trace/span ids) alongside correlation ids; synthetic checks.

- Data protection & compliance
  - PAN/CVV policy: never store CVV; avoid storing PAN.
  - Hashing/HMAC for comparisons/lookup.
  - Encryption in transit (TLS everywhere) and at rest (DB/volumes with KMS).
  - Secrets management (e.g., Vault/SM/KMS); automatic rotation; never in source control.
  - Data minimization & retention: configurable TTLs; GDPR/“right to be forgotten” workflows.

These measures, combined with the current validation, masking, and structured logging, ensure a secure and resilient gateway suitable for production workloads.

---

## Troubleshooting & FAQ

- 400 Rejected
  - Input failed validation (see errors[]). Common cases: PAN length, CVV length, lowercase currency, expiry not in the future.
- 401 Unauthorized
  - Missing `X-API-Key`. Add the header; for local dev use `test-key` (configurable).
- 403 Forbidden
  - Invalid `X-API-Key`. Verify configuration `gateway.security.api-keys` and the header value.
- 404 Not Found
  - Wrong route (unknown path) or payment id does not exist.
- 503 Service Unavailable
  - Bank simulator returned 503 (card ending 0) or is down/unreachable. Ensure `docker compose up bank_simulator` is running.
- Swagger UI not loading
  - Verify app is on 8090 and bank simulator on 8080; check port collisions.
- Logs hard to correlate
  - Provide `X-Correlation-Id` in requests; the gateway will echo it and include it in log lines.

---
