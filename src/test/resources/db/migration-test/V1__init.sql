-- H2-compatible schema for tests

CREATE TABLE exercises (
    id                UUID PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    level             VARCHAR(255),
    force             VARCHAR(255),
    mechanic          VARCHAR(255),
    equipment         VARCHAR(255),
    category          VARCHAR(255),
    primary_muscles   VARCHAR ARRAY,
    secondary_muscles VARCHAR ARRAY,
    instructions      VARCHAR ARRAY
);

CREATE TABLE users (
    id                          UUID PRIMARY KEY DEFAULT random_uuid(),
    workos_id                   VARCHAR(255) NOT NULL UNIQUE,
    username                    VARCHAR(255),
    timezone                    VARCHAR(255),
    active_routine_id           UUID,
    registration_completed      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  DATE,
    weight_kg                   DOUBLE PRECISION,
    height_cm                   INTEGER,
    current_streak              INTEGER NOT NULL DEFAULT 0,
    longest_streak              INTEGER NOT NULL DEFAULT 0,
    next_workout_id             UUID,
    next_workout_deadline       DATE,
    streak_freeze_count         INTEGER NOT NULL DEFAULT 0,
    last_workout_counted_date   DATE,
    current_week_start          DATE,
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_timezone ON users(timezone);

CREATE TABLE user_push_tokens (
    id      UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id UUID NOT NULL,
    token   VARCHAR(255) NOT NULL,
    UNIQUE (user_id, token)
);

CREATE TABLE user_notification_schedule (
    id            UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id       UUID NOT NULL,
    schedule_time TIME NOT NULL
);

CREATE INDEX idx_notification_schedule_user ON user_notification_schedule(user_id);

CREATE TABLE user_weekly_completed_workouts (
    id         UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id    UUID NOT NULL,
    workout_id UUID NOT NULL,
    UNIQUE (user_id, workout_id)
);

CREATE TABLE workout_templates (
    id         UUID PRIMARY KEY DEFAULT random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workouts (
    id          UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id     UUID NOT NULL,
    title       VARCHAR(255),
    notes       VARCHAR(2000),
    template_id UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workouts_user ON workouts(user_id);

CREATE TABLE template_exercises (
    id               UUID PRIMARY KEY DEFAULT random_uuid(),
    template_id      UUID NOT NULL,
    exercise_id      UUID NOT NULL,
    ordinal          INTEGER NOT NULL,
    reps             INTEGER ARRAY,
    weight           DOUBLE PRECISION ARRAY,
    duration_seconds INTEGER ARRAY,
    notes            VARCHAR(2000)
);

CREATE INDEX idx_template_exercises_template ON template_exercises(template_id);

CREATE TABLE workout_records (
    id            UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id       UUID NOT NULL,
    workout_id    UUID NOT NULL,
    workout_title VARCHAR(255),
    notes         VARCHAR(2000),
    start_time    TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workout_records_user ON workout_records(user_id);
CREATE INDEX idx_workout_records_workout ON workout_records(workout_id);

CREATE TABLE exercise_records (
    id                          UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id                     UUID NOT NULL,
    workout_record_id           UUID NOT NULL,
    exercise_id                 UUID NOT NULL,
    ordinal                     INTEGER NOT NULL DEFAULT 0,
    reps                        INTEGER ARRAY,
    weight                      DOUBLE PRECISION ARRAY,
    duration_seconds            INTEGER ARRAY,
    notes                       VARCHAR(2000),
    achieved_one_rm_value       DOUBLE PRECISION,
    achieved_one_rm_set_index   INTEGER,
    achieved_total_volume_value DOUBLE PRECISION,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exercise_records_user_exercise ON exercise_records(user_id, exercise_id);
CREATE INDEX idx_exercise_records_workout_record ON exercise_records(workout_record_id);

CREATE TABLE exercise_personal_bests (
    id                      UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id                 UUID NOT NULL,
    exercise_id             UUID NOT NULL,
    one_rm                  DOUBLE PRECISION,
    one_rm_record_id        UUID,
    one_rm_record_set_index INTEGER,
    total_volume            DOUBLE PRECISION,
    total_volume_record_id  UUID,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, exercise_id)
);

CREATE TABLE routines (
    id           UUID PRIMARY KEY DEFAULT random_uuid(),
    user_id      UUID NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(2000),
    routine_type VARCHAR(255) NOT NULL DEFAULT 'SEQUENTIAL',
    start_date   TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_routines_user ON routines(user_id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_active_routine
    FOREIGN KEY (active_routine_id) REFERENCES routines(id) ON DELETE SET NULL;

CREATE TABLE routine_patterns (
    id         UUID PRIMARY KEY DEFAULT random_uuid(),
    routine_id UUID NOT NULL,
    day_index  INTEGER NOT NULL,
    day_type   VARCHAR(255) NOT NULL,
    workout_id UUID
);

CREATE INDEX idx_routine_patterns_routine ON routine_patterns(routine_id);

