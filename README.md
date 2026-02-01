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
- [Design Decisions & Trade-offs](#design-decisions--trade-offs)
- [Examples (curl)](#examples-curl)
- [Contributing & Conventions](#contributing--conventions)
- [License](#license)

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

---

## Build, Run, Test

Prerequisites
- JDK 17, Docker, curl (for examples).

Quick start
- Build: `./gradlew clean build`
- Start bank simulator: `docker-compose up` (listens on 8080)
- Run app: `./gradlew bootRun` (listens on 8090)
- Tests: `./gradlew test`

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

## Examples (curl)

Start simulator + app
```
docker-compose up -d
./gradlew bootRun
```

POST Authorized (odd last digit)
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

POST Declined (even last digit)
```
curl -s -X POST http://localhost:8090/api/payments \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: test-key' \
  -d '{"card_number":"2222405343248878","expiry_month":12,"expiry_year":2030,"currency":"USD","amount":1050,"cvv":"123"}'
```

GET by id
```
curl -s -H 'X-API-Key: test-key' http://localhost:8090/api/payments/<id-from-post>
```

---

## Contributing & Conventions

- Java 17, Spring Boot 3, Lombok builders on DTOs for clarity.
- Code style enforced by `.editorconfig` (2‑space indent, 100 cols).
- Keep controllers thin; place validation/orchestration in `service/` and storage in `repository/`.
- Never log sensitive data; use `CardDataUtil` for masking.

---

## License

This project is provided as part of a coding exercise template. Use and adapt as needed for evaluation purposes.
