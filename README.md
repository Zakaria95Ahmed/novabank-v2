# NovaBank v2

Event-driven banking microservices on Spring Boot (Java 21), Kafka, PostgreSQL and Docker.

> v2 is the hardened evolution of the v1 learning project. Roadmap: [`docs/ROADMAP.md`](docs/ROADMAP.md). Full specification: [`docs/UPGRADE-SPEC.md`](docs/UPGRADE-SPEC.md).

## Services

| Service | API port | Management port (internal) | Role |
|---|---|---|---|
| discovery-service | 8761 | 9761 | Eureka service registry |
| api-gateway | 8080 | 9080 | Single entry point (JWT validation, routing) |
| user-account-service | 8081 | 9081 | Users, roles, accounts, JWT issuing |
| transaction-service | 8082 | 9082 | Deposits and transfers |
| notification-service | 8083 | 9083 | Email notifications from Kafka events |

The management (actuator) port is the service port + 1000. It is never published to the host.

## Repository layout

```text
libs/novabank-contracts   shared events, enums, topic names
services/*                deployable services
deploy/                   docker-compose files and ops files
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

2. **Everything in Docker** (production-like): only the gateway is reachable from your machine.
   ```bash
   docker compose -f deploy/docker-compose.yml up -d --build
   # API: http://localhost:8080
   ```

3. **Services from IntelliJ** (infrastructure in Docker, internal ports published for development):
   ```bash
   docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml up -d postgres kafka
   ```
   Then run the services with the working directory set to the repository root.
   PowerShell tip, so you can type plain `docker compose ...` in the current session:
   ```powershell
   $env:COMPOSE_FILE = "deploy/docker-compose.yml;deploy/docker-compose.dev.yml"
   ```

> Phase note: the `roles` table starts empty. Insert the roles once:
> `INSERT INTO roles (name) VALUES ('ADMIN'), ('CUSTOMER');`
> (automated by Flyway in phase P2).

## Build one image

```bash
docker build -t novabank-api-gateway --build-arg SERVICE=api-gateway --build-arg PORT=8080 .
```

## Contributing

One task = one branch = one pull request. Commit messages follow Conventional Commits (`feat(account): ...`). See `.github/pull_request_template.md`.
