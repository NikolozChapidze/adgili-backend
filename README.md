# adgi.li — Backend API (MVP)

Peer-to-peer garage storage marketplace for Tbilisi. Spring Boot 3 modular monolith implementing Phase 1 of the PRD: auth, profiles, identity verification, listings with moderation, search with PostGIS, and an admin surface.

This MVP ships with **stubs** for email, SMS, identity verification, and payments so you can demo the whole flow without signing up for any provider.

---

## Quick links

- **Swagger UI** — `http://localhost:8080/swagger-ui.html` once running
- **OpenAPI JSON** — `/v3/api-docs`
- **Health check** — `/actuator/health`

---

## Tech stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 3.3.5 |
| DB | PostgreSQL 16 + PostGIS 3.4 |
| Migrations | Flyway |
| Auth | JWT (HS256, access + refresh), BCrypt-12 |
| Cache/OTP | Redis (optional — falls back to in-memory) |
| Docs | springdoc-openapi (Swagger UI) |
| Mapping | MapStruct, Jackson |
| Testing | JUnit 5, Testcontainers (real PostGIS) |
| Build | Maven 3.9 (with Maven wrapper) |

---

## Quick start (local)

### Option A: Docker Compose (easiest, no Java needed locally)

```bash
docker compose up --build
```

That brings up `postgres` (with PostGIS), `redis`, and the `api`. The API starts with `SPRING_PROFILES_ACTIVE=dev`, which triggers **seed data** on first boot.

Open:
- **Swagger UI** → http://localhost:8080/swagger-ui.html

### Option B: Run locally against dockerized Postgres

```bash
# 1. Only start Postgres + Redis
docker compose up -d postgres redis

# 2. Run the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Maven wrapper

The project includes `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/` so you can build without installing Maven globally. If for some reason the wrapper is missing, regenerate it with:

```bash
mvn wrapper:wrapper -Dmaven=3.9.9
```

Common Maven commands:

| Task | Command |
|---|---|
| Run the app | `./mvnw spring-boot:run` |
| Build a jar | `./mvnw clean package` |
| Build without tests | `./mvnw clean package -DskipTests` |
| Run tests only | `./mvnw test` |

The packaged jar lands at `target/adgili-backend.jar`.

---

## Seed data & demo credentials

On startup with the `dev` profile and an empty `users` table, `DevDataSeeder` creates:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@adgi.li` | `Password123!` |
| Host | `giorgi@example.com` | `Password123!` |
| Host | `nino@example.com` | `Password123!` |
| Host | `levan@example.com` | `Password123!` |
| Renter | `mariam@example.com` | `Password123!` |
| Renter | `david@example.com` | `Password123!` |

Plus **10 LIVE listings** distributed across Tbilisi (Vake, Saburtalo, Vera, Didube, Mtatsminda, Old Tbilisi, Isani, Gldani, Nadzaladevi) with realistic coordinates, photos (picsum.photos), dimensions, rules, and feature tags.

---

## End-to-end demo walkthrough (curl)

Copy-paste friendly. Works against a running local server.

### 1. Search public listings (no auth needed)

```bash
curl -s "http://localhost:8080/api/v1/search/listings?district=Saburtalo&sort=PRICE_ASC" | jq
```

### 2. Radius search around a point (PostGIS)

```bash
# 2km radius around Freedom Square
curl -s "http://localhost:8080/api/v1/search/listings?centerLat=41.6934&centerLon=44.8015&radiusMeters=2000&sort=CLOSEST" | jq
```

### 3. Register a new user

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "email":"demo@example.com",
    "phone":"+995555777888",
    "password":"Password123!",
    "firstName":"Demo","lastName":"User",
    "acceptTerms":true,"acceptPrivacy":true
  }' | tee /tmp/auth.json | jq
```

Capture the access token:

```bash
TOKEN=$(jq -r .tokens.accessToken /tmp/auth.json)
```

### 4. Verify phone (dev stub uses fixed OTP `000000`)

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/verify-phone-otp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"phone":"+995555777888","code":"000000"}' -i
```

### 5. Verify email (fetch token from notifications table — stubbed)

In dev, email notifications are persisted with the token in the payload. Query via admin or Swagger, or just log in as the seeded admin and hit `/admin/audit-logs`. For a full test, see `AuthFlowIntegrationTest`.

### 6. Login as a host and create a listing

