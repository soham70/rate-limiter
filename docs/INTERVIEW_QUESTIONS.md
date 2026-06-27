# Rate Limiter — Interview Questions

Practice questions from basic to advanced, with suggested answer points.
Use alongside `INTERVIEW_PROJECT_GUIDE.md`.

---

## Level 1 — Fundamentals (Junior / Warm-Up)

### Q1. What is rate limiting and why do APIs need it?
**Answer points:**
- Controls how many requests a client can make in a time period
- Prevents abuse, DDoS, brute-force attacks, resource exhaustion
- Ensures fair usage across tenants
- Protects downstream services (DB, payment APIs)

---

### Q2. What HTTP status code is used when a client is rate limited?
**Answer:** `429 Too Many Requests` (RFC 6585). Optionally include `Retry-After` header telling the client when to retry.

---

### Q3. What is the difference between rate limiting and throttling?
**Answer points:**
- **Rate limiting** — hard cap; excess requests are rejected (429)
- **Throttling** — slows down requests (queues, delays) rather than rejecting
- Often used interchangeably in practice, but technically different

---

### Q4. What does your project do in one sentence?
**Answer:** It's a Spring Boot API rate limiter that supports token-bucket and sliding-window algorithms, with in-memory or Redis storage, applied per IP or API key via an HTTP interceptor.

---

### Q5. What endpoints does your project expose?
**Answer:**
- `GET /api/ping` — simple test endpoint
- `GET /api/data` — another protected endpoint
- Both are rate-limited under `/api/**`

---

### Q6. How do you identify which client to rate limit?
**Answer:** Check `X-API-Key` header first → key is `"key:{apiKey}"`. If absent, fall back to client IP → `"ip:{remoteAddr}"`. Each key gets its own bucket/window.

---

### Q7. What is a token bucket?
**Answer:** A bucket holds tokens. Each request consumes 1 token. Tokens refill at a constant rate. Bucket has a max capacity (burst size). Empty bucket = request rejected.

---

### Q8. What are the two main parameters of your token bucket?
**Answer:**
- **capacity** — max burst (e.g. 5 requests at once)
- **refill-rate** — sustained rate (e.g. 1 token per second)

---

### Q9. What happens if you send 8 requests instantly with capacity=5?
**Answer:** First 5 succeed (200). Requests 6–8 get HTTP 429. After ~1 second, 1 token refills and the next request succeeds.

---

### Q10. What is the Strategy pattern and where did you use it?
**Answer:** Define a family of algorithms behind a common interface (`RateLimiterStrategy`). The HTTP interceptor depends on the interface, not concrete classes. Swap token-bucket ↔ sliding-window or in-memory ↔ Redis via config without code changes.

---

## Level 2 — Implementation Details (Mid-Level)

### Q11. Walk me through the request flow in your project.
**Answer:**
1. Request hits `/api/**`
2. `RateLimitInterceptor.preHandle()` runs
3. Resolve client key (API key or IP)
4. Call `rateLimiter.tryConsume(key)`
5. Allowed → proceed to controller
6. Rejected → return 429 + JSON + Retry-After header

---

### Q12. Why did you use an interceptor instead of a servlet filter or AOP?
**Answer points:**
- Interceptor integrates cleanly with Spring MVC
- Runs after dispatcher mapping, before controller
- Easy to register for specific path patterns (`/api/**`)
- Filters run earlier (before Spring MVC); AOP is heavier for cross-cutting HTTP concerns
- Gateway filters would be the production equivalent at infrastructure level

---

### Q13. How is your TokenBucket thread-safe?
**Answer:** `tryConsume()` and `refill()` are `synchronized` on the bucket instance. Each client key gets its own bucket in a `ConcurrentHashMap`, so different clients don't block each other.

---

### Q14. Why is a fixed-window counter a bad approach?
**Answer:** Client can send 100 requests at 11:59:59 and 100 at 12:00:00 — 200 requests in 2 seconds while "staying under 100/minute." Called the **boundary burst problem**.

---

### Q15. What is a sliding window and how is it different from token bucket?
**Answer:**
- **Sliding window** — count requests in the last N seconds exactly; reject if count >= limit
- **Token bucket** — allows bursts up to capacity, then enforces refill rate
- Sliding window is more precise; token bucket is cheaper (O(1) vs tracking timestamps)

