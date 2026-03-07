ALTER TABLE coach_profile
    RENAME COLUMN bio TO description;

ALTER TABLE coach_profile
    ADD COLUMN picture_url TEXT;

CREATE TABLE coach_social_link (
    id       BIGSERIAL PRIMARY KEY,
    coach_id BIGINT       NOT NULL REFERENCES coach_profile(id) ON DELETE CASCADE,
    type     VARCHAR(20)  NOT NULL,
    url      TEXT         NOT NULL,
    label    VARCHAR(100)
);
