-- ============================================================
-- FIX: Missing 'payload' column in 'commands' table
-- Run this in Supabase Dashboard → SQL Editor
-- ============================================================

-- 1. Add payload column if it doesn't exist
ALTER TABLE commands ADD COLUMN IF NOT EXISTS payload JSONB;

-- 2. Ensure Realtime is enabled for the commands table
-- This allows the dashboard and device to listen for changes
ALTER TABLE commands REPLICA IDENTITY FULL;

-- 3. Check if 'supabase_realtime' publication includes the table
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_publication_tables 
            WHERE pubname = 'supabase_realtime' AND tablename = 'commands'
        ) THEN
            ALTER PUBLICATION supabase_realtime ADD TABLE commands;
        END IF;
    END IF;
END $$;

-- 4. Final verification
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'commands' AND column_name = 'payload';
