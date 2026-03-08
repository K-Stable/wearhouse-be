# Auth + Passport Strategy Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement buyer/seller separated signup/login with JWT + Redis refresh tokens, gateway passport authentication/relay, and common `@CurrentUser` foundation.

**Architecture:** Client sends access JWT cookie (never passport). API Gateway authenticates request, obtains passport via auth validate (with short cache), performs route authorization, then relays signed passport headers to downstream services. Downstream services verify passport and expose principal via `@CurrentUser` (annotation + resolver only; no API attachment yet).

**Tech Stack:** Spring Boot 3.5, Spring Security, Spring Cloud Gateway Server MVC, Redis, MySQL, JWT (JJWT), Flyway.

---

## Scope Lock

- Buyer/Seller account tables and APIs are separated.
- Local URL strategy uses subdomain-style split in config values.
- Gateway handles authentication + passport relay.
- Services keep authorization capability via security context + `@CurrentUser` infra only.

## Implementation Tasks

### Task 1: Common current user foundation
- Create `@CurrentUser` annotation and principal model in `common`.
- Add argument resolver + web config in `common`.
- No controller usage change in this task.

### Task 2: Auth domain and persistence
- Add auth Flyway migrations for buyer/seller accounts and refresh token metadata.
- Add JPA/JDBC repositories for buyer/seller users.
- Add password encoder, JWT provider, refresh token Redis store.

### Task 3: Auth APIs
- Implement separated APIs:
  - `/api/v1/auth/buyers/signup`, `/login`, `/refresh`, `/logout`
  - `/api/v1/auth/sellers/signup`, `/login`, `/refresh`, `/logout`
- Implement internal validate API for gateway:
  - `/api/v1/internal/auth/validate`
- Set access token cookies and refresh token cookies for local testing.

### Task 4: Gateway security + passport
- Add Spring Security config and CORS alignment.
- Implement filters/services:
  - JWT extraction/validation entry filter
  - passport authorization filter
  - passport relay filter
- Ensure client-provided `X-Passport-*` is removed.
- Relay `X-Passport-User`, `X-Passport-Sig`, `X-Passport-Ts`.

### Task 5: Service-side passport parsing + current user binding
- Add common service security config usable by order/inventory/payment.
- Add passport header parser + signature verifier.
- Bind principal to security context so `@CurrentUser` works.

### Task 6: Local config and docs
- Add required env/config defaults for local URLs and secrets.
- Document local test flow (signup/login/api call) in docs.

### Task 7: Verification
- Run focused tests:
  - auth module tests (signup/login/refresh/validate)
  - gateway tests (filter behavior, header strip/relay)
- Run build/tests for touched modules.

