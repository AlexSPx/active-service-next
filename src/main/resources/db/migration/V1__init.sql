-- V1__init.sql
-- Full PostgreSQL schema for the Active gym-tracking service
-- Migrated from MongoDB document model

-- ============================================================
-- 1. exercises (reference / seed data)
-- ============================================================
CREATE TABLE exercises (
    id                UUID PRIMARY KEY,
    name              TEXT NOT NULL,
    level             TEXT,
    force             TEXT,
    mechanic          TEXT,
    equipment         TEXT,
    category          TEXT,
    primary_muscles   TEXT[],
    secondary_muscles TEXT[],
    instructions      TEXT[]
);

-- ============================================================
-- 2. users
-- ============================================================
CREATE TABLE users (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workos_id                   TEXT NOT NULL UNIQUE,
    username                    TEXT,
    timezone                    TEXT,
    active_routine_id           UUID,
    registration_completed      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  DATE,

    -- Body Measurements (flattened)
    weight_kg                   DOUBLE PRECISION,
    height_cm                   INTEGER,

    -- Streak Info (flattened)
    current_streak              INTEGER NOT NULL DEFAULT 0,
    longest_streak              INTEGER NOT NULL DEFAULT 0,
    next_workout_id             UUID,
    next_workout_deadline       DATE,
    streak_freeze_count         INTEGER NOT NULL DEFAULT 0,
    last_workout_counted_date   DATE,
    current_week_start          DATE,

    -- Notification Preferences (flattened flag)
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_timezone ON users(timezone);

-- ============================================================
-- 3. user_push_tokens
-- ============================================================
CREATE TABLE user_push_tokens (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token   TEXT NOT NULL,
    UNIQUE (user_id, token)
);

-- ============================================================
-- 4. user_notification_schedule
-- ============================================================
CREATE TABLE user_notification_schedule (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    schedule_time TIME NOT NULL
);

CREATE INDEX idx_notification_schedule_user ON user_notification_schedule(user_id);

-- ============================================================
-- 5. user_weekly_completed_workouts
-- ============================================================
CREATE TABLE user_weekly_completed_workouts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_id UUID NOT NULL,
    UNIQUE (user_id, workout_id)
);

-- ============================================================
-- 6. workout_templates
-- ============================================================
CREATE TABLE workout_templates (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- 7. workouts
-- ============================================================
CREATE TABLE workouts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       TEXT,
    notes       TEXT,
    template_id UUID NOT NULL REFERENCES workout_templates(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workouts_user ON workouts(user_id);

-- ============================================================
-- 8. template_exercises
-- ============================================================
CREATE TABLE template_exercises (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id      UUID NOT NULL REFERENCES workout_templates(id) ON DELETE CASCADE,
    exercise_id      UUID NOT NULL REFERENCES exercises(id),
    ordinal          INTEGER NOT NULL,
    reps             INTEGER[],
    weight           DOUBLE PRECISION[],
    duration_seconds INTEGER[],
    notes            TEXT
);

CREATE INDEX idx_template_exercises_template ON template_exercises(template_id);

-- ============================================================
-- 9. workout_records
-- ============================================================
CREATE TABLE workout_records (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_id    UUID REFERENCES workouts(id) ON DELETE SET NULL,
    workout_title TEXT,
    notes         TEXT,
    start_time    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_workout_records_user ON workout_records(user_id);
CREATE INDEX idx_workout_records_workout ON workout_records(workout_id);

-- ============================================================
-- 10. exercise_records
-- ============================================================
CREATE TABLE exercise_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_record_id           UUID NOT NULL REFERENCES workout_records(id) ON DELETE CASCADE,
    exercise_id                 UUID NOT NULL REFERENCES exercises(id),
    ordinal                     INTEGER NOT NULL DEFAULT 0,
    reps                        INTEGER[],
    weight                      DOUBLE PRECISION[],
    duration_seconds            INTEGER[],
    notes                       TEXT,
    achieved_one_rm_value       DOUBLE PRECISION,
    achieved_one_rm_set_index   INTEGER,
    achieved_total_volume_value DOUBLE PRECISION,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_exercise_records_user_exercise ON exercise_records(user_id, exercise_id);
CREATE INDEX idx_exercise_records_workout_record ON exercise_records(workout_record_id);

-- ============================================================
-- 11. exercise_personal_bests
-- ============================================================
CREATE TABLE exercise_personal_bests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id             UUID NOT NULL REFERENCES exercises(id),
    one_rm                  DOUBLE PRECISION,
    one_rm_record_id        UUID,
    one_rm_record_set_index INTEGER,
    total_volume            DOUBLE PRECISION,
    total_volume_record_id  UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, exercise_id)
);

-- ============================================================
-- 12. routines
-- ============================================================
CREATE TABLE routines (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    description  TEXT,
    routine_type TEXT NOT NULL DEFAULT 'SEQUENTIAL',
    start_date   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_routines_user ON routines(user_id);

-- Deferred FK from users to routines
ALTER TABLE users
    ADD CONSTRAINT fk_users_active_routine
    FOREIGN KEY (active_routine_id) REFERENCES routines(id) ON DELETE SET NULL;

-- ============================================================
-- 13. routine_patterns
-- ============================================================
CREATE TABLE routine_patterns (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routine_id UUID NOT NULL REFERENCES routines(id) ON DELETE CASCADE,
    day_index  INTEGER NOT NULL,
    day_type   TEXT NOT NULL,
    workout_id UUID REFERENCES workouts(id) ON DELETE SET NULL
);

CREATE INDEX idx_routine_patterns_routine ON routine_patterns(routine_id);
