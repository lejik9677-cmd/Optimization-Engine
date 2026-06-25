const postgres = require('postgres');

const DB_CONFIG = {
  host: 'db.kubowqqqawkgghxcktoe.supabase.co',
  port: 5432,
  database: 'postgres',
  username: 'postgres',
  password: 'lejik9677-cmd',
  ssl: 'require'
};

async function fixRLS() {
  const sql = postgres(DB_CONFIG);
  try {
    console.log("🔓 Updating RLS policies for Public Access...");

    // 1. تفعيل الوصول العام لجدول المواقع (لعرض الأجهزة)
    await sql`
      DROP POLICY IF EXISTS "Allow anon select to locations" ON locations;
      CREATE POLICY "Allow anon select to locations" ON locations FOR SELECT TO anon USING (true);
      
      DROP POLICY IF EXISTS "Allow anon insert to locations" ON locations;
      CREATE POLICY "Allow anon insert to locations" ON locations FOR INSERT TO anon WITH CHECK (true);
    `;

    // 2. تفعيل الوصول العام لجدول الإعدادات
    await sql`
      DROP POLICY IF EXISTS "Allow anon select to remote_settings" ON remote_settings;
      CREATE POLICY "Allow anon select to remote_settings" ON remote_settings FOR SELECT TO anon USING (true);
      
      DROP POLICY IF EXISTS "Allow anon update to remote_settings" ON remote_settings;
      CREATE POLICY "Allow anon update to remote_settings" ON remote_settings FOR UPDATE TO anon USING (true) WITH CHECK (true);

      DROP POLICY IF EXISTS "Allow anon insert to remote_settings" ON remote_settings;
      CREATE POLICY "Allow anon insert to remote_settings" ON remote_settings FOR INSERT TO anon WITH CHECK (true);
    `;

    // 3. تفعيل الوصول العام لجدول الأحداث
    await sql`
      DROP POLICY IF EXISTS "Allow anon select to device_events" ON device_events;
      CREATE POLICY "Allow anon select to device_events" ON device_events FOR SELECT TO anon USING (true);
    `;

    console.log("✅ RLS policies fixed! Devices should now appear in the dashboard.");
  } catch (err) {
    console.error("❌ Error fixing RLS:", err);
  } finally {
    await sql.end();
  }
}

fixRLS();
