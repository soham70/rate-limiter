# Rate Limiter

A pluggable, production-style API rate limiter built in Java/Spring Boot.
Supports **token-bucket** and **sliding-window** strategies, with **in-memory**
or **Redis-backed** storage for single-instance and distributed deployments.

## Status

- [x] **Day 1** — Core token-bucket algorithm (`TokenBucket.java`), plain Java, unit tested.
- [x] **Day 2** — Wired into Spring Boot as an HTTP interceptor, applied per-IP / per-API-key.
- [x] **Day 3** — Distributed Redis token bucket with atomic Lua script.
- [x] **Day 4** — Sliding-window strategy (in-memory + Redis ZSET), configurable via `application.yml`.

## Run it

```bash
mvn spring-boot:run
```

Test it (capacity=5, refill-rate=1.0/sec by default — see `application.yml`):

```powershell
# Fire 8 requests quickly — first 5 succeed, rest get HTTP 429
1..8 | ForEach-Object { (Invoke-WebRequest -Uri http://localhost:8081/api/ping -UseBasicParsing).StatusCode }
```

Run tests:

```bash
mvn test
```

Build:

```bash
mvn clean package
```

## Configuration

```yaml
ratelimiter:
  strategy: token-bucket       # token-bucket | sliding-window
  backend: in-memory           # in-memory | redis
  capacity: 5                  # token-bucket burst size
  refill-rate: 1.0             # token-bucket refill rate (tokens/sec)
  window-seconds: 10           # sliding-window duration
  max-requests: 5              # sliding-window limit
```

### Redis (distributed mode)

Start Redis:

```bash
docker compose up -d
```

Run with Redis profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=redis
```

Or set `ratelimiter.backend: redis` in `application.yml`.

## Why token-bucket (not a fixed counter)?

A naive fixed-window counter (e.g. "max 100 requests per minute") allows a
client to send 100 requests at 11:59:59 and another 100 at 12:00:00 —
200 requests in under 2 seconds. Token-bucket smooths this by refilling
continuously and supports controlled bursts up to `capacity`.

## Strategy trade-offs

| Strategy | Pros | Cons |
|----------|------|------|
| **Token bucket** | O(1) per request, allows controlled bursts | Slightly less precise about exact timing |
| **Sliding window** | Precise rate enforcement, no boundary burst | More memory (tracks timestamps per key) |

## Project structure

```
src/main/java/com/soham/ratelimiter/
  core/
    TokenBucket.java
    RateLimiterStrategy.java
    RateLimitResult.java
    InMemoryTokenBucketLimiter.java
    InMemorySlidingWindowRateLimiter.java
    RedisTokenBucketLimiter.java
    RedisSlidingWindowRateLimiter.java
  filter/
    RateLimitInterceptor.java
  config/
    RateLimiterProperties.java
    RateLimiterConfiguration.java
    WebConfig.java
  controller/
    DemoController.java
docs/
  INTERVIEW_PROJECT_GUIDE.md
  INTERVIEW_QUESTIONS.md
```

## Interview prep

See `docs/INTERVIEW_PROJECT_GUIDE.md` for how to explain this project in interviews,
and `docs/INTERVIEW_QUESTIONS.md` for practice questions from basic to advanced.
