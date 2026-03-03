CREATE TABLE time_window (
    id                   BIGSERIAL PRIMARY KEY,
    coach_id             BIGINT        NOT NULL REFERENCES app_user(id),
    start_date           DATE          NOT NULL,
    end_date             DATE          NOT NULL,
    daily_start_time     TIME          NOT NULL,
    daily_end_time       TIME          NOT NULL,
    unit_of_work_minutes INTEGER       NOT NULL CHECK (unit_of_work_minutes > 0),
    price_per_unit       NUMERIC(10,2),
    priority             INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT tw_dates_check CHECK (end_date >= start_date),
    CONSTRAINT tw_times_check CHECK (daily_end_time > daily_start_time)
);

CREATE INDEX idx_time_window_coach_dates ON time_window (coach_id, start_date, end_date);
