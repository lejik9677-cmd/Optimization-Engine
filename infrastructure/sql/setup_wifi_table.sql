-- Create the connected_devices table
CREATE TABLE connected_devices (
    id SERIAL PRIMARY KEY,
    gateway_device_id VARCHAR NOT NULL,
    ssid_name VARCHAR NOT NULL,
    device_name VARCHAR,
    ip_address VARCHAR,
    mac_address VARCHAR NOT NULL,
    is_blocked BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(gateway_device_id, mac_address)
);

-- Enable Row Level Security (optional but recommended)
-- ALTER TABLE connected_devices ENABLE ROW LEVEL SECURITY;

-- Add realtime subscription publication
ALTER PUBLICATION supabase_realtime ADD TABLE connected_devices;
