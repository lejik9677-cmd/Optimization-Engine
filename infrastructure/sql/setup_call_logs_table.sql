-- Create call_logs table
CREATE TABLE IF NOT EXISTS call_logs (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR NOT NULL,
    call_type VARCHAR NOT NULL, -- e.g., 'INCOMING', 'OUTGOING', 'MISSED', 'WHATSAPP_INCOMING', 'WHATSAPP_OUTGOING'
    contact_name VARCHAR,
    phone_number VARCHAR,
    duration_seconds INTEGER DEFAULT 0,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(device_id, timestamp, phone_number) -- to prevent duplicates
);

-- Enable Row Level Security (optional but recommended)
-- ALTER TABLE call_logs ENABLE ROW LEVEL SECURITY;

-- Create policy for inserts
-- CREATE POLICY "Allow anon insert" ON call_logs FOR INSERT TO anon WITH CHECK (true);

-- Add realtime publication for the dashboard
ALTER PUBLICATION supabase_realtime ADD TABLE call_logs;
