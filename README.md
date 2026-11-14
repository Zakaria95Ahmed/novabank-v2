# NovaBank v2

Event-driven banking microservices on Spring Boot (Java 21), Kafka, PostgreSQL and Docker.

> v2 is the hardened evolution of the v1 learning project. Roadmap: [`docs/ROADMAP.md`](docs/ROADMAP.md). Full specification: [`docs/UPGRADE-SPEC.md`](docs/UPGRADE-SPEC.md).

## Services

| Service | Port | Role |
|---|---|---|
| discovery-service | 8761 | Eureka service registry |
| api-gateway | 8080 | Single entry point (JWT validation, routing) |
| user-account-service | 8081 | Users, roles, accounts, JWT issuing |
| transaction-service | 8082 | Deposits and transfers |
| notification-service | 8083 | Email notifications from Kafka events |

## Repository layout

```text
libs/novabank-contracts   shared events, enums, topic names
services/*                deployable services
deploy/                   docker-compose and ops files
docs/                     specification, roadmap, ADRs
```

## Prerequisites

- JDK 21, Maven 3.9+
- Docker Desktop (Compose v2)

## Build

```bash
mvn -B -DskipTests verify
```

## Run

1. Create your environment file (never commit it):
   ```bash
   cp deploy/.env.example deploy/.env
   ```
   Edit `deploy/.env` with real values.
2. **Everything in Docker:**
   ```bash
   docker compose -f deploy/docker-compose.yml up -d --build
   ```
3. **Services from IntelliJ** (infrastructure in Docker only):
   ```bash
   docker compose -f deploy/docker-compose.yml up -d postgres kafka
   ```
   Then run the services with the working directory set to the repository root.

> Phase P0 note: the `roles` table starts empty. Insert the roles once:
> `INSERT INTO roles (name) VALUES ('ADMIN'), ('CUSTOMER');`
> (automated by Flyway in phase P2).

## Build one image

```bash
docker build -t novabank-api-gateway --build-arg SERVICE=api-gateway --build-arg PORT=8080 .
```

## Contributing

One task = one branch = one pull request. Commit messages follow Conventional Commits (`feat(account): ...`). See `.github/pull_request_template.md`.
