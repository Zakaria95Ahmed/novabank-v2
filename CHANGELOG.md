# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Security
- SEC-02 (user-account-service): actuator moved to an internal management port (9081) and limited to `health,info`; `/actuator/**` is no longer `permitAll` on the API port.
- SEC-07 (user-account-service): a disabled user is no longer authenticated by a still-valid token; a token whose user no longer exists no longer causes a 500.
- SEC-03: only the API gateway publishes a port in `deploy/docker-compose.yml`. Internal ports are published only by `deploy/docker-compose.dev.yml` (development).

### Added
- P0: new repository structure (`libs/`, `services/`, `deploy/`, `docs/`).
- P0: parent POM with shared versions, plugin management and Java 21 enforcement.
- P0: `novabank-contracts` module skeleton (topic names).
- P0: single parameterized `Dockerfile` (JRE runtime, non-root user).
- P0: `deploy/docker-compose.yml` using YAML anchors.
- P0: basic CI (build + Docker build matrix) and Dependabot.
- SEC-03: `deploy/docker-compose.dev.yml` development override.

### Changed
- Maven `groupId` is now `com.novabank` and version `2.0.0-SNAPSHOT` (Java packages are unchanged until the optional package rename).
- Java target: 21.
- Management (actuator) port convention: service port + 1000 (account 9081, transaction 9082, notification 9083, gateway 9080, discovery 9761).
- Running services from IntelliJ now needs the dev override for the infrastructure:
  `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.dev.yml up -d postgres kafka`.

## [v1-baseline]
- Imported from the v1 monorepo (`novabank-MS`) without functional changes.
