CREATE TABLE client_profile (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE REFERENCES app_user(id),
    description TEXT
);

CREATE TABLE client_social_link (
    id         BIGSERIAL PRIMARY KEY,
    client_id  BIGINT       NOT NULL REFERENCES client_profile(id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL,
    url        TEXT         NOT NULL,
    label      VARCHAR(100)
);