```bash
HOST_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"giorgi@example.com","password":"Password123!"}' \
  | jq -r .tokens.accessToken)

curl -s -X POST http://localhost:8080/api/v1/listings \
  -H "Authorization: Bearer $HOST_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "title":"Brand new demo listing",
    "description":"Clean, dry, well-lit garage in Vake",
    "spaceType":"GARAGE",
    "monthlyPrice":350.00,
    "minRentalMonths":1,
    "location":{
      "district":"Vake","neighborhood":"Chavchavadze",
      "addressLine1":"Chavchavadze Ave 42",
      "exactLatitude":41.7094,"exactLongitude":44.7589
    },
    "dimensions":{"widthM":3.2,"lengthM":5.5,"heightM":2.4,"areaSqm":17.6},
    "access":{"accessHoursText":"24/7","access247Flag":true,"accessNoticeHours":0},
    "security":{"securityCameraFlag":true,"gatedPropertyFlag":true,"lightingFlag":true},
    "rules":{
      "allowedItemsText":"Boxes, furniture, seasonal items",
      "prohibitedItemsText":"Flammables, perishables"
    },
    "featureCodes":["CCTV","GATED","DRY_SPACE"]
  }' | tee /tmp/listing.json | jq
```

### 7. Add a photo, then submit for review

```bash
LISTING_ID=$(jq -r .id /tmp/listing.json)

curl -s -X POST "http://localhost:8080/api/v1/host/listings/$LISTING_ID/photos" \
  -H "Authorization: Bearer $HOST_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"fileUrl":"https://picsum.photos/seed/demo1/1200/800","isCover":true}'

curl -s -X POST "http://localhost:8080/api/v1/host/listings/$LISTING_ID/submit-for-review" \
  -H "Authorization: Bearer $HOST_TOKEN"
```

### 8. Admin approves the listing

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@adgi.li","password":"Password123!"}' \
  | jq -r .tokens.accessToken)

# See the moderation queue
curl -s "http://localhost:8080/api/v1/admin/listings?status=PENDING" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.content[].title'

# Approve
curl -s -X POST "http://localhost:8080/api/v1/admin/listings/$LISTING_ID/approve" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq .listingStatus
# → "LIVE"
```

### 9. The listing is now discoverable

```bash
curl -s "http://localhost:8080/api/v1/search/listings?district=Vake" \
  | jq '.content[] | select(.title=="Brand new demo listing")'
```

---

## Project structure

```
adgili-backend/
├── pom.xml                       — Maven build
├── Dockerfile                    — multi-stage build (Maven → JRE 21)
├── docker-compose.yml            — local stack (api + postgres + redis)
├── railway.json                  — Railway deploy config
├── mvnw, mvnw.cmd                — Maven wrapper scripts
├── .mvn/wrapper/                 — wrapper config
└── src/
    ├── main/java/li/adgi/api/
    │   ├── AdgiliApplication.java
    │   ├── admin/          — admin dashboard, user suspend, audit viewer
    │   ├── audit/          — AuditLog (JSONB), REQUIRES_NEW service
    │   ├── auth/           — register/login/refresh/verify, OtpStore, TokenStore
    │   ├── common/         — BaseEntity, ApiException, GlobalExceptionHandler, GeoUtil
    │   ├── config/         — JwtProperties, FeatureFlags, OpenApiConfig, DevDataSeeder
    │   ├── listing/        — Listing + Photo/Feature/Rules, state machine, moderation
    │   ├── notification/   — stubbed email/SMS/in-app dispatch, in-app feed
    │   ├── search/         — native PostGIS queries with ST_DWithin + ST_Distance
    │   ├── security/       — JwtService, JwtAuthenticationFilter, SecurityConfig
    │   ├── user/           — User, UserProfile, HostProfile, RenterProfile
    │   └── verification/   — identity submission, async auto-approve, admin review
    ├── main/resources/
    │   ├── application.properties        — base config
    │   ├── application-dev.properties    — dev profile (seed data active)
    │   ├── application-prod.properties   — prod overrides
    │   └── db/migration/                 — V1..V4 Flyway migrations
    └── test/java/                — Testcontainers integration tests
```

---

## Configuration

All settings in `application.properties`, overridable by environment variables. Key ones:

| Env var | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/adgili` | JDBC URL |
| `DATABASE_USERNAME` | `adgili` | DB user |
| `DATABASE_PASSWORD` | `adgili` | DB password |
| `REDIS_URL` | `redis://localhost:6379` | Redis (optional) |
| `JWT_SECRET` | (dev default) | **Change for prod** — min 32 bytes |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,https://adgi.li` | Comma-separated |
| `SPRING_PROFILES_ACTIVE` | (none) | Use `dev` to enable seeder, `prod` for prod config |

Feature flags (stubs, all `true` by default):

- `adgili.features.email-stub` — log email to console instead of sending
- `adgili.features.sms-stub` — use fixed OTP `000000`
- `adgili.features.identity-auto-approve` — auto-approve verification after 2s
- `adgili.features.payments-stub` — payments tables exist, no real provider

---

## Deploying to Railway

