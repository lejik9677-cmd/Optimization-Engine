const { Client } = require('pg');

const client = new Client({
  host: '2600:1f16:1cd0:3340:1b32:5027:ffb1:a574',
  port: 5432,
  database: 'postgres',
  user: 'postgres',
  password: 'lejik9677-cmd',
  ssl: {
    rejectUnauthorized: false
  }
});

async function main() {
  console.log("🔄 Connecting to PostgreSQL using pg via IPv6...");
  await client.connect();
  console.log("✅ Connected!");
  
  console.log("➕ Adding missing columns: recording_enabled, capture_interval...");
  await client.query(`
    ALTER TABLE remote_settings 
    ADD COLUMN IF NOT EXISTS recording_enabled BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS capture_interval INTEGER DEFAULT 60000;
  `);
  console.log("✅ Columns added successfully!");
  
  const res = await client.query(`
    SELECT column_name, data_type 
    FROM information_schema.columns 
    WHERE table_schema = 'public' AND table_name = 'remote_settings';
  `);
  console.log("\n--- Current Columns in remote_settings ---");
  res.rows.forEach(row => {
    console.log(`- ${row.column_name}: ${row.data_type}`);
  });
  
  await client.end();
}

main().catch(async (err) => {
  console.error("❌ Error:", err);
  try {
    await client.end();
  } catch {}
});
