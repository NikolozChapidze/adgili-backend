-- V4: messaging, reviews, support, notifications, audit

CREATE TABLE conversation_threads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id          UUID REFERENCES listings(id),
    booking_request_id  UUID REFERENCES booking_requests(id),
    booking_id          UUID REFERENCES bookings(id),
    host_user_id        UUID NOT NULL REFERENCES users(id),
    renter_user_id      UUID NOT NULL REFERENCES users(id),
    thread_status       VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ct_status_chk CHECK (thread_status IN ('OPEN','CLOSED','BLOCKED'))
);

CREATE INDEX idx_ct_host   ON conversation_threads (host_user_id);
CREATE INDEX idx_ct_renter ON conversation_threads (renter_user_id);

CREATE TABLE conversation_messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id         UUID NOT NULL REFERENCES conversation_threads(id) ON DELETE CASCADE,
    sender_user_id    UUID NOT NULL REFERENCES users(id),
    message_body      TEXT NOT NULL,
    is_system_message BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at           TIMESTAMPTZ
);

CREATE INDEX idx_cm_thread_created ON conversation_messages (thread_id, created_at);

-- ============================================================
-- reviews
-- ============================================================
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
    CONSTRAINT reviews_type_chk   CHECK (review_type IN ('HOST_REVIEW','RENTER_REVIEW')),
    CONSTRAINT reviews_rating_chk CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT reviews_status_chk CHECK (status IN ('PUBLISHED','HIDDEN','FLAGGED')),
    CONSTRAINT uq_review_per_booking_type UNIQUE (booking_id, review_type)
);

CREATE INDEX idx_reviews_reviewee ON reviews (reviewee_user_id, status);

-- ============================================================
-- support_tickets
-- ============================================================
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
    CONSTRAINT st_status_chk   CHECK (status IN ('OPEN','IN_PROGRESS','WAITING_USER','RESOLVED','CLOSED')),
    CONSTRAINT st_priority_chk CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT'))
);

CREATE INDEX idx_st_user   ON support_tickets (user_id, status);
CREATE INDEX idx_st_status ON support_tickets (status);

-- ============================================================
-- disputes
-- ============================================================
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
    CONSTRAINT d_status_chk CHECK (status IN ('OPEN','REVIEWING','RESOLVED','REJECTED'))
);

CREATE INDEX idx_disputes_booking ON disputes (booking_id);

-- ============================================================
-- notifications
-- ============================================================
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel         VARCHAR(20) NOT NULL,
    template_code   VARCHAR(100) NOT NULL,
    subject         VARCHAR(255),
    payload         JSONB,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT n_channel_chk CHECK (channel IN ('EMAIL','SMS','IN_APP')),
    CONSTRAINT n_status_chk  CHECK (status  IN ('PENDING','SENT','FAILED'))
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, created_at DESC)
    WHERE channel = 'IN_APP' AND read_at IS NULL;

-- ============================================================
-- audit_logs
-- ============================================================
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES users(id),
    actor_type    VARCHAR(30) NOT NULL DEFAULT 'USER',
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     UUID,
    action        VARCHAR(100) NOT NULL,
    old_value     JSONB,
    new_value     JSONB,
    ip_address    VARCHAR(64),
    user_agent    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT al_actor_chk CHECK (actor_type IN ('USER','ADMIN','SYSTEM'))
);

CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_actor  ON audit_logs (actor_user_id, created_at DESC);
