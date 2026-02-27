CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(200) NOT NULL UNIQUE,
    phone         VARCHAR(30),
    password_hash VARCHAR(72)  NOT NULL,
    role          VARCHAR(20)  NOT NULL
);

CREATE TABLE coach_profile (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT    NOT NULL UNIQUE REFERENCES app_user(id),
    bio       TEXT,
    specialty VARCHAR(200)
);

CREATE TABLE availability (
    id        BIGSERIAL  PRIMARY KEY,
    coach_id  BIGINT     NOT NULL REFERENCES app_user(id),
    starts_at TIMESTAMP  NOT NULL,
    ends_at   TIMESTAMP  NOT NULL,
    booked    BOOLEAN    NOT NULL DEFAULT FALSE
);

CREATE TABLE booking (
    id              BIGSERIAL  PRIMARY KEY,
    client_id       BIGINT     NOT NULL REFERENCES app_user(id),
    coach_id        BIGINT     NOT NULL REFERENCES app_user(id),
    availability_id BIGINT     NOT NULL UNIQUE REFERENCES availability(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    created_at      TIMESTAMP  NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_message (
    id         BIGSERIAL PRIMARY KEY,
    booking_id BIGINT    NOT NULL REFERENCES booking(id),
    sender_id  BIGINT    NOT NULL REFERENCES app_user(id),
    content    TEXT      NOT NULL,
    sent_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