1. **Create a new Railway project** and connect this repo (or push from GitHub).
2. **Add a PostgreSQL plugin** — Railway provisions a managed Postgres instance.
3. **Enable PostGIS on the database.** In the Railway Postgres query tab:
   ```sql
   CREATE EXTENSION IF NOT EXISTS postgis;
   CREATE EXTENSION IF NOT EXISTS pgcrypto;
   CREATE EXTENSION IF NOT EXISTS citext;
   ```
   (Flyway's V1 migration also runs these, but enabling them as superuser first avoids permission issues on managed Postgres.)
4. **Environment variables on the API service:**
   - `SPRING_PROFILES_ACTIVE=prod` (or `prod,seed` for seed data on first deploy)
   - `JWT_SECRET=<generate a 48+ byte random string>`
   - `DATABASE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
   - `DATABASE_USERNAME=${{Postgres.PGUSER}}`
   - `DATABASE_PASSWORD=${{Postgres.PGPASSWORD}}`
   - `CORS_ALLOWED_ORIGINS=https://your-frontend.com`
5. **Deploy** — Railway uses the `Dockerfile` and `railway.json` automatically. First deploy takes ~3-4 minutes (Maven dependency download + build).
6. **Verify** — hit `https://<your-app>.up.railway.app/actuator/health` and `/swagger-ui.html`.

### Recommended demo setup on Railway

- Use `SPRING_PROFILES_ACTIVE=prod,seed` so the seed loads on first deploy. The seeder is idempotent (skips if users table is non-empty), so you can leave `seed` on or remove it after the first run — either works.
- Set `JWT_SECRET` once and don't rotate it during the demo (would invalidate all tokens).

---

## Running the tests

```bash
./mvnw test
```

Testcontainers spins up a real PostGIS 16 container for each test run. Requires Docker running locally. `AuthFlowIntegrationTest` covers:

- End-to-end register → phone OTP verify → email token verify → status transitions to ACTIVE
- Login happy/sad paths (correct creds → 200, wrong password → 401)
- Protected endpoints reject missing/invalid bearer tokens
- Duplicate email registration returns 409

---

## API surface — what's implemented in MVP

### Auth (`/api/v1/auth/*`) — all public
`POST /register`, `/login`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password`, `/verify-email`, `/send-phone-otp`, `/verify-phone-otp`, `/me`

### Users (`/api/v1/users/*`) — auth required except public profile
`GET /me`, `PATCH /me`, `GET /{userId}`

### Verifications
`POST /verifications/identity`, `GET /verifications/me`

### Listings
Public: `GET /listings/{id}` (exact address hidden unless post-booking)
Host: `POST /listings`, `GET /host/listings`, `PATCH /host/listings/{id}`, `/submit-for-review`, `/pause`, `/unpause`, `POST /host/listings/{id}/photos`, `DELETE …`

### Search (public)
`GET /search/listings` — filters: district, neighborhood, min/maxPrice, min/maxArea, access247, gated, cctv, indoor, centerLat+centerLon+radiusMeters (PostGIS), sort (RECOMMENDED, PRICE_ASC, PRICE_DESC, AREA_DESC, NEWEST, CLOSEST)

### Notifications
`GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`

### Admin (`/api/v1/admin/*`) — ADMIN role only
`GET /dashboard`, `/users`, `/users/{id}`, `POST /users/{id}/suspend`, `/unsuspend`, `GET /audit-logs`, `/listings`, `POST /listings/{id}/approve`, `/reject`, `/block`, `GET /verifications`, `POST /verifications/{id}/approve`, `/reject`

---

## What's NOT in this MVP (scoped out intentionally)

From the PRD, these are out of scope for Phase 1:
- Booking requests and booking lifecycle (DB tables exist, no controllers)
- Payments, invoices, recurring billing, payouts (DB tables + stub feature flag only)
- Messaging (threads + messages; DB tables only)
- Reviews (DB tables only)
- Support tickets and disputes (DB tables only)
- Native mobile, Algolia/search-engine upgrades, smart-lock integrations

The schema is already in place (migrations V3 + V4), so Phase 2 can add controllers/services without touching the DB.

---

## Trade-offs made for MVP

1. **No refresh token rotation.** A refresh token stays valid until TTL expiry. For prod, store a per-user refresh token hash in Redis and rotate on use.
2. **No rate limiting yet.** Spring Cloud Gateway or bucket4j would be the next step — the PRD calls for it on auth/booking/messaging.
3. **Slug generation is dumb** — base-from-title + random suffix. Good enough; collisions are effectively zero.
4. **Approximate-location jitter is fixed at 300m.** Listings in rural edges of Tbilisi are fine; dense central blocks could use finer grain.
5. **Photo upload is URL-only.** No multipart upload, no image processing, no S3 signing — the API accepts pre-uploaded URLs. Wire S3/Cloudflare R2 when you have a frontend to upload from.
6. **In-memory OTP/token fallback.** If Redis is down, the app keeps working but tokens are per-instance. Fine for 1 instance, broken for horizontal scaling.

---

## License

Proprietary. See the founders.
