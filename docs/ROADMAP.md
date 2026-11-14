# NovaBank v2 Roadmap (live checklist)

Source of truth for details and acceptance criteria: [`UPGRADE-SPEC.md`](UPGRADE-SPEC.md).
Rule: do not start a phase before the acceptance criteria of the previous one are met.

## P0: Repository foundation
- [x] P0-1 Create repo `novabank-v2` (Private first)
- [x] P0-2 Import v1 sources (script) and tag `v1-baseline`
- [x] P0-3 New structure: `libs/`, `services/`, `deploy/`, `docs/`
- [x] P0-4 Parent POM + modules + `relativePath ../../pom.xml`
- [x] P0-5 Repo files: `.gitignore`, `.gitattributes`, `.editorconfig`, `CODEOWNERS`, PR template, Dependabot
- [x] P0-6 Basic CI (build + Docker build matrix)
- [ ] P0-7 (optional, separate commit) rename `com.example.*` to `com.novabank.*`
- [ ] **Acceptance:** `mvn -B verify` green locally and on GitHub; `docker compose -f deploy/docker-compose.yml build` works

## P1: Quick security wins
- [ ] SEC-01 Public registration always creates `CUSTOMER`; safe admin bootstrap
- [ ] SEC-02 Actuator: `health,info,prometheus` on internal management port 9090
- [ ] SEC-03 Only the gateway port is published; `docker-compose.dev.yml` for dev ports
- [ ] SEC-06 Login rate limiting (Redis `RequestRateLimiter`)

## P2: Database
- [ ] DB-01 Flyway + `ddl-auto=validate` + roles seed
- [ ] SEC-05 One database user per service (`init-db.sh`)

## P3: Money safety
- [ ] ARCH-01 `novabank-contracts` events and enums (moved here from P4)
- [ ] FIN-03 Outbox
- [ ] FIN-01 Idempotency (client key + idempotent consumers)
- [ ] FIN-02 Optimistic locking and `balance >= 0`
- [ ] FIN-04 Saga with compensation
- [ ] FIN-05 Authorization: customers touch their own accounts only
- [ ] QA-01 Unit + Testcontainers tests, enabled in CI

## P4: Contracts, API, configuration
- [ ] API-01 `/api/v1`, ProblemDetail, pagination, OpenAPI
- [ ] ARCH-02 Profiles (`local`, `docker`) + `@ConfigurationProperties`

## P5: JWT RS256
- [ ] SEC-04 Private key only in Account; Resource Server elsewhere

## P6: Resilience
- [ ] RES-01 Feign timeouts + circuit breaker
- [ ] RES-02 Kafka retry + Dead Letter Topics

## P7: Observability
- [ ] OBS-01 Logging cleanup
- [ ] OBS-02 Tracing + correlation id
- [ ] OBS-03 Prometheus + Grafana (Compose profile)

## P8: Docker hardening and CI/CD
- [ ] OPS-01 Pinned images, healthchecks, memory limits
- [ ] OPS-02 Release workflow (GHCR), Dependabot, deployment job
