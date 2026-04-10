const postgres = require('postgres');
const fs = require('fs');

const DB_CONFIG = {
  host: 'db.kubowqqqawkgghxcktoe.supabase.co',
  port: 5432,
  database: 'postgres',
  username: 'postgres',
  password: 'lejik9677-cmd',
  ssl: 'require'
};

async function execute() {
  const sql = postgres(DB_CONFIG);
  try {
    console.log('🔄 Connecting to Database...');
    const sqlContent = fs.readFileSync('update_schema.sql', 'utf8');
    
    console.log('🚀 Executing SQL from update_schema.sql...');
    // We execute line by line or as a block if possible. postgres.js handles blocks well.
    await sql.unsafe(sqlContent);
    
    console.log('✅ SQL Execution Successful!');
    
    // Verification
    console.log('🔍 Verifying column "location_interval_ms" in "remote_settings"...');
    const result = await sql`
      SELECT column_name 
      FROM information_schema.columns 
      WHERE table_name = 'remote_settings' AND column_name = 'location_interval_ms'
    `;
    
    if (result.length > 0) {
      console.log('✨ Column verified: location_interval_ms exists.');
    } else {
      console.error('❌ Column not found after execution.');
    }

  } catch (err) {
    console.error('❌ Error executing SQL:', err);
  } finally {
    await sql.end();
  }
}

execute();
