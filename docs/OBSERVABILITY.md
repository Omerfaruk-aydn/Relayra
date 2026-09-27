# Relayra — Observability

## 1. Amaç

Production davranışı "neden bozuldu?" sorusunu cevaplayabilmelidir.

---

## 2. Structured Logging

JSON-friendly structured logs tercih edilir.

Alanlar:
- timestamp
- level
- service
- requestId
- userId (uygunsa)
- operation
- status
- latencyMs
- errorCode

Asla:
- password
- token
- secret

loglanmaz.

---

## 3. Correlation ID

REST request başına requestId oluştur.

WebSocket inbound eventlerde de correlation/request id kullanılabilir.

Client error response ile server log eşleştirilebilmelidir.

---

## 4. Metrics

Önemli metrikler:

### HTTP
- request count
- latency
- 4xx
- 5xx

### Auth
- login success/fail
- refresh success/fail
- rate-limited auth

### Messaging
- messages created
- failed message writes
- duplicate message prevention count
- WebSocket active connections
- reconnect count

### Redis
- latency
- failure count

### DB
- connection pool usage
- query latency
- transaction errors

### Upload
- upload count
- rejected file count
- bytes uploaded

---

## 5. Health Checks

Actuator:
- liveness
- readiness
- database
- Redis
- storage dependency (gerektiğinde)

Sensitive actuator endpoints public olmamalıdır.

---

## 6. Alerting

Production aşamasında alert adayları:
- 5xx spike
- auth failure anomaly
- DB pool exhaustion
- WebSocket disconnect spike
- Redis unavailable
- storage unavailable
- latency SLO breach

---

## 7. Error Classification

Beklenen business error:
- INFO/WARN

Unexpected server error:
- ERROR + stack trace internal log

Client'a stack trace dönülmez.
