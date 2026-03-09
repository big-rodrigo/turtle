-- Migrate existing booking statuses to new payment-aware flow
UPDATE booking SET status = 'CONFIRMED'       WHERE status = 'APPROVED';
UPDATE booking SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';

-- Create payment table
CREATE TABLE payment (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT        NOT NULL UNIQUE REFERENCES booking(id),
    preference_id       VARCHAR(255),
    external_payment_id VARCHAR(255),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    amount              DECIMAL(10, 2),
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_payment_external_id ON payment(external_payment_id);