---

### Q16. How does your sliding window implementation work?
**Answer (in-memory):** Store timestamps in a deque per key. On each request, remove timestamps older than `window-seconds`, count remaining. If under limit, add current timestamp and allow. Else reject with retry time = when oldest entry expires.

**Answer (Redis):** ZSET with timestamp as score. Lua script atomically removes expired entries, counts, and adds new member if under limit.

---

### Q17. What is returned when a request is rate limited?
**Answer:**
```json
HTTP 429
Retry-After: 1
{"error":"rate_limit_exceeded","message":"Too many requests...","retryAfterSeconds":1}
```

---

### Q18. How do you configure which strategy to use?
**Answer:** `application.yml`:
```yaml
ratelimiter:
  strategy: token-bucket    # or sliding-window
  backend: in-memory        # or redis
```
Spring `@ConditionalOnProperty` wires the correct bean at startup.

---

### Q19. What unit tests do you have?
**Answer:**
- `TokenBucketTest` — burst capacity, rejection, refill over time, capacity cap, invalid args
- `InMemorySlidingWindowRateLimiterTest` — window limit, key isolation, window slide, invalid args

---

### Q20. What is the Big-O complexity of each strategy?
**Answer:**
| Strategy | Time | Space per key |
|----------|------|---------------|
| Token bucket (in-memory) | O(1) | O(1) |
| Sliding window (in-memory) | O(k) eviction | O(k) k=requests in window |
| Token bucket (Redis) | O(1) network + O(1) Lua | O(1) in Redis |
| Sliding window (Redis) | O(log n) ZSET ops | O(k) in Redis |

---

## Level 3 — System Design (Senior / Design Round)

### Q21. Design a rate limiter for a distributed system with 10 app servers.
**Answer points:**
1. Centralized store (Redis) — single source of truth
2. Atomic operations (Lua script) — prevent race conditions
3. Key by client ID / API key / IP
4. Token bucket or sliding window algorithm
5. Return 429 + Retry-After at API gateway or app layer
6. Consider Redis cluster for HA
7. Metrics: rejection rate, top offenders, latency impact

*Your project implements exactly this with Redis + Lua.*

---

### Q22. Why can't you use in-memory rate limiting with multiple server instances?
**Answer:** Each instance maintains its own counters. A client routed to different instances gets a separate budget. With 3 replicas and limit=100/min, client effectively gets 300/min.

---

### Q23. Why use a Lua script in Redis instead of separate GET/SET commands?
**Answer:** Race condition example:
```
Instance A: GET tokens → 1
Instance B: GET tokens → 1
Instance A: SET tokens → 0  (allowed)
Instance B: SET tokens → 0  (allowed)
Both allowed but only 1 token existed!
```
Lua executes atomically on Redis server — no interleaving.

---

### Q24. Compare token bucket, leaky bucket, fixed window, and sliding window.
**Answer:**

| Algorithm | Burst allowed? | Boundary problem? | Memory | Precision |
|-----------|---------------|-------------------|--------|-----------|
| Fixed window | Yes (at boundary) | Yes | O(1) | Low |
| Sliding window | No | No | O(k) | High |
| Token bucket | Yes (controlled) | No | O(1) | Medium |
| Leaky bucket | No | No | O(1) | Medium |

---

### Q25. How would you rate limit at different granularities (global, per-user, per-endpoint)?
**Answer:**
- Composite keys: `"user:{id}:endpoint:{path}"`
- Multiple limiters in chain (interceptor checks all)
- Hierarchical: global cap → per-tenant cap → per-user cap
- Gateway rules per route (e.g. `/login` stricter than `/health`)

---

### Q26. What happens if Redis goes down in production?
**Answer options:**
- **Fail-open** — allow all traffic (availability over protection). Risk: abuse during outage.
- **Fail-closed** — reject all (protection over availability). Risk: outage takes down API.
- **Local fallback** — degrade to in-memory (partial protection).
- Best practice: circuit breaker + monitoring + configurable policy per API criticality.

---

### Q27. How would you handle clients behind NAT (same IP, many users)?
**Answer:**
- IP-only limiting is insufficient for shared IPs (offices, mobile carriers)
- Require authentication → rate limit by user ID or API key
- Your project already prefers `X-API-Key` over IP
- Combine: anonymous = IP limit (lower), authenticated = per-user limit (higher)

