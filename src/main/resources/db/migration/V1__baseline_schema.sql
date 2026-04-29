-- =============================================================================
-- V1: Baseline schema for MundialFutbol
--
-- Creates the eight tables that back the domain model described in
-- Docs/02-er.puml, together with every declared CHECK / UNIQUE / FOREIGN KEY
-- constraint and the indexes documented in the design.
--
-- Tables are created in dependency order so that foreign keys can be declared
-- inline:
--   1. users     (independent)
--   2. teams     (independent)
--   3. stages    (independent)
--   4. matches         -> stages, teams (x2)
--   5. predictions     -> users, matches
--   6. prediction_scores -> predictions
--   7. user_scores     -> users (shared PK)
--   8. audit_log       -> users (admin)
-- =============================================================================


-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id               BIGSERIAL    PRIMARY KEY,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    full_name        VARCHAR(255) NOT NULL,
    role             VARCHAR(32)  NOT NULL,
    password_changed BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);


-- -----------------------------------------------------------------------------
-- teams
-- -----------------------------------------------------------------------------
CREATE TABLE teams (
    id          BIGSERIAL    PRIMARY KEY,
    external_id INTEGER      NOT NULL,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(10),
    flag_url    VARCHAR(512),
    CONSTRAINT uk_teams_external_id UNIQUE (external_id)
);


-- -----------------------------------------------------------------------------
-- stages
-- -----------------------------------------------------------------------------
CREATE TABLE stages (
    id        BIGSERIAL   PRIMARY KEY,
    name      VARCHAR(64) NOT NULL,
    order_idx INTEGER     NOT NULL,
    CONSTRAINT uk_stages_name UNIQUE (name)
);


-- -----------------------------------------------------------------------------
-- matches
-- -----------------------------------------------------------------------------
CREATE TABLE matches (
    id                BIGSERIAL   PRIMARY KEY,
    external_id       INTEGER     NOT NULL,
    stage_id          BIGINT      NOT NULL,
    home_team_id      BIGINT      NOT NULL,
    away_team_id      BIGINT      NOT NULL,
    kickoff_at        TIMESTAMPTZ NOT NULL,
    status            VARCHAR(16) NOT NULL,
    home_goals        INTEGER,
    away_goals        INTEGER,
    home_penalties    INTEGER,
    away_penalties    INTEGER,
    went_to_penalties BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_matches_external_id UNIQUE (external_id),
    CONSTRAINT fk_matches_stage
        FOREIGN KEY (stage_id)     REFERENCES stages(id) ON DELETE RESTRICT,
    CONSTRAINT fk_matches_home_team
        FOREIGN KEY (home_team_id) REFERENCES teams(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_matches_away_team
        FOREIGN KEY (away_team_id) REFERENCES teams(id)  ON DELETE RESTRICT,
    CONSTRAINT chk_matches_status CHECK (
        status IN ('SCHEDULED', 'LIVE', 'FINISHED', 'ADJUSTED',
                   'POSTPONED', 'SUSPENDED', 'CANCELLED')
    ),
    CONSTRAINT chk_matches_distinct_teams    CHECK (home_team_id <> away_team_id),
    CONSTRAINT chk_matches_home_goals        CHECK (home_goals     IS NULL OR home_goals     >= 0),
    CONSTRAINT chk_matches_away_goals        CHECK (away_goals     IS NULL OR away_goals     >= 0),
    CONSTRAINT chk_matches_home_penalties    CHECK (home_penalties IS NULL OR home_penalties >= 0),
    CONSTRAINT chk_matches_away_penalties    CHECK (away_penalties IS NULL OR away_penalties >= 0),
    CONSTRAINT chk_matches_penalties_consistency CHECK (
        went_to_penalties = FALSE
        OR (home_penalties IS NOT NULL AND away_penalties IS NOT NULL)
    )
);

CREATE INDEX idx_matches_status     ON matches (status);
CREATE INDEX idx_matches_kickoff_at ON matches (kickoff_at);
CREATE INDEX idx_matches_stage_id   ON matches (stage_id);


-- -----------------------------------------------------------------------------
-- predictions
-- -----------------------------------------------------------------------------
CREATE TABLE predictions (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    match_id       BIGINT      NOT NULL,
    home_goals     INTEGER     NOT NULL,
    away_goals     INTEGER     NOT NULL,
    home_penalties INTEGER,
    away_penalties INTEGER,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_predictions_user_match UNIQUE (user_id, match_id),
    CONSTRAINT fk_predictions_user
        FOREIGN KEY (user_id)  REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_predictions_match
        FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    CONSTRAINT chk_predictions_home_goals     CHECK (home_goals >= 0),
    CONSTRAINT chk_predictions_away_goals     CHECK (away_goals >= 0),
    CONSTRAINT chk_predictions_home_penalties CHECK (home_penalties IS NULL OR home_penalties >= 0),
    CONSTRAINT chk_predictions_away_penalties CHECK (away_penalties IS NULL OR away_penalties >= 0),
    CONSTRAINT chk_predictions_penalties_pair CHECK (
        (home_penalties IS NULL) = (away_penalties IS NULL)
    )
);

CREATE INDEX idx_predictions_match_id ON predictions (match_id);


-- -----------------------------------------------------------------------------
-- prediction_scores
-- -----------------------------------------------------------------------------
CREATE TABLE prediction_scores (
    id                 BIGSERIAL   PRIMARY KEY,
    prediction_id      BIGINT      NOT NULL,
    points             INTEGER     NOT NULL,
    exact_score_points INTEGER     NOT NULL,
    winner_points      INTEGER     NOT NULL,
    penalties_points   INTEGER     NOT NULL,
    calculated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_prediction_scores_prediction UNIQUE (prediction_id),
    CONSTRAINT fk_prediction_scores_prediction
        FOREIGN KEY (prediction_id) REFERENCES predictions(id) ON DELETE CASCADE,
    CONSTRAINT chk_prediction_scores_points             CHECK (points             >= 0),
    CONSTRAINT chk_prediction_scores_exact_score_points CHECK (exact_score_points >= 0),
    CONSTRAINT chk_prediction_scores_winner_points      CHECK (winner_points      >= 0),
    CONSTRAINT chk_prediction_scores_penalties_points   CHECK (penalties_points   >= 0),
    CONSTRAINT chk_prediction_scores_sum CHECK (
        points = exact_score_points + winner_points + penalties_points
    )
);


-- -----------------------------------------------------------------------------
-- user_scores  (shared primary key with users)
-- -----------------------------------------------------------------------------
CREATE TABLE user_scores (
    user_id      BIGINT      PRIMARY KEY,
    total_points INTEGER     NOT NULL DEFAULT 0,
    exact_count  INTEGER     NOT NULL DEFAULT 0,
    winner_count INTEGER     NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_scores_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_scores_total_points CHECK (total_points >= 0),
    CONSTRAINT chk_user_scores_exact_count  CHECK (exact_count  >= 0),
    CONSTRAINT chk_user_scores_winner_count CHECK (winner_count >= 0)
);

CREATE INDEX idx_user_scores_ranking
    ON user_scores (total_points DESC, exact_count DESC);


-- -----------------------------------------------------------------------------
-- audit_log
-- -----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id         BIGSERIAL    PRIMARY KEY,
    admin_id   BIGINT       NOT NULL,
    action     VARCHAR(64)  NOT NULL,
    entity     VARCHAR(64)  NOT NULL,
    entity_id  BIGINT,
    details    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_log_admin
        FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_audit_log_entity     ON audit_log (entity, entity_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);
