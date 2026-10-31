# Architecture

```
web (React/Vite PWA)  ──/api──▶  backend (Spring Boot, modular monolith)  ──▶  PostgreSQL 16 (Flyway)
        └── /media ◀── StorageService (local disk; S3/R2 seam)
```

## Backend modules
| Package | Responsibility |
|---|---|
| `identity` | register/login/refresh(rotation)/reset/invite, `/api/me` (profile, export, delete) |
| `security` | stateless JWT (HS256), DB-backed role/status per request, CORS, method security |
| `catalog` | public categories, salons, professionals, services, unified search, reviews list |
| `booking` | `AvailabilityService` (slot engine), `BookingService` (create/act/reschedule), reminders |
| `payment` | `PaymentProvider` seam, `SimulatedPaymentProvider` (dev), webhook, refunds |
| `engagement` | reviews, favorites, notifications (+ `ExternalChannel` seam), audit log |
| `business` | owner back-office (`/api/biz/{id}`), staff schedules, `/api/pro` self-service |
| `admin` | verification, moderation, commission, plans, promotions, audit |

## Booking flow
1. `GET /api/availability?serviceId&professionalId|any&date` → slots (advisory).
2. `POST /api/bookings` (`Idempotency-Key`) → transaction: advisory lock per professional → recompute slots → price/promo → insert (DB exclusion constraint is the final arbiter) → history + notifications.
3. Conflict → `409 SLOT_UNAVAILABLE` with nearby alternatives.
4. Optional `POST /api/bookings/{id}/payments`; result arrives by provider webhook (`POST /api/payments/webhook/{provider}`, `X-Sulu-Signature` = hex HMAC-SHA256 of raw body).

## Data
See `backend/src/main/resources/db/migration`. Money = `bigint` minor units + `char(3)` currency. Reference data (categories, plans, settings) is a migration; demo data is a dev-only runner.
