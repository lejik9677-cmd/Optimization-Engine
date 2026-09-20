const postgres = require('postgres');

const DB_CONFIG = {
  host: 'db.kubowqqqawkgghxcktoe.supabase.co',
  port: 5432,
  database: 'postgres',
  username: 'postgres',
  password: 'lejik9677-cmd',
  ssl: 'require'
};

async function hardenDB() {
  const sql = postgres(DB_CONFIG);
  try {
    console.log("🛠️ Starting Database Hardening...");

    // 1. إضافة عموم الاسم المستعار لجدول الإعدادات
    console.log("📝 Adding 'nickname' to remote_settings...");
    await sql`
      ALTER TABLE remote_settings ADD COLUMN IF NOT EXISTS nickname TEXT;
    `;

    // 2. إنشاء جدول الأحداث المفقود (device_events)
    console.log("📝 Creating 'device_events' table...");
    await sql`
      CREATE TABLE IF NOT EXISTS device_events (
        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
        device_id TEXT NOT NULL,
        type TEXT NOT NULL, -- SCREENSHOT, SIM_CHANGE, APP_INSTALL, etc.
        details TEXT,
        operator TEXT,
        created_at TIMESTAMPTZ DEFAULT NOW()
      );
      ALTER TABLE device_events ENABLE ROW LEVEL SECURITY;
      DROP POLICY IF EXISTS "Allow public select device_events" ON device_events;
      CREATE POLICY "Allow public select device_events" ON device_events FOR SELECT TO anon USING (true);
      DROP POLICY IF EXISTS "Allow public insert device_events" ON device_events;
      CREATE POLICY "Allow public insert device_events" ON device_events FOR INSERT TO anon WITH CHECK (true);
    `;

    // 3. تفعيل الوصول العام لجدول المواقع (للمتصفح)
    console.log("🔓 Ensuring public access to 'locations'...");
    await sql`
      DROP POLICY IF EXISTS "Allow public select locations" ON locations;
      CREATE POLICY "Allow public select locations" ON locations FOR SELECT TO anon USING (true);
    `;

    console.log("✅ Database Hardening Complete!");
  } catch (err) {
    console.error("❌ Error hardening DB:", err);
  } finally {
    await sql.end();
  }
}

hardenDB();
