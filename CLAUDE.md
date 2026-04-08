# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot SNS (Social Networking Service) sample project — a REST API built with Spring Boot 4.0.5 and Java 26.

## Build & Test Commands

```bash
./gradlew build          # Build the project
./gradlew test           # Run all tests
./gradlew bootRun        # Run the application
./gradlew test --tests "com.lecture.spring_sns_sample_project.SomeTest"  # Run a single test class
```

## Tech Stack

- **Java 26**, Gradle (Kotlin DSL)
- **Spring Boot 4.0.5** (Web MVC, Data JPA)
- **H2** (dev/test), **PostgreSQL** (production)
- **JUnit 5** for testing

## Architecture

Layered architecture with domain-driven packaging:

```
com.lecture.spring_sns_sample_project
├── controller/              # @RestController classes
│   └── dto/                 # Java records for request/response DTOs
├── domain/{domainName}/     # One package per domain aggregate
│   ├── {Domain}.java        # JPA entity
│   ├── {Domain}Repository.java
│   ├── {Domain}Service.java
│   └── {Domain}Exception.java
└── config/                  # @Configuration classes
```

## Key Conventions

- **DI:** Constructor injection via `@RequiredArgsConstructor` — no field `@Autowired`
- **Controllers:** `@RestController`, no class-level `@RequestMapping`, return `ResponseEntity<T>`
- **DTOs:** Java records with `toEntity()` on requests and `from(Entity)` on responses
- **Entities:** `protected` no-arg constructor, `GenerationType.IDENTITY`, `FetchType.LAZY` for associations, no FK constraints
- **Services:** `@Transactional` only for multi-write or dirty-checking scenarios — not for single repo calls or simple reads
- **Exceptions:** Domain-specific exception classes, global handling via `@RestControllerAdvice`
- **API testing:** Shell scripts with curl commands go in `src/main/resources/http/`
