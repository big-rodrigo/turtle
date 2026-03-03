ALTER TABLE booking
    ADD COLUMN time_window_id BIGINT REFERENCES time_window(id),
    ADD COLUMN starts_at      TIMESTAMP,
    ADD COLUMN ends_at        TIMESTAMP;

-- Allow availability_id to be nullable (time-window bookings don't use it)
ALTER TABLE booking ALTER COLUMN availability_id DROP NOT NULL;

-- Constraint: exactly one of (availability_id, time_window_id) must be set
ALTER TABLE booking ADD CONSTRAINT booking_source_check
    CHECK (
        (availability_id IS NOT NULL AND time_window_id IS NULL) OR
        (availability_id IS NULL     AND time_window_id IS NOT NULL)
    );