---

### Q28. How do you prevent clients from spoofing X-Forwarded-For to bypass IP limits?
**Answer:**
- Only trust `X-Forwarded-For` from known reverse proxies/load balancers
- Strip/ignore forwarded headers from direct client connections
- Prefer API key / JWT subject for authenticated rate limiting
- At gateway level, extract real IP from TCP connection, not headers

---

### Q29. How would you implement tiered rate limits (free vs premium users)?
**Answer:**
```yaml
# Conceptual
tiers:
  free:     { capacity: 10,  refill-rate: 1 }
  premium:  { capacity: 100, refill-rate: 10 }
```
- Resolve tier from API key metadata (DB/Redis lookup)
- Create bucket with tier-specific params
- Cache tier config; avoid DB lookup on every request

---

### Q30. Where should rate limiting live — app, API gateway, or CDN?
**Answer:**

| Layer | Pros | Cons |
|-------|------|------|
| CDN (Cloudflare) | Blocks traffic earliest | Coarse rules, limited customization |
| API Gateway (Kong/NGINX) | Centralized, no app changes | Less business-context aware |
| App (your project) | Full context (user tier, endpoint) | Runs after request reaches app |

**Best practice:** coarse limits at edge/gateway, fine-grained business rules in app.

---

## Level 4 — Advanced / Staff Engineer

### Q31. How would you make rate limits dynamic (change without redeploy)?
**Answer:**
- Store limits in Redis/DB/config service (Consul, Spring Cloud Config)
- Refresh config on interval or via pub/sub
- Admin API to update limits per tenant
- Feature flags for emergency loosening/tightening

---

### Q32. Design rate limiting for 1 million requests per second.
**Answer points:**
1. **Edge first** — CDN/WAF absorbs bulk
2. **Sharding** — partition Redis by key hash across cluster
3. **Local + global hybrid** — local token bucket handles 90% without Redis call; sync periodically
4. **Approximate counting** — Redis Cell, Count-Min Sketch for very high throughput
5. **Async rejection** — don't block hot path on limit check failure logging
6. **Dedicated rate limit service** — not embedded in app

---

### Q33. Explain the "local token bucket + global Redis sync" optimization.
**Answer:**
- Each instance maintains a local bucket with a fraction of the global budget
- Most requests checked locally (no network)
- Periodically sync with Redis or use "loan" tokens from global pool
- Trade-off: slight over-admission during sync lag vs massive latency reduction
- Used by high-scale systems (Envoy, some API gateways)

---

### Q34. How would you test rate limiting correctness under concurrent load?
**Answer:**
- JMeter/Gatling with N threads hitting same key
- Assert total allowed requests ≤ limit (within tolerance)
- Integration test with Testcontainers Redis
- Chaos test: kill Redis mid-test, verify fallback behavior
- Property-based testing for token bucket invariants

---

### Q35. What metrics and alerts would you add?
**Answer:**
- `rate_limit_rejections_total{key_prefix, endpoint}` — counter
- `rate_limit_check_duration_seconds` — histogram
- `rate_limit_redis_errors_total` — counter
- Alert: rejection rate > 10% for 5 min
- Alert: Redis unreachable
- Dashboard: top rejected keys, rejection rate over time

---

### Q36. Compare Redis INCR+EXPIRE vs Lua script vs Redis Cell module.
**Answer:**
- **INCR+EXPIRE** — simple fixed window, not atomic together (race on first request), boundary burst problem
- **Lua script** — fully atomic, custom logic, your project uses this
- **Redis Cell (CL.THROTTLE)** — native token bucket in Redis module, very efficient, less portable
- **Redisson RRateLimiter** — Java library wrapping Redis, production-ready but adds dependency

---

### Q37. How does NGINX rate limiting work and how does it compare to your project?
**Answer:**
- NGINX uses leaky bucket algorithm (`limit_req_zone`)
- Config: `rate=10r/s`, `burst=20`
- Runs at edge, very fast (C, no JVM)
- Your project: more flexible (sliding window, per-API-key, custom JSON responses, business logic)
- Complementary: NGINX for coarse protection, your app for fine-grained rules

---

