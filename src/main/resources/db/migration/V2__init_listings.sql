-- V2: listings and related tables

CREATE TABLE listings (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_user_id                   UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    slug                           VARCHAR(255) NOT NULL UNIQUE,
    title                          VARCHAR(255) NOT NULL,
    description                    TEXT NOT NULL,
    listing_status                 VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    moderation_status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    rejection_reason               TEXT,
    space_type                     VARCHAR(50) NOT NULL DEFAULT 'GARAGE',
    monthly_price                  NUMERIC(12,2) NOT NULL,
    currency                       VARCHAR(10) NOT NULL DEFAULT 'GEL',
    deposit_amount                 NUMERIC(12,2),
    min_rental_months              INT NOT NULL DEFAULT 1,
    notice_days_for_cancellation   INT NOT NULL DEFAULT 30,
    instant_book_enabled           BOOLEAN NOT NULL DEFAULT FALSE,

    -- Location: approximate (public) and exact (private, revealed post-booking)
    approximate_latitude           NUMERIC(9,6) NOT NULL,
    approximate_longitude          NUMERIC(9,6) NOT NULL,
    approximate_location           geography(Point, 4326) NOT NULL,
    exact_latitude                 NUMERIC(9,6),
    exact_longitude                NUMERIC(9,6),
    exact_location                 geography(Point, 4326),
    district                       VARCHAR(100) NOT NULL,
    neighborhood                   VARCHAR(100),
    address_line_1                 VARCHAR(255),
    address_line_2                 VARCHAR(255),
    postal_code                    VARCHAR(50),
    country_code                   VARCHAR(2) NOT NULL DEFAULT 'GE',

    -- Space dimensions
    width_m                        NUMERIC(8,2),
    length_m                       NUMERIC(8,2),
    height_m                       NUMERIC(8,2),
    area_sqm                       NUMERIC(8,2),
    indoor_flag                    BOOLEAN NOT NULL DEFAULT TRUE,
    enclosed_flag                  BOOLEAN NOT NULL DEFAULT TRUE,
    ground_floor_flag              BOOLEAN,
    humidity_notes                 TEXT,

    -- Access
    access_hours_text              VARCHAR(255),
    access_24_7_flag               BOOLEAN NOT NULL DEFAULT FALSE,
    access_notice_hours            INT NOT NULL DEFAULT 0,
    host_presence_required         BOOLEAN NOT NULL DEFAULT FALSE,

    -- Security
    security_camera_flag           BOOLEAN NOT NULL DEFAULT FALSE,
    gated_property_flag            BOOLEAN NOT NULL DEFAULT FALSE,
    alarm_flag                     BOOLEAN NOT NULL DEFAULT FALSE,
    lighting_flag                  BOOLEAN NOT NULL DEFAULT FALSE,

    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at                   TIMESTAMPTZ,

    CONSTRAINT listings_status_chk CHECK (listing_status IN
        ('DRAFT','PENDING_REVIEW','LIVE','REJECTED','PAUSED','BLOCKED','ARCHIVED')),
    CONSTRAINT listings_mod_chk    CHECK (moderation_status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT listings_space_chk  CHECK (space_type IN ('GARAGE','STORAGE_UNIT')),
    CONSTRAINT listings_price_chk  CHECK (monthly_price > 0)
);

CREATE INDEX idx_listings_host            ON listings (host_user_id);
CREATE INDEX idx_listings_status          ON listings (listing_status);
CREATE INDEX idx_listings_district        ON listings (district);
CREATE INDEX idx_listings_price           ON listings (monthly_price);
CREATE INDEX idx_listings_area            ON listings (area_sqm);
CREATE INDEX idx_listings_approx_geo      ON listings USING GIST (approximate_location);
CREATE INDEX idx_listings_published_live  ON listings (published_at DESC)
    WHERE listing_status = 'LIVE';

-- ============================================================
-- listing_photos
-- ============================================================
CREATE TABLE listing_photos (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id   UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    file_url     TEXT NOT NULL,
    sort_order   INT NOT NULL DEFAULT 0,
    is_cover     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_listing_photos_listing ON listing_photos (listing_id, sort_order);
CREATE UNIQUE INDEX uq_listing_photos_cover
    ON listing_photos (listing_id) WHERE is_cover = TRUE;

-- ============================================================
-- listing_features
-- ============================================================
CREATE TABLE listing_features (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id   UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    feature_code VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_listing_feature UNIQUE (listing_id, feature_code)
);

CREATE INDEX idx_listing_features_code ON listing_features (feature_code);

-- ============================================================
-- listing_rules
-- ============================================================
CREATE TABLE listing_rules (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id               UUID NOT NULL UNIQUE REFERENCES listings(id) ON DELETE CASCADE,
    allowed_items_text       TEXT,
    prohibited_items_text    TEXT,
    additional_access_rules  TEXT,
    move_in_instructions     TEXT,
    move_out_instructions    TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- listing_availability
-- ============================================================
CREATE TABLE listing_availability (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id      UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    available_from  DATE NOT NULL,
    available_to    DATE,
    status          VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT lav_status_chk CHECK (status IN ('AVAILABLE','BLOCKED','BOOKED'))
);

CREATE INDEX idx_listing_availability_listing ON listing_availability (listing_id, available_from);
