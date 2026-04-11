# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot SNS (Social Networking Service) sample project. Monorepo:
- **Backend** (`/`): REST API — Spring Boot 4.0.5 + Java 26
- **Frontend** (`frontend/`): Next.js 15+ App Router + React 19 + TypeScript (Threads/Instagram/Twitter 스타일 UI)

## Code Rules

- **Backend rules**: `.claude/skills/spring-api-rules/SKILL.md` — REST API, 도메인 패키징, 검증, 페이징, 공통 예외
- **Frontend rules**: `.claude/skills/nextjs-frontend-rules/SKILL.md` — Next.js App Router, 컴포넌트, TanStack Query, Tailwind, shadcn/ui, 폼 검증
- **Code review**: `.claude/agents/code-reviewer.md` — 위 두 SKILL.md 를 자동 참조

## Design Documents

- **Backend design**: `docs/backend-design.md` — 패키지 구조, 도메인 모델, API 계약, 인증, 검증 전략
- **Frontend design**: `docs/frontend-design.md` — 디렉토리, 데이터 흐름, rewrites 전략, Next.js 16 변경점
- **Auth design**: `docs/auth-design.md` — Session-Primary / JWT-Auxiliary 설계, 위협 모델, 단계별 도입 로드맵

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
