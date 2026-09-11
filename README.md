# RailPredictor

A rule-based, simulation-driven Train ETA Prediction System. See
[docs/architecture.md](docs/architecture.md) for the intended design.

Being built phase by phase; see "Current phase" below for what actually
exists right now.

## Prerequisites

- Java 21 (JDK)
- No local Maven install needed — this project uses the Maven Wrapper
  (`mvnw`), which downloads the right Maven version automatically.

## Build and test

```bash
./mvnw clean verify
```

On Windows (PowerShell/cmd):

```bash
mvnw.cmd clean verify
```

## Run

```bash
./mvnw spring-boot:run
```

## Current phase

**Phase 1 — Project Foundation.** The project is a compilable, empty Spring
Boot 3 skeleton: package structure, base configuration, and a placeholder
context-loads test. No RailRadar integration, prediction logic, or REST
endpoints exist yet.
