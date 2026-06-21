# Rate Limiter

A pluggable, production-style API rate limiter built in Java/Spring Boot.
Built as a standalone portfolio project — and designed to be reused later
as the gateway-level rate limiter inside the microservices order system.

## Status

- [x] **Day 1** — Core token-bucket algorithm (`TokenBucket.java`), plain Java, unit tested.
- [x] **Day 2** — Wired into Spring Boot as an HTTP interceptor, applied per-IP / per-API-key.
- [ ] **Day 3** — Make it distributed using Redis (`INCR` + `EXPIRE`, or a Lua script for atomicity).
- [ ] **Day 4** — Add a sliding-window strategy as a pluggable alternative; write up the trade-offs.

## Run it

```bash
mvn spring-boot:run
```

Test it (capacity=5, refill-rate=1.0/sec by default — see `application.yml`):

```bash
# Fire 8 requests quickly — first 5 succeed, rest get HTTP 429
for i in {1..8}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/ping; done
```

Run tests:

```bash
mvn test
```

## Why token-bucket (not a fixed counter)?

A naive fixed-window counter (e.g. "max 100 requests per minute") allows a
client to send 100 requests at 11:59:59 and another 100 at 12:00:00 —
200 requests in under 2 seconds, which defeats the purpose. Token-bucket
smooths this by refilling continuously and naturally supports controlled
bursts up to the bucket's capacity, which is what most real APIs actually
want (allow short bursts, prevent sustained abuse).

## Day 3 plan — making it distributed (do this next)

Right now, `InMemoryTokenBucketLimiter` keeps state in a local
`ConcurrentHashMap`. That's correct for a single instance, but if you scale
to 3 replicas behind a load balancer, each replica has its own bucket — a
client could get up to 3x the intended limit. This is *the* problem that
comes up in every "design a rate limiter" system design interview.

Steps:
1. Add the `spring-boot-starter-data-redis` dependency (already commented
   out in `pom.xml` — just uncomment it).
2. Create `RedisTokenBucketLimiter implements RateLimiterStrategy`.
3. Use a Lua script executed via `RedisTemplate.execute(...)` to make the
   "check tokens, refill, decrement" sequence atomic — doing this as
   separate Redis calls introduces race conditions under concurrent load,
   which is worth understanding and explaining in interviews.
4. Swap the bean in `WebConfig.rateLimiterStrategy()` — nothing else in
   the codebase needs to change, because everything depends on the
   `RateLimiterStrategy` interface, not the concrete class.

## Day 4 plan — sliding window as an alternative strategy

Implement `SlidingWindowRateLimiter implements RateLimiterStrategy` using
a sorted set of timestamps (Redis `ZSET`) or a log of request times. Make
the active strategy configurable via `application.yml`
(`ratelimiter.strategy: token-bucket | sliding-window`).

In your README/portfolio writeup, be ready to explain the trade-off:
- **Token bucket**: cheap (O(1) per request), allows controlled bursts,
  slightly less precise about exact request timing.
- **Sliding window**: more precise rate enforcement, but more
  memory/compute per request (must track individual timestamps).

## Project structure

```
src/main/java/com/soham/ratelimiter/
  core/
    TokenBucket.java              # Day 1 — core algorithm, no Spring
    RateLimiterStrategy.java      # pluggable strategy interface
    InMemoryTokenBucketLimiter.java
  filter/
    RateLimitInterceptor.java     # Day 2 — Spring HTTP interceptor
  config/
    WebConfig.java                # registers interceptor, exposes config
  controller/
    DemoController.java           # dummy endpoints to test against
src/test/java/com/soham/ratelimiter/core/
  TokenBucketTest.java            # Day 1 unit tests
```

## Next steps for your portfolio

- Deploy to Render/Railway and put the live URL + curl examples in this README.
- Record a short terminal gif showing the 429 kicking in.
- Once Day 3/4 are done, this becomes a strong standalone GitHub repo *and*
  a component you plug into the gateway of your microservices order system.