### Q38. What are the CAP trade-offs for a distributed rate limiter?
**Answer:**
- Rate limiting needs **consistency** (can't over-admit) → prefer CP over AP
- Redis single primary: strong consistency for Lua scripts
- Redis cluster: partition → some keys unavailable → fail-open or fail-closed decision
- Multi-region: global limit requires cross-region Redis (latency) or regional limits with global cap

---

### Q39. How would you integrate this into a microservices architecture?
**Answer:**
1. **API Gateway** — embed rate limiter as a filter (Spring Cloud Gateway / Kong plugin)
2. **Shared Redis** — all services/gateway share limit state
3. **Per-service limits** — different limits per downstream service sensitivity
4. **Your project** — designed to plug into gateway; `RateLimiterStrategy` is framework-agnostic core

---

### Q40. Implement a rate limiter in an interview — what do interviewers expect?
**Answer checklist:**
1. Clarify requirements (limit, window, distributed?, key?)
2. Choose algorithm and justify (token bucket is safe default)
3. Define data structure (hash map key → counter/bucket)
4. Handle concurrency (locks, atomic ops, Redis Lua)
5. Discuss scale (single vs distributed)
6. Mention HTTP 429 + Retry-After
7. State trade-offs unprompted
8. Write clean code for core `tryConsume()` logic

---

## Coding Questions (Whiteboard / Live Coding)

### Q41. Write pseudocode for token bucket `tryConsume()`.
```
function tryConsume():
    now = currentTime()
    elapsed = now - lastRefillTime
    tokensToAdd = elapsed * refillRate
    tokens = min(capacity, tokens + tokensToAdd)
    lastRefillTime = now

    if tokens >= 1:
        tokens -= 1
        return ALLOWED
    return REJECTED
```

---

### Q42. Write pseudocode for sliding window `tryConsume()`.
```
function tryConsume(key):
    now = currentTime()
    window = requestLog[key]
    remove all timestamps where ts <= now - windowSize
    if len(window) < maxRequests:
        window.add(now)
        return ALLOWED
    retryAfter = window.oldest + windowSize - now
    return REJECTED(retryAfter)
```

---

### Q43. How would you implement a fixed-window counter in Redis?
```
MULTI
INCR rate:{key}:{currentMinute}
EXPIRE rate:{key}:{currentMinute} 60
EXEC
if count > limit: REJECT
```
Mention boundary burst problem as follow-up.

---

### Q44. How would you add a `/api/status` endpoint showing remaining quota?
**Answer:** Extend `RateLimiterStrategy` with `getRemainingQuota(key)`. Token bucket returns `availableTokens`. Sliding window returns `maxRequests - currentCount`. Expose via controller (careful: don't leak other users' quotas).

---

## Behavioral / Project Questions

### Q45. What was the hardest part of this project?
**Suggested answer:** Getting distributed correctness with Redis Lua — understanding why separate GET/SET calls fail under concurrency and ensuring the script handles initialization, refill, TTL, and rejection in one atomic operation.

---

### Q46. What would you do differently if you started over?
**Suggested answer:**
- Start with the `RateLimiterStrategy` interface on day 1
- Add integration tests with Testcontainers Redis earlier
- Add Micrometer metrics from the start
- Consider Spring Cloud Gateway filter as the primary integration point

---

### Q47. How long did this take and how did you build it incrementally?
**Suggested answer:** 4-day incremental build:
- Day 1: Core algorithm + unit tests (plain Java)
- Day 2: Spring interceptor + HTTP 429
- Day 3: Redis distributed backend + Lua
- Day 4: Sliding window + config-driven strategy selection

---

### Q48. How does this project relate to system design interviews?
**Suggested answer:** "Design a rate limiter" is one of the most common system design questions (alongside URL shortener, chat, news feed). This project lets me speak from implementation experience, not just theory — I can discuss algorithm trade-offs, distributed state, atomicity, and operational concerns with concrete code examples.

---

## Quick Reference Cheat Sheet

| Topic | Key phrase |
|-------|-----------|
| Default algorithm | Token bucket |
| Distributed store | Redis + Lua script |
| HTTP response | 429 + Retry-After |
| Keying | API key > IP |
| Pattern | Strategy pattern |
| Burst problem | Fixed window boundary |
| Scale problem | In-memory × N replicas |
| Atomicity | Lua script in Redis |
| Config | application.yml strategy/backend |
| Complexity | Token bucket O(1), sliding window O(k) |
