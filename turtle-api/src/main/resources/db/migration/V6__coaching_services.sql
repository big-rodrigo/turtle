-- V6: Coaching Services

CREATE TABLE coaching_service (
    id          BIGSERIAL    PRIMARY KEY,
    coach_id    BIGINT       NOT NULL REFERENCES app_user(id),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    CONSTRAINT cs_name_not_empty CHECK (char_length(trim(name)) > 0)
);

CREATE INDEX idx_coaching_service_coach ON coaching_service (coach_id);

-- Self-referential join: a service can list other services as optional extras
CREATE TABLE service_extras (
    service_id  BIGINT NOT NULL REFERENCES coaching_service(id) ON DELETE CASCADE,
    extra_id    BIGINT NOT NULL REFERENCES coaching_service(id) ON DELETE CASCADE,
    PRIMARY KEY (service_id, extra_id),
    CONSTRAINT no_self_extra CHECK (service_id <> extra_id)
);

-- Bind time windows to a service (nullable so existing rows are not broken)
ALTER TABLE time_window
    ADD COLUMN service_id BIGINT REFERENCES coaching_service(id);

CREATE INDEX idx_time_window_service ON time_window (service_id);

-- Which extras the client selected when creating a booking
CREATE TABLE booking_extras (
    booking_id  BIGINT NOT NULL REFERENCES booking(id)          ON DELETE CASCADE,
    service_id  BIGINT NOT NULL REFERENCES coaching_service(id) ON DELETE RESTRICT,
    PRIMARY KEY (booking_id, service_id)
);
