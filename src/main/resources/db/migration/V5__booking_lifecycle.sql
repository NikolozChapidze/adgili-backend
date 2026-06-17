-- V5: Booking lifecycle redesign
-- Replaces the placeholder V3 bookings/booking_requests schema with a
-- single-table state-machine design. Also removes unused V3 payment tables
-- and updates V4 social tables to reference the new bookings table.

-- ============================================================
-- 1. Drop V4 tables that reference old booking_requests / bookings
--    (leaf tables first; CASCADE added as safety net)
-- ============================================================
DROP TABLE IF EXISTS conversation_messages CASCADE;
DROP TABLE IF EXISTS conversation_threads CASCADE;
DROP TABLE IF EXISTS disputes CASCADE;
DROP TABLE IF EXISTS reviews CASCADE;
DROP TABLE IF EXISTS support_tickets CASCADE;

-- ============================================================
-- 2. Drop V3 payment + booking tables
--    bookings must come before booking_requests because V3
--    bookings.booking_request_id references booking_requests.id
-- ============================================================
DROP TABLE IF EXISTS payouts CASCADE;
DROP TABLE IF EXISTS payment_transactions CASCADE;
DROP TABLE IF EXISTS invoices CASCADE;
DROP TABLE IF EXISTS payment_methods CASCADE;
DROP TABLE IF EXISTS bookings CASCADE;
DROP TABLE IF EXISTS booking_requests CASCADE;

-- ============================================================
-- 3. New bookings table (single-table state machine)
-- ============================================================
CREATE TABLE bookings (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id                      UUID NOT NULL REFERENCES listings(id) ON DELETE RESTRICT,
    host_user_id                    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    renter_user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

    status                          VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',

    -- Snapshot of listing terms at request time (immutable after creation)
    monthly_price                   NUMERIC(10,2) NOT NULL,
    currency                        VARCHAR(10) NOT NULL,
    deposit_amount                  NUMERIC(10,2),
    rental_months                   INTEGER NOT NULL,
    notice_days_for_cancellation    INTEGER NOT NULL,

    -- Timeline
    requested_move_in_date          DATE NOT NULL,
    actual_move_in_date             DATE,
    scheduled_move_out_date         DATE,
    actual_move_out_date            DATE,

    -- Renter context
    renter_message                  TEXT,
    items_to_store                  TEXT,

    -- Host decision
    host_response_message           TEXT,
    host_responded_at               TIMESTAMPTZ,

    -- Cancellation / termination
    cancelled_by_user_id            UUID REFERENCES users(id),
    cancellation_reason             TEXT,
    cancelled_at                    TIMESTAMPTZ,

    -- Auto-expiry (REQUESTED → EXPIRED after 48 h)
    request_expires_at              TIMESTAMPTZ NOT NULL,

    -- Audit
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT booking_status_chk CHECK (status IN (
        'REQUESTED', 'ACCEPTED', 'ACTIVE', 'ENDED',
        'DECLINED', 'EXPIRED', 'CANCELLED', 'TERMINATED'
    )),
    CONSTRAINT booking_no_self_booking CHECK (host_user_id != renter_user_id),
    CONSTRAINT booking_rental_months_chk CHECK (rental_months >= 1 AND rental_months <= 24)
);

CREATE INDEX idx_bookings_renter  ON bookings(renter_user_id, created_at DESC);
CREATE INDEX idx_bookings_host    ON bookings(host_user_id, created_at DESC);
CREATE INDEX idx_bookings_listing ON bookings(listing_id, status);
CREATE INDEX idx_bookings_status  ON bookings(status)
    WHERE status IN ('REQUESTED', 'ACCEPTED', 'ACTIVE');
CREATE INDEX idx_bookings_expiring ON bookings(request_expires_at)
    WHERE status = 'REQUESTED';

-- ============================================================
-- 4. Booking event log (append-only timeline)
-- ============================================================
CREATE TABLE booking_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    actor_user_id   UUID REFERENCES users(id),
    event_type      VARCHAR(50) NOT NULL,
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_events_booking ON booking_events(booking_id, created_at);

-- ============================================================
-- 5. Listings: track occupancy for availability filtering
-- ============================================================
ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS currently_rented BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_listings_available ON listings(listing_status, currently_rented)
    WHERE listing_status = 'LIVE' AND currently_rented = FALSE;

-- ============================================================
-- 6. Recreate V4 social/support tables pointing at new bookings
-- ============================================================
CREATE TABLE conversation_threads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id          UUID REFERENCES listings(id),
    booking_id          UUID REFERENCES bookings(id),
    host_user_id        UUID NOT NULL REFERENCES users(id),
    renter_user_id      UUID NOT NULL REFERENCES users(id),
    thread_status       VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ct_status_chk CHECK (thread_status IN ('OPEN', 'CLOSED', 'BLOCKED'))
);

CREATE INDEX idx_ct_host   ON conversation_threads(host_user_id);
CREATE INDEX idx_ct_renter ON conversation_threads(renter_user_id);

CREATE TABLE conversation_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id         UUID NOT NULL REFERENCES conversation_threads(id) ON DELETE CASCADE,
    sender_user_id    UUID NOT NULL REFERENCES users(id),
    message_body      TEXT NOT NULL,
    is_system_message BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at           TIMESTAMPTZ
);

CREATE INDEX idx_cm_thread_created ON conversation_messages(thread_id, created_at);

CREATE TABLE reviews (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id         UUID NOT NULL REFERENCES bookings(id),
    reviewer_user_id   UUID NOT NULL REFERENCES users(id),
    reviewee_user_id   UUID NOT NULL REFERENCES users(id),
    review_type        VARCHAR(30) NOT NULL,
    rating             INT NOT NULL,
    comment            TEXT,
    status             VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reviews_type_chk   CHECK (review_type IN ('HOST_REVIEW', 'RENTER_REVIEW')),
    CONSTRAINT reviews_rating_chk CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT reviews_status_chk CHECK (status IN ('PUBLISHED', 'HIDDEN', 'FLAGGED')),
    CONSTRAINT uq_review_per_booking_type UNIQUE (booking_id, review_type)
);

CREATE INDEX idx_reviews_reviewee ON reviews(reviewee_user_id, status);

CREATE TABLE support_tickets (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users(id),
    booking_id               UUID REFERENCES bookings(id),
    listing_id               UUID REFERENCES listings(id),
    category                 VARCHAR(50) NOT NULL,
    subject                  VARCHAR(255) NOT NULL,
    description              TEXT NOT NULL,
    status                   VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority                 VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assigned_admin_user_id   UUID REFERENCES users(id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at              TIMESTAMPTZ,
    CONSTRAINT st_status_chk   CHECK (status IN ('OPEN', 'IN_PROGRESS', 'WAITING_USER', 'RESOLVED', 'CLOSED')),
    CONSTRAINT st_priority_chk CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

CREATE INDEX idx_st_user   ON support_tickets(user_id, status);
CREATE INDEX idx_st_status ON support_tickets(status);

CREATE TABLE disputes (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id            UUID NOT NULL REFERENCES bookings(id),
    opened_by_user_id     UUID NOT NULL REFERENCES users(id),
    dispute_type          VARCHAR(50) NOT NULL,
    description           TEXT NOT NULL,
    status                VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    resolution_notes      TEXT,
    resolved_by           UUID REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at           TIMESTAMPTZ,
    CONSTRAINT d_status_chk CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'REJECTED'))
);

CREATE INDEX idx_disputes_booking ON disputes(booking_id);
