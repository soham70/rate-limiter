# Rate Limiter — Interview Project Guide

Use this document as your script when explaining the project in technical interviews, system design rounds, or portfolio walkthroughs.

---

## 30-Second Elevator Pitch

> "I built a production-style API rate limiter in Java and Spring Boot. It supports two algorithms — token bucket and sliding window — and two storage backends — in-memory for single-node and Redis for distributed deployments. Requests are limited per IP or per API key via a Spring interceptor, and rejected clients get HTTP 429 with a Retry-After header. The design uses a strategy interface so algorithms and backends are swappable without changing the HTTP layer."

---

## 2-Minute Walkthrough

### Problem
APIs need to protect themselves from abuse — too many requests from one client can overload the server or unfairly consume resources. Rate limiting enforces a fair usage policy.

### Solution
I implemented a **pluggable rate limiter** as a Spring Boot HTTP interceptor that runs before every `/api/**` request.

### How a request flows

```
Client Request
    ↓
RateLimitInterceptor (resolve key: API key or IP)
    ↓
RateLimiterStrategy.tryConsume(key)
    ↓
Allowed → Controller returns 200
Rejected → HTTP 429 + Retry-After + JSON error body
```

### Key design decisions

1. **Strategy pattern** — `RateLimiterStrategy` interface with 4 implementations. The HTTP layer never knows which algorithm or backend is active.

2. **Token bucket (default)** — Allows controlled bursts (e.g. 5 requests instantly) then enforces sustained rate (1 req/sec refill). Good for most APIs.

3. **Sliding window (alternative)** — Tracks individual request timestamps in a rolling window. More precise, higher memory cost.

4. **In-memory vs Redis** — In-memory works for one server instance. Redis with Lua scripts makes limits **global across all replicas** behind a load balancer.

5. **Per-key isolation** — Each client gets their own bucket/window. Key = `X-API-Key` header if present, else client IP.

---

## Deep Dive: Token Bucket Algorithm

### Concept
Imagine a bucket that holds tokens. Each request consumes 1 token. Tokens refill continuously at a fixed rate. If the bucket is empty, the request is rejected.

### Parameters
- **capacity** (e.g. 5) — max burst size
- **refill-rate** (e.g. 1.0/sec) — sustained rate after burst is consumed

### Example behavior (capacity=5, refill=1/sec)
| Time | Action | Result |
|------|--------|--------|
| t=0 | 5 rapid requests | All allowed (burst) |
| t=0 | 6th request | Rejected (429) |
| t=1 | 1 request | Allowed (1 token refilled) |

### Why not a simple counter?
A fixed-window counter ("100 req/minute") allows **200 requests in 2 seconds** if the client sends 100 at 11:59:59 and 100 at 12:00:00. Token bucket avoids this boundary problem.

### Thread safety (in-memory)
Each `TokenBucket` uses `synchronized` refill + consume. Buckets are stored in a `ConcurrentHashMap` keyed by client identifier.

---

## Deep Dive: Sliding Window Algorithm

### Concept
Track timestamps of recent requests. Count how many fall within the last N seconds. If count >= limit, reject.

### Parameters
- **window-seconds** (e.g. 10) — rolling window size
- **max-requests** (e.g. 5) — max allowed in that window

### Trade-off vs token bucket
| | Token Bucket | Sliding Window |
|---|-------------|----------------|
| Memory | O(1) per key | O(requests in window) per key |
| Precision | Allows bursts | Strict count in window |
| Use case | Most public APIs | Strict fairness needed |

---

## Deep Dive: Distributed Rate Limiting (Redis)

### The problem
With 3 app replicas behind a load balancer, each in-memory limiter has its own state. A client hitting different replicas gets **3× the intended limit**.

### The solution
Redis becomes the **single source of truth**. All instances execute the same atomic Lua script:

**Token bucket Lua script:**
1. Read current tokens + last refill time from Redis hash
2. Compute refilled tokens based on elapsed time
3. If token available → decrement and return allowed
4. Else → return rejected

**Why Lua?** Separate Redis GET + SET commands have race conditions under concurrent load from multiple app instances. Lua runs atomically on the Redis server.

**Sliding window:** Uses Redis sorted set (ZSET) — score = timestamp, member = unique request ID. Script removes expired entries, counts remaining, adds new entry if under limit.

---

## Architecture Diagram (Explain on Whiteboard)

