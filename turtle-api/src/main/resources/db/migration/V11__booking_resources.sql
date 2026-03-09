CREATE TABLE booking_resource (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT NOT NULL REFERENCES booking(id),
    title       VARCHAR(255) NOT NULL,
    url         VARCHAR(2048) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_resource_booking ON booking_resource(booking_id);
