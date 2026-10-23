# Pečiatka Stamps API — Java / Spring Boot

[![CI](https://github.com/h010grams/peciatka-java/actions/workflows/ci.yml/badge.svg)](https://github.com/h010grams/peciatka-java/actions/workflows/ci.yml)

The core of [Pečiatka](https://github.com/h010grams/peciatka) — a digital loyalty card for small beauty salons — re-implemented in **Java 21 and Spring Boot 4**.

The original product is written in TypeScript (Node.js + React). This project ports its central business logic to the Java ecosystem: the same rules, the same edge cases, the same concurrency guarantees — so the two implementations can be compared side by side.

## What it does

A REST API for the salon counter:

- create a loyalty program — *N stamps → a reward*, at most one stamp per *X* hours,
- register a client by phone (the same phone always returns the same card),
- add a stamp; the N-th stamp earns a reward and starts a new cycle, rewards accumulate,
- redeem the oldest available reward,
- undo a mistaken stamp within 24 hours — if that stamp earned a reward, the reward is voided; if the reward was already used, the undo is refused.

| Method | Path | |
|---|---|---|
| `POST` | `/api/programs` | create a program |
| `POST` | `/api/cards` | register a client (201 new, 200 existing) |
| `GET` | `/api/cards/{id}` | card state, next allowed stamp time, whether undo is possible |
| `POST` | `/api/cards/{id}/stamps` | add a stamp (409 `cooldown` with `retryAt`) |
| `POST` | `/api/cards/{id}/rewards/redeem` | redeem a reward (409 `no_reward`) |
| `POST` | `/api/cards/{id}/stamps/revoke-last` | undo the last stamp |

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details with a machine-readable `code`.

## Design decisions

- **Pure domain rules.** Cooldown, rewards and undo live in `StampingRules` — static functions over an immutable `Counters` record, unit-tested without Spring or a database.
- **No double stamps.** Every write starts with `@Lock(PESSIMISTIC_WRITE)` on the card row (`SELECT … FOR UPDATE`). Two staff members scanning the same card at the same moment are serialized; the second sees the first stamp and hits the cooldown. `ConcurrentStampTest` fires four simultaneous requests and expects exactly one stamp.
- **Unambiguous event order.** Stamps are an append-only event log (`ADD` / `REVOKE`). Ordering by timestamp alone turned out to be unsafe — two stamps can share a millisecond — so each event gets a per-card sequence number, assigned under the row lock. The tests caught this.
- **Testable time.** The current time comes from an injected `java.time.Clock`; tests move a `MutableClock` forward instead of sleeping.
- **Denormalized counters + history.** Card counters are stored for fast reads; the full history stays in `stamp_events` and `rewards`.

## Tech

Java 21 · Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator) · Hibernate · PostgreSQL / H2 · JUnit 5 · AssertJ · MockMvc · Maven · Docker · GitHub Actions

## Running

Requires JDK 21. Maven is downloaded by the wrapper.

```bash
./mvnw test              # 25 tests: domain rules, API, concurrency
./mvnw spring-boot:run   # API on http://localhost:8080 with in-memory H2
```

With PostgreSQL:

```bash
SPRING_PROFILES_ACTIVE=postgres DATABASE_URL=jdbc:postgresql://localhost:5432/peciatka ./mvnw spring-boot:run
```

With Docker:

```bash
docker build -t peciatka-java .
docker run -p 8080:8080 peciatka-java
```

Example:

```bash
curl -s -X POST localhost:8080/api/programs -H 'Content-Type: application/json' \
  -d '{"name":"Loyalty card","stampsRequired":10,"rewardText":"15% off","cooldownHours":12}'
```

## Scope

This is a focused port of the domain, not the full product: no authentication, multi-tenancy or Wallet integrations — those live in the [TypeScript version](https://github.com/h010grams/peciatka). The schema is created by Hibernate (`ddl-auto=update`); a production setup would use Flyway migrations.

## Author

**Nikita Kolotusha** — [GitHub @h010grams](https://github.com/h010grams) · [LinkedIn](https://www.linkedin.com/in/nikita-kolotusha-307902419/)