```
┌─────────────────────────────────────────────────┐
│                  Spring Boot App                 │
│                                                  │
│  DemoController ← RateLimitInterceptor           │
│                         │                        │
│                         ▼                        │
│              RateLimiterStrategy (interface)       │
│                    /    |    \    \              │
│                   /     |     \    \             │
│     InMemoryTB  InMemorySW  RedisTB  RedisSW     │
└─────────────────────────────────────────────────┘
                              │
                              ▼ (when backend=redis)
                        ┌──────────┐
                        │  Redis   │
                        │ (Lua/ZSET)│
                        └──────────┘
```

---

## Configuration (Show You Understand Ops)

```yaml
ratelimiter:
  strategy: token-bucket    # or sliding-window
  backend: in-memory        # or redis
  capacity: 5
  refill-rate: 1.0
  window-seconds: 10
  max-requests: 5
```

Spring `@ConditionalOnProperty` selects the correct bean at startup — no code changes needed to switch modes.

---

## HTTP Layer Details

### Key resolution
```
if X-API-Key header present → key = "key:{apiKey}"
else                        → key = "ip:{remoteAddr}"
```

### 429 response
```json
{
  "error": "rate_limit_exceeded",
  "message": "Too many requests. Please slow down.",
  "retryAfterSeconds": 1
}
```
Plus `Retry-After: 1` header (RFC 6585).

---

## What Makes This a Strong Portfolio Project

1. **Real algorithm** — Not just a counter; implements token bucket and sliding window correctly.
2. **Production concerns** — Distributed state, atomicity, Retry-After, per-key isolation.
3. **Clean architecture** — Strategy pattern, config-driven, interceptor separation.
4. **Testable** — Core algorithm unit tested without Spring; strategies tested independently.
5. **Extensible** — Could add leaky bucket, fixed window, or gateway integration next.

---

## How to Demo Live (2 Minutes)

```powershell
# Terminal 1: start app
mvn spring-boot:run

# Terminal 2: burst test
1..8 | ForEach-Object {
  try {
    $r = Invoke-WebRequest -Uri http://localhost:8081/api/ping -UseBasicParsing
    "$_ -> $($r.StatusCode)"
  } catch {
    "$_ -> $($_.Exception.Response.StatusCode.value__)"
  }
}
# Expect: 200 x5, then 429 x3

# Test per-API-key isolation
Invoke-WebRequest -Uri http://localhost:8081/api/ping -Headers @{"X-API-Key"="client-a"} -UseBasicParsing
Invoke-WebRequest -Uri http://localhost:8081/api/ping -Headers @{"X-API-Key"="client-b"} -UseBasicParsing
# Both succeed — separate buckets
```

---

## Common Follow-Up Questions (Quick Answers)

**Q: Why token bucket over leaky bucket?**
Token bucket allows bursts; leaky bucket enforces a strict constant outflow rate with no burst.

**Q: What if Redis goes down?**
Production systems typically fail-open (allow traffic) or fail-closed (reject all). I'd add a circuit breaker and configurable fallback policy.

**Q: How would you rate-limit at the API gateway instead?**
Same algorithms apply. This interceptor logic maps directly to Kong, NGINX, or Spring Cloud Gateway filters. The Redis layer stays the same.

**Q: How do you prevent IP spoofing?**
Trust `X-Forwarded-For` only from known proxies/load balancers. Prefer API key auth for authenticated clients.

**Q: Big-O complexity?**
Token bucket: O(1) per request. Sliding window: O(k) where k = requests in window (or O(log n) with Redis ZSET).

---

## Suggested Interview Story Arc

1. **Motivation** — "APIs need protection from abuse; rate limiting is a classic system design problem."
2. **MVP** — "I started with token bucket in plain Java, unit tested, no framework."
3. **Integration** — "Wired it into Spring as an interceptor with per-IP/API-key bucketing."
4. **Scale problem** — "In-memory breaks with multiple replicas — moved state to Redis with Lua for atomicity."
5. **Flexibility** — "Added sliding window as a pluggable alternative and made everything config-driven."
6. **What's next** — "Gateway integration, metrics/dashboards, dynamic limits per tenant tier."

---

## Keywords to Use (Signals Senior Thinking)

- Token bucket / sliding window / fixed window / leaky bucket
- Burst vs sustained rate
- Atomicity (Lua script)
- Strategy pattern / pluggable backends
- HTTP 429 / Retry-After
- Horizontal scaling / shared state
- Per-tenant / per-IP / per-API-key keying
- Fail-open vs fail-closed
- O(1) vs memory trade-offs
