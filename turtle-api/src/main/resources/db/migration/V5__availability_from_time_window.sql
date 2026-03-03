-- Link availability slots back to the time window that generated them
ALTER TABLE availability
    ADD COLUMN time_window_id BIGINT REFERENCES time_window(id);

-- A booking now owns many availabilities (nullable until booking is created)
ALTER TABLE availability
    ADD COLUMN booking_id BIGINT REFERENCES booking(id);

-- Migrate existing bookings: mark their availability as owned by that booking
UPDATE availability a
SET booking_id = b.id
FROM booking b
WHERE b.availability_id = a.id;

-- Drop old booking_source_check constraint
ALTER TABLE booking DROP CONSTRAINT IF EXISTS booking_source_check;

-- Drop the old one-to-one FK and the now-obsolete time-window columns from booking
ALTER TABLE booking DROP COLUMN IF EXISTS availability_id;
ALTER TABLE booking DROP COLUMN IF EXISTS time_window_id;
ALTER TABLE booking DROP COLUMN IF EXISTS starts_at;
ALTER TABLE booking DROP COLUMN IF EXISTS ends_at;

-- Index for querying all slots in a time window by status
CREATE INDEX idx_availability_time_window ON availability (time_window_id, booking_id, starts_at);
