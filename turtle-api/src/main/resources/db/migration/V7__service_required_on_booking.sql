-- Remove time windows and their slots that have no associated service
DELETE FROM availability WHERE time_window_id IN (SELECT id FROM time_window WHERE service_id IS NULL);
DELETE FROM time_window WHERE service_id IS NULL;

-- Enforce service is required on time windows
ALTER TABLE time_window ALTER COLUMN service_id SET NOT NULL;

-- Track the main service on a booking
ALTER TABLE booking ADD COLUMN service_id BIGINT REFERENCES coaching_service(id);
