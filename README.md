# ClickKart API Gateway

The platform's single internet-facing entry point. Every client request enters here; no other
service is directly reachable from outside the cluster. Item **#3** in the ClickKart build order.

- **Port:** `8080`
- **Stack:** Spring Cloud Gateway (WebFlux — reactive, not Spring MVC)
- **Datastore:** Redis (rate-limit counters + revoked-JWT store)

## Responsibilities

| Concern | How |
|---|---|
| Routing | `lb://` load-balanced routes resolved through Eureka |
| Edge authentication | Validates the JWT signature locally, then forwards identity downstream |
| Token revocation | Checks a Redis `revoked:jti:<id>` key so logout takes effect immediately |
| Rate limiting | Redis-backed `RequestRateLimiter`, per authenticated user or per IP |
| CORS | Single global policy; origins injected per environment, never `*` |
| API docs | Aggregated Swagger UI across all downstream services |

On successful authentication the Gateway forwards `X-User-Id`, `X-User-Roles`, and
`X-Correlation-Id` downstream, so services never re-validate the token themselves.

## Public paths

These bypass JWT validation (`gateway.security.public-paths` in the config repo):

```
/api/v1/auth/login          /api/v1/auth/register        /api/v1/auth/refresh
/api/v1/auth/forgot-password /api/v1/auth/reset-password
/api/v1/auth/otp/request    /api/v1/auth/otp/verify
/api/v1/captcha/challenge   /actuator/health
/docs/**  /swagger-ui.html  /swagger-ui/**  /webjars/**
```

Note `/api/v1/captcha/verify` is deliberately **not** routed here — it is server-to-server only,
called by Auth Service's Feign client.

## Aggregated API documentation

<http://localhost:8080/swagger-ui.html> presents a dropdown across every downstream service. The
Gateway generates no OpenAPI spec of its own (it has no business endpoints); it proxies each
service's real spec via a dedicated route:

```
/docs/auth-service/v3/api-docs          /docs/notification-service/v3/api-docs
/docs/audit-log-service/v3/api-docs     /docs/captcha-service/v3/api-docs
```

## Failure behaviour

Redis is a **required** dependency, not best-effort. Without it there is no way to honour a prior
logout, so every access token would become un-revocable until natural expiry. On a Redis outage
the Gateway returns a clean `503`, and `/actuator/health/readiness` reports `DOWN` so Kubernetes
takes the pod out of rotation. Command and connect timeouts are pinned to `2s` — Lettuce's 60s
default made outages hang instead of failing fast.

## Configuration

Pulled from the config repo at startup. Key variables:

| Variable | Required in | Notes |
|---|---|---|
| `JWT_SECRET` | all | **Must byte-for-byte match Auth Service's** or every token fails |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | prod | Managed cache endpoint |
| `GATEWAY_ADMIN_USERNAME` / `_PASSWORD` | test/qa/prod | Actuator access |
| `EUREKA_DASHBOARD_USERNAME` / `_PASSWORD` | test/qa/prod | |
| `ALLOWED_ORIGINS` | qa/prod | Real frontend origin; no default |
| `RATE_LIMIT_REPLENISH` / `RATE_LIMIT_BURST` | — | Defaults 50/100 (dev), 100/200 (prod) |

## Running locally

```bash
docker compose -f docker-compose.dev-infra.yml -f docker-compose.app-tier.yml up -d
curl http://localhost:8080/actuator/health
```

## Build

```bash
mvn -B verify
```

## Related

- [clickkart-platform](https://github.com/kripals1199/clickkart-platform) — architecture, local setup
- [clickkart-auth-service](https://github.com/kripals1199/clickkart-auth-service) — mints the JWTs validated here
