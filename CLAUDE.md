# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run locally (Java 21 required)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run full stack with Docker
docker compose up --build

# Build JAR
./mvnw clean package -DskipTests

# Run all tests (requires Docker for Testcontainers)
./mvnw test
```

Tests use Testcontainers to spin up a real PostGIS 16 container — Docker must be running.

## Architecture

Spring Boot 3.3.5 modular monolith on Java 21. Peer-to-peer garage storage marketplace for Tbilisi, Georgia.

**Base package:** `li.adgi.api`  
**API base path:** `/api/v1/`

### Modules

| Module | Responsibility |
|--------|---------------|
| `auth/` | Register, login, refresh tokens, phone/email OTP verification |
| `user/` | User profiles, host/renter profile management |
| `listing/` | Garage/storage listings with status state machine and moderation |
| `search/` | PostGIS-powered geographic radius search with filters |
| `verification/` | Identity verification (ID/passport/selfie upload and review) |
| `admin/` | Admin dashboard: user suspend, listing moderation, audit log viewing |
| `notification/` | EMAIL/SMS/IN_APP notifications (stubbed in dev/prod by default) |
| `audit/` | Audit log writes via `REQUIRES_NEW` transactions |
| `security/` | JWT filter, `JwtService`, `SecurityConfig` |
| `config/` | `FeatureFlags`, `JwtProperties`, `DevDataSeeder`, `OpenApiConfig` |
| `common/` | `BaseEntity`, `ApiException` hierarchy, `GlobalExceptionHandler`, `GeoUtil`, `PageResponse` |

### Layered pattern
Controllers → Services → Repositories (JPA). DTO mapping is mostly manual.

### Key design decisions

- **Location privacy:** Listing coordinates are jittered by up to 300m before storing `approximate_location` (used for public search). The `exact_location` is only revealed post-booking. See `GeoUtil` and `ListingService`.
- **Geographic queries:** PostGIS `ST_DWithin` + `ST_Distance` with GIST index on `approximate_location` (geography type, SRID 4326). Search queries are native SQL in `SearchService`.
- **Redis optional:** `TokenStore` and `OtpStore` fall back to in-memory maps if Redis is unavailable. Dev OTP stub code is `000000`.
- **Audit writes:** `AuditService` uses `@Transactional(propagation=REQUIRES_NEW)` so audit records persist even when the outer transaction rolls back.
- **Feature flags:** `FeatureFlags` bean controls email-stub, sms-stub, identity-auto-approve, and payments-stub. All are on by default — flip to `false` to wire real providers.

### What's NOT implemented yet
V3 and V4 Flyway migrations created tables for bookings, payments, messaging, reviews, and support tickets — but no controllers or services exist yet. The feature flag `payments-stub` is a placeholder.

## Database

**PostgreSQL 16 + PostGIS 3.4.** Migrations via Flyway at `src/main/resources/db/migration/`.

- V1: users, profiles, identity verifications
- V2: listings, photos, features, rules
- V3: bookings, payments, payouts (tables only)
- V4: messaging, reviews, support tickets, notifications, audit logs

All PKs are UUIDs via `gen_random_uuid()`. Email fields use `citext` for case-insensitive uniqueness. Soft deletes via `status` column.

## Configuration

Key environment variables (all have defaults for local dev):

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | Use `dev` for seed data + verbose logging |
| `DATABASE_URL` | JDBC URL (default: `jdbc:postgresql://localhost:5432/adgili`) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials (default: `adgili`/`adgili`) |
| `REDIS_URL` | Optional; falls back to in-memory if absent |
| `JWT_SECRET` | HS256 signing key — must be ≥32 bytes in prod |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins |

Dev seed users created on startup when users table is empty:
- Admin: `admin@adgi.li` / `Password123!`
- Hosts: giorgi, nino, levan `@example.com`
- Renters: mariam, david `@example.com`

## Testing

Integration tests only (no unit tests). `AuthFlowIntegrationTest` covers register → OTP verify → login → protected endpoints. Tests truncate the DB before each test in FK-safe order. Test profile disables Redis (in-memory fallback used).
