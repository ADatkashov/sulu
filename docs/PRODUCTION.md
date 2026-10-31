# Production checklist
- Run with **no** `dev` profile. Set `SULU_JWT_SECRET` (>=32 random bytes), DB credentials, `SULU_CORS_ORIGINS`, `SULU_ADMIN_EMAIL/PASSWORD` (rotate password after first login).
- `SULU_SEED_DEMO=false`, `SULU_PAYMENTS_SIMULATED=false`.
- Terminate TLS in front; serve `web/dist` as static files and proxy `/api` and `/media` to the backend (same origin → no CORS needed).
- Replace `StorageService` local-disk implementation with S3/R2 and CDN; use licensed photography.
- Implement a real `PaymentProvider` (create charge, refund) and register its webhook with `SULU_PAYMENT_WEBHOOK_SECRET`; verify signatures per the provider's scheme.
- Implement `ExternalChannel` for email/SMS/push; until then only in-app notifications exist.
- Move the in-memory auth rate limiter to a shared store when running >1 instance.
- Back up PostgreSQL; Flyway migrations are forward-only.
