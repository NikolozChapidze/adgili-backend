-- adgi.li schema bootstrap
-- V1: extensions + core identity tables

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "citext";

-- ============================================================
-- users
-- ============================================================
CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 CITEXT UNIQUE,
    phone                 VARCHAR(50) UNIQUE,
    password_hash         VARCHAR(255),
    role                  VARCHAR(30) NOT NULL DEFAULT 'USER',
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    email_verified_at     TIMESTAMPTZ,
    phone_verified_at     TIMESTAMPTZ,
    terms_accepted_at     TIMESTAMPTZ,
    privacy_accepted_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT users_role_chk   CHECK (role   IN ('USER','ADMIN','SUPPORT')),
    CONSTRAINT users_status_chk CHECK (status IN ('ACTIVE','SUSPENDED','DELETED','PENDING'))
);

CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_role   ON users (role);

-- ============================================================
-- user_profiles
-- ============================================================
CREATE TABLE user_profiles (
    user_id              UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    first_name           VARCHAR(100),
    last_name            VARCHAR(100),
    display_name         VARCHAR(150),
    profile_photo_url    TEXT,
    bio                  TEXT,
    preferred_language   VARCHAR(10) NOT NULL DEFAULT 'ka',
    city                 VARCHAR(100) NOT NULL DEFAULT 'Tbilisi',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- identity_verifications
-- ============================================================
CREATE TABLE identity_verifications (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    verification_type     VARCHAR(30) NOT NULL,
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    provider_reference    VARCHAR(255),
    document_front_url    TEXT,
    document_back_url     TEXT,
    rejection_reason      TEXT,
    reviewed_by           UUID REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at           TIMESTAMPTZ,
    CONSTRAINT idv_type_chk   CHECK (verification_type IN ('ID_CARD','PASSPORT','SELFIE')),
    CONSTRAINT idv_status_chk CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED'))
);

CREATE INDEX idx_idv_user_status ON identity_verifications (user_id, status);

-- ============================================================
-- host_profiles
-- ============================================================
CREATE TABLE host_profiles (
    user_id                 UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    host_status             VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    average_rating          NUMERIC(3,2) NOT NULL DEFAULT 0,
    ratings_count           INT NOT NULL DEFAULT 0,
    response_rate           NUMERIC(5,2),
    response_time_minutes   INT,
    payout_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT hp_status_chk CHECK (host_status IN ('PENDING','ACTIVE','BLOCKED'))
);

-- ============================================================
-- renter_profiles
-- ============================================================
CREATE TABLE renter_profiles (
    user_id          UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    average_rating   NUMERIC(3,2) NOT NULL DEFAULT 0,
    ratings_count    INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- payout_accounts
-- ============================================================
CREATE TABLE payout_accounts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider                    VARCHAR(50) NOT NULL,
    provider_account_id         VARCHAR(255),
    status                      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    account_holder_name         VARCHAR(255),
    iban_or_account_masked      VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payout_status_chk CHECK (status IN ('PENDING','VERIFIED','BLOCKED'))
);

CREATE INDEX idx_payout_accounts_user ON payout_accounts (user_id);
