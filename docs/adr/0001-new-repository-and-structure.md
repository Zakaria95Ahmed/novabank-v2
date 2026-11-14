# ADR-0001: New independent repository and monorepo structure

- Status: accepted
- Date: 2026-11-11

## Context
v1 is a learning-oriented monorepo. The upgrade (see `docs/UPGRADE-SPEC.md`) changes security, money-handling, API shape and infrastructure.

## Decision
- Create an independent repository `novabank-v2` and keep v1 untouched for reference (tag `v1-baseline` marks the imported state).
- One Maven reactor: `libs/` (shared contracts), `services/` (deployable services), `deploy/` (Compose and ops files), `docs/`.
- One parent POM defines versions and plugin configuration; one Dockerfile builds any service via `ARG SERVICE`.

## Consequences
- Single CI pipeline and single place for dependency versions.
- Services remain independently buildable and deployable (one image per service).
- Breaking API changes versus v1 are expected and recorded in `CHANGELOG.md`.
