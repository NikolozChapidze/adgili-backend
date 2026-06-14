-- V3: booking requests, bookings, payments, payouts

CREATE TABLE booking_requests (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id             UUID NOT NULL REFERENCES listings(id) ON DELETE RESTRICT,
    renter_user_id         UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    host_user_id           UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    desired_start_date     DATE NOT NULL,
    renter_message         TEXT,
    status                 VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    host_response_message  TEXT,
    expires_at             TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at           TIMESTAMPTZ,
    CONSTRAINT br_status_chk CHECK (status IN
        ('REQUESTED','APPROVED','REJECTED','EXPIRED','CANCELLED'))
);

CREATE INDEX idx_br_renter ON booking_requests (renter_user_id, status);
CREATE INDEX idx_br_host   ON booking_requests (host_user_id, status);
CREATE INDEX idx_br_expires ON booking_requests (expires_at) WHERE status = 'REQUESTED';

-- ============================================================
-- bookings
-- ============================================================
CREATE TABLE bookings (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_request_id           UUID UNIQUE REFERENCES booking_requests(id),
    listing_id                   UUID NOT NULL REFERENCES listings(id) ON DELETE RESTRICT,
    renter_user_id               UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    host_user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status                       VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    start_date                   DATE NOT NULL,
    end_date                     DATE,
    billing_day_of_month         INT NOT NULL,
    monthly_price                NUMERIC(12,2) NOT NULL,
    platform_fee_renter          NUMERIC(12,2) NOT NULL DEFAULT 0,
    platform_fee_host            NUMERIC(12,2) NOT NULL DEFAULT 0,
    deposit_amount               NUMERIC(12,2),
    cancellation_notice_days     INT NOT NULL DEFAULT 30,
    confirmed_at                 TIMESTAMPTZ,
    activated_at                 TIMESTAMPTZ,
    cancelled_at                 TIMESTAMPTZ,
    completed_at                 TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT b_status_chk CHECK (status IN
        ('PENDING_PAYMENT','CONFIRMED','ACTIVE','CANCELLATION_REQUESTED',
         'CANCELLED','COMPLETED','DISPUTED','SUSPENDED')),
    CONSTRAINT b_billing_day_chk CHECK (billing_day_of_month BETWEEN 1 AND 28)
);

CREATE INDEX idx_bookings_renter  ON bookings (renter_user_id, status);
CREATE INDEX idx_bookings_host    ON bookings (host_user_id, status);
CREATE INDEX idx_bookings_listing ON bookings (listing_id, status);

-- ============================================================
-- payment_methods
-- ============================================================
CREATE TABLE payment_methods (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider                    VARCHAR(50) NOT NULL,
    provider_payment_method_id  VARCHAR(255) NOT NULL,
    brand                       VARCHAR(50),
    last4                       VARCHAR(4),
    exp_month                   INT,
    exp_year                    INT,
    is_default                  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pm_user ON payment_methods (user_id);

-- ============================================================
-- invoices
-- ============================================================
CREATE TABLE invoices (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id              UUID NOT NULL REFERENCES bookings(id) ON DELETE RESTRICT,
    invoice_type            VARCHAR(30) NOT NULL,
    billing_period_start    DATE NOT NULL,
    billing_period_end      DATE NOT NULL,
    subtotal_amount         NUMERIC(12,2) NOT NULL,
    renter_fee_amount       NUMERIC(12,2) NOT NULL DEFAULT 0,
    tax_amount              NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_amount            NUMERIC(12,2) NOT NULL,
    currency                VARCHAR(10) NOT NULL DEFAULT 'GEL',
    due_at                  TIMESTAMPTZ NOT NULL,
    paid_at                 TIMESTAMPTZ,
    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT inv_type_chk   CHECK (invoice_type IN ('INITIAL','RECURRING','ADJUSTMENT','REFUND')),
    CONSTRAINT inv_status_chk CHECK (status IN ('PENDING','PAID','FAILED','VOID','REFUNDED'))
);

CREATE INDEX idx_invoices_booking ON invoices (booking_id);
CREATE INDEX idx_invoices_status  ON invoices (status);

-- ============================================================
-- payment_transactions
-- ============================================================
CREATE TABLE payment_transactions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id                  UUID REFERENCES invoices(id),
    booking_id                  UUID REFERENCES bookings(id),
    user_id                     UUID NOT NULL REFERENCES users(id),
    provider                    VARCHAR(50) NOT NULL,
    provider_payment_intent_id  VARCHAR(255),
    transaction_type            VARCHAR(30) NOT NULL,
    amount                      NUMERIC(12,2) NOT NULL,
    currency                    VARCHAR(10) NOT NULL DEFAULT 'GEL',
    status                      VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    failure_reason              TEXT,
    raw_payload                 JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pt_type_chk   CHECK (transaction_type IN ('CHARGE','REFUND','DEPOSIT_HOLD','RELEASE')),
    CONSTRAINT pt_status_chk CHECK (status IN ('INITIATED','SUCCEEDED','FAILED','PENDING'))
);

CREATE INDEX idx_pt_invoice ON payment_transactions (invoice_id);
CREATE INDEX idx_pt_booking ON payment_transactions (booking_id);
CREATE INDEX idx_pt_user    ON payment_transactions (user_id);

-- ============================================================
-- payouts
-- ============================================================
CREATE TABLE payouts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID REFERENCES bookings(id),
    host_user_id        UUID NOT NULL REFERENCES users(id),
    invoice_id          UUID REFERENCES invoices(id),
    gross_amount        NUMERIC(12,2) NOT NULL,
    platform_fee_host   NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_amount          NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'GEL',
    provider            VARCHAR(50) NOT NULL,
    provider_payout_id  VARCHAR(255),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payouts_status_chk CHECK (status IN
        ('PENDING','IN_TRANSIT','PAID','FAILED','REVERSED'))
);

CREATE INDEX idx_payouts_host ON payouts (host_user_id, status);
