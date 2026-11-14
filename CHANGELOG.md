# Changelog

All notable changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Added
- P0: new repository structure (`libs/`, `services/`, `deploy/`, `docs/`).
- P0: parent POM with shared versions, plugin management and Java 21 enforcement.
- P0: `novabank-contracts` module skeleton (topic names).
- P0: single parameterized `Dockerfile` (JRE runtime, non-root user).
- P0: `deploy/docker-compose.yml` using YAML anchors.
- P0: basic CI (build + Docker build matrix) and Dependabot.

### Changed
- Maven `groupId` is now `com.novabank` and version `2.0.0-SNAPSHOT` (Java packages are unchanged until the optional package rename).
- Java target: 21.

## [v1-baseline]
- Imported from the v1 monorepo (`novabank-MS`) without functional changes.
