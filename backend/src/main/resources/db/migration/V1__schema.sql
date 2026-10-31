-- SULU core schema. Money is stored in integer minor units (tiyn/kurus) + explicit currency.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(254) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(120) NOT NULL,
  phone         VARCHAR(32),
  role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER','PROFESSIONAL','BUSINESS_OWNER','PLATFORM_ADMIN')),
  language      VARCHAR(5)   NOT NULL DEFAULT 'ru' CHECK (language IN ('kk','ru','en','tr')),
  city          VARCHAR(80),
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','BLOCKED','DELETED')),
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  notify_email  BOOLEAN NOT NULL DEFAULT TRUE,
  notify_push   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE service_categories (
  id      BIGSERIAL PRIMARY KEY,
  slug    VARCHAR(40) NOT NULL UNIQUE,
  name_kk VARCHAR(80) NOT NULL,
  name_ru VARCHAR(80) NOT NULL,
  name_en VARCHAR(80) NOT NULL,
  name_tr VARCHAR(80),
  icon    VARCHAR(40) NOT NULL DEFAULT 'sparkles',
  sort_order INT NOT NULL DEFAULT 0,
  active  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE businesses (
  id          BIGSERIAL PRIMARY KEY,
  owner_id    BIGINT NOT NULL REFERENCES users(id),
  name        VARCHAR(120) NOT NULL,
  kind        VARCHAR(20) NOT NULL DEFAULT 'SALON' CHECK (kind IN ('SALON','STUDIO','BARBERSHOP','INDEPENDENT')),
  description TEXT NOT NULL DEFAULT '',
  city        VARCHAR(80) NOT NULL,
  address     VARCHAR(200) NOT NULL,
  lat         DOUBLE PRECISION,
  lng         DOUBLE PRECISION,
  phone       VARCHAR(32),
  timezone    VARCHAR(40) NOT NULL DEFAULT 'Asia/Almaty',
  currency    CHAR(3) NOT NULL DEFAULT 'KZT',
  status      VARCHAR(24) NOT NULL DEFAULT 'PENDING_VERIFICATION'
              CHECK (status IN ('PENDING_VERIFICATION','APPROVED','REJECTED','SUSPENDED')),
  is_demo     BOOLEAN NOT NULL DEFAULT FALSE,
  image_key   VARCHAR(120),
  -- booking rules
  auto_confirm          BOOLEAN NOT NULL DEFAULT TRUE,
  min_lead_minutes      INT NOT NULL DEFAULT 60 CHECK (min_lead_minutes >= 0),
  max_advance_days      INT NOT NULL DEFAULT 60 CHECK (max_advance_days BETWEEN 1 AND 365),
  buffer_minutes        INT NOT NULL DEFAULT 0 CHECK (buffer_minutes BETWEEN 0 AND 120),
  cancellation_hours    INT NOT NULL DEFAULT 24 CHECK (cancellation_hours >= 0),
  rating_avg  NUMERIC(3,2) NOT NULL DEFAULT 0,
  rating_count INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_businesses_city_status ON businesses (city, status);
CREATE INDEX ix_businesses_owner ON businesses (owner_id);
CREATE INDEX ix_businesses_name_trgm ON businesses USING gin (lower(name) gin_trgm_ops);

CREATE TABLE business_hours (
  business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  weekday     SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7), -- ISO: 1=Mon
  open_time   TIME NOT NULL,
  close_time  TIME NOT NULL,
  PRIMARY KEY (business_id, weekday),
  CHECK (close_time > open_time)
);

CREATE TABLE business_closures (
  id BIGSERIAL PRIMARY KEY,
  business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  closed_on DATE NOT NULL,
  reason VARCHAR(120),
  UNIQUE (business_id, closed_on)
);

CREATE TABLE professionals (
  id          BIGSERIAL PRIMARY KEY,
  business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  user_id     BIGINT REFERENCES users(id),
  full_name   VARCHAR(120) NOT NULL,
  title       VARCHAR(120) NOT NULL DEFAULT '',
  bio         TEXT NOT NULL DEFAULT '',
  experience_years INT NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
  specialties VARCHAR(300) NOT NULL DEFAULT '',
  gender      VARCHAR(10) CHECK (gender IN ('FEMALE','MALE')),
  avatar_key  VARCHAR(120),
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  rating_avg  NUMERIC(3,2) NOT NULL DEFAULT 0,
  rating_count INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_professionals_business ON professionals (business_id) WHERE active;
CREATE UNIQUE INDEX ux_professionals_user ON professionals (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX ix_professionals_name_trgm ON professionals USING gin (lower(full_name) gin_trgm_ops);

CREATE TABLE professional_invites (
  id BIGSERIAL PRIMARY KEY,
  professional_id BIGINT NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  email VARCHAR(254) NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  accepted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE professional_working_hours (
  professional_id BIGINT NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  weekday     SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
  start_time  TIME NOT NULL,
  end_time    TIME NOT NULL,
  break_start TIME,
  break_end   TIME,
  PRIMARY KEY (professional_id, weekday),
  CHECK (end_time > start_time),
  CHECK ((break_start IS NULL) = (break_end IS NULL)),
  CHECK (break_start IS NULL OR (break_end > break_start AND break_start >= start_time AND break_end <= end_time))
);

CREATE TABLE professional_time_off (
  id BIGSERIAL PRIMARY KEY,
  professional_id BIGINT NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at   TIMESTAMPTZ NOT NULL,
  reason VARCHAR(120),
  CHECK (ends_at > starts_at)
);
CREATE INDEX ix_time_off_pro ON professional_time_off (professional_id, starts_at);

CREATE TABLE portfolio_images (
  id BIGSERIAL PRIMARY KEY,
  professional_id BIGINT NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  image_key VARCHAR(120) NOT NULL,
  caption VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE services (
  id          BIGSERIAL PRIMARY KEY,
  business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  category_id BIGINT NOT NULL REFERENCES service_categories(id),
  name        VARCHAR(120) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  price_minor BIGINT NOT NULL CHECK (price_minor >= 0),
  price_from  BOOLEAN NOT NULL DEFAULT FALSE,
  currency    CHAR(3) NOT NULL DEFAULT 'KZT',
  duration_min INT NOT NULL CHECK (duration_min BETWEEN 5 AND 720),
  at_home     BOOLEAN NOT NULL DEFAULT FALSE,
  tags        VARCHAR(200) NOT NULL DEFAULT '',
  image_key   VARCHAR(120),
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_services_business ON services (business_id) WHERE active;
CREATE INDEX ix_services_category ON services (category_id) WHERE active;
CREATE INDEX ix_services_name_trgm ON services USING gin (lower(name) gin_trgm_ops);

CREATE TABLE professional_services (
  professional_id BIGINT NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  service_id      BIGINT NOT NULL REFERENCES services(id) ON DELETE CASCADE,
  PRIMARY KEY (professional_id, service_id)
);
CREATE INDEX ix_pro_services_service ON professional_services (service_id);

CREATE TABLE promotions (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(40) NOT NULL,
  business_id BIGINT REFERENCES businesses(id) ON DELETE CASCADE,
  percent_off INT CHECK (percent_off BETWEEN 1 AND 100),
  amount_off_minor BIGINT CHECK (amount_off_minor > 0),
  currency CHAR(3),
  valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
  valid_to TIMESTAMPTZ,
  max_redemptions INT,
  redemptions INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((percent_off IS NOT NULL) <> (amount_off_minor IS NOT NULL))
);
CREATE UNIQUE INDEX ux_promotions_code ON promotions (upper(code));

CREATE TABLE bookings (
  id            BIGSERIAL PRIMARY KEY,
  reference     VARCHAR(16) NOT NULL UNIQUE,
  customer_id   BIGINT NOT NULL REFERENCES users(id),
  business_id   BIGINT NOT NULL REFERENCES businesses(id),
  professional_id BIGINT NOT NULL REFERENCES professionals(id),
  service_id    BIGINT NOT NULL REFERENCES services(id),
  service_name  VARCHAR(120) NOT NULL,           -- snapshot so history survives edits
  start_at      TIMESTAMPTZ NOT NULL,
  end_at        TIMESTAMPTZ NOT NULL,
  status        VARCHAR(12) NOT NULL CHECK (status IN ('PENDING','CONFIRMED','COMPLETED','CANCELLED','REJECTED','NO_SHOW')),
  price_minor   BIGINT NOT NULL CHECK (price_minor >= 0),
  discount_minor BIGINT NOT NULL DEFAULT 0 CHECK (discount_minor >= 0),
  currency      CHAR(3) NOT NULL,
  promotion_id  BIGINT REFERENCES promotions(id),
  commission_bps INT NOT NULL DEFAULT 0,
  payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
  customer_note VARCHAR(500),
  cancel_reason VARCHAR(200),
  idempotency_key VARCHAR(80),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (end_at > start_at),
  CHECK (discount_minor <= price_minor),
  during TSTZRANGE GENERATED ALWAYS AS (tstzrange(start_at, end_at, '[)')) STORED,
  -- Core guarantee: one professional can never have two overlapping active bookings.
  CONSTRAINT bookings_no_overlap EXCLUDE USING gist (professional_id WITH =, during WITH &&)
    WHERE (status IN ('PENDING','CONFIRMED'))
);
CREATE UNIQUE INDEX ux_bookings_idem ON bookings (customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_bookings_customer ON bookings (customer_id, start_at DESC);
CREATE INDEX ix_bookings_business ON bookings (business_id, start_at);
CREATE INDEX ix_bookings_pro ON bookings (professional_id, start_at);

CREATE TABLE booking_status_history (
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  from_status VARCHAR(12),
  to_status VARCHAR(12) NOT NULL,
  actor_id BIGINT REFERENCES users(id),
  note VARCHAR(200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_bsh_booking ON booking_status_history (booking_id);

CREATE TABLE promotion_redemptions (
  id BIGSERIAL PRIMARY KEY,
  promotion_id BIGINT NOT NULL REFERENCES promotions(id),
  user_id BIGINT NOT NULL REFERENCES users(id),
  booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (promotion_id, user_id)
);

CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES bookings(id),
  provider VARCHAR(30) NOT NULL,
  provider_ref VARCHAR(80) NOT NULL,
  method VARCHAR(20) NOT NULL,
  amount_minor BIGINT NOT NULL CHECK (amount_minor >= 0),
  refunded_minor BIGINT NOT NULL DEFAULT 0 CHECK (refunded_minor >= 0),
  currency CHAR(3) NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('INITIATED','PENDING','SUCCEEDED','FAILED','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')),
  simulated BOOLEAN NOT NULL DEFAULT FALSE,
  idempotency_key VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_ref),
  CHECK (refunded_minor <= amount_minor)
);
CREATE INDEX ix_payments_booking ON payments (booking_id);

CREATE TABLE payment_events (
  id BIGSERIAL PRIMARY KEY,
  payment_id BIGINT REFERENCES payments(id),
  provider VARCHAR(30) NOT NULL,
  event_id VARCHAR(80) NOT NULL,
  type VARCHAR(40) NOT NULL,
  payload TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, event_id)   -- webhook idempotency
);

CREATE TABLE reviews (
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id), -- one review per booking
  customer_id BIGINT NOT NULL REFERENCES users(id),
  business_id BIGINT NOT NULL REFERENCES businesses(id),
  professional_id BIGINT NOT NULL REFERENCES professionals(id),
  rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  body VARCHAR(2000) NOT NULL DEFAULT '',
  reply VARCHAR(1000),
  replied_at TIMESTAMPTZ,
  status VARCHAR(12) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE','HIDDEN')),
  reported_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reviews_business ON reviews (business_id, created_at DESC) WHERE status = 'VISIBLE';
CREATE INDEX ix_reviews_pro ON reviews (professional_id, created_at DESC) WHERE status = 'VISIBLE';

CREATE TABLE review_reports (
  review_id BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
  reporter_id BIGINT NOT NULL REFERENCES users(id),
  reason VARCHAR(200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (review_id, reporter_id)
);

CREATE TABLE favorites (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind VARCHAR(12) NOT NULL CHECK (kind IN ('BUSINESS','PROFESSIONAL','SERVICE')),
  target_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kind, target_id)
);

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(40) NOT NULL,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(400) NOT NULL DEFAULT '',
  link VARCHAR(120),
  dedupe_key VARCHAR(120),
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_notifications_dedupe ON notifications (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);

CREATE TABLE subscription_plans (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  price_minor BIGINT NOT NULL DEFAULT 0 CHECK (price_minor >= 0),
  currency CHAR(3) NOT NULL DEFAULT 'KZT',
  features VARCHAR(400) NOT NULL DEFAULT '',
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE business_subscriptions (
  business_id BIGINT PRIMARY KEY REFERENCES businesses(id) ON DELETE CASCADE,
  plan_id BIGINT NOT NULL REFERENCES subscription_plans(id),
  status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED','PAST_DUE')),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE platform_settings (
  key VARCHAR(60) PRIMARY KEY,
  value VARCHAR(200) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT REFERENCES users(id),
  action VARCHAR(60) NOT NULL,
  target_type VARCHAR(30) NOT NULL,
  target_id VARCHAR(40),
  detail VARCHAR(400),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);

CREATE TABLE business_customer_notes (
  business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  customer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  note VARCHAR(1000) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (business_id, customer_id)
);
