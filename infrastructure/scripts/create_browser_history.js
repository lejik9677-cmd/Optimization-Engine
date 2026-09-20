const { Client } = require('pg');

const client = new Client({
  host: 'db.kubowqqqawkgghxcktoe.supabase.co',
  port: 5432,
  database: 'postgres',
  user: 'postgres',
  password: 'lejik9677-cmd',
  ssl: {
    rejectUnauthorized: false
  }
});

async function main() {
  console.log("🔄 Connecting to PostgreSQL using pg...");
  await client.connect();
  console.log("✅ Connected!");

  console.log("➕ Creating browser_history table...");
  await client.query(`
    CREATE TABLE IF NOT EXISTS browser_history (
        id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
        device_id       TEXT        NOT NULL,
        browser         TEXT        NOT NULL,
        url             TEXT        NOT NULL,
        title           TEXT,
        visited_at      TIMESTAMPTZ DEFAULT NOW(),
        created_at      TIMESTAMPTZ DEFAULT NOW()
    );
  `);
  
  console.log("➕ Creating index on browser_history...");
  await client.query(`
    CREATE INDEX IF NOT EXISTS idx_browser_history_device
        ON browser_history (device_id, visited_at DESC);
  `);

  console.log("➕ Disabling RLS for browser_history...");
  await client.query(`
    ALTER TABLE browser_history DISABLE ROW LEVEL SECURITY;
  `);

  console.log("✅ browser_history table created successfully!");
  await client.end();
}

main().catch(async (err) => {
  console.error("❌ Error:", err);
  try {
    await client.end();
  } catch {}
});
