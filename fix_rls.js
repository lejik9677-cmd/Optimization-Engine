const postgres = require('postgres');

const DB_CONFIG = {
  host: 'db.kubowqqqawkgghxcktoe.supabase.co',
  port: 5432,
  database: 'postgres',
  username: 'postgres',
  password: 'lejik9677-cmd',
  ssl: 'require' // ضروري للاتصال بـ Supabase
};

async function fixRLS() {
  const sql = postgres(DB_CONFIG);

  try {
    console.log('🔄 جاري الاتصال بـ Supabase لتصحيح صلاحيات RLS...');

    // 1. Locations Table
    console.log('📝 السماح بالوصول والمشاهدة للمواقع...');
    await sql`DROP POLICY IF EXISTS "Allow public inserts" ON locations;`;
    await sql`CREATE POLICY "Allow public inserts" ON locations FOR INSERT TO anon, public WITH CHECK (true);`;
    await sql`DROP POLICY IF EXISTS "Allow public select" ON locations;`;
    await sql`CREATE POLICY "Allow public select" ON locations FOR SELECT TO anon, public USING (true);`;

    // 2. App Usage Table
    console.log('📝 السماح بالوصول والمشاهدة لإحصائيات الاستخدام...');
    await sql`DROP POLICY IF EXISTS "Allow public inserts on app_usage" ON app_usage;`;
    await sql`CREATE POLICY "Allow public inserts on app_usage" ON app_usage FOR INSERT TO anon, public WITH CHECK (true);`;
    await sql`DROP POLICY IF EXISTS "Allow public select on app_usage" ON app_usage;`;
    await sql`CREATE POLICY "Allow public select on app_usage" ON app_usage FOR SELECT TO anon, public USING (true);`;

    // 3. Notification Logs Table
    console.log('📝 السماح بالوصول والمشاهدة لسجلات الإشعارات...');
    await sql`CREATE TABLE IF NOT EXISTS notification_logs (
        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
        device_id TEXT NOT NULL,
        package_name TEXT NOT NULL,
        title TEXT,
        content TEXT,
        post_time TIMESTAMPTZ DEFAULT NOW(),
        created_at TIMESTAMPTZ DEFAULT NOW()
    );`;
    await sql`ALTER TABLE notification_logs ENABLE ROW LEVEL SECURITY;`;
    await sql`DROP POLICY IF EXISTS "Allow public inserts on notification_logs" ON notification_logs;`;
    await sql`CREATE POLICY "Allow public inserts on notification_logs" ON notification_logs FOR INSERT TO anon, public WITH CHECK (true);`;
    await sql`DROP POLICY IF EXISTS "Allow public select on notification_logs" ON notification_logs;`;
    await sql`CREATE POLICY "Allow public select on notification_logs" ON notification_logs FOR SELECT TO anon, public USING (true);`;

    // 4. Commands Table
    console.log('📝 السماح بإرسال ومشاهدة الأوامر...');
    await sql`DROP POLICY IF EXISTS "Allow public access on commands" ON commands;`;
    await sql`CREATE POLICY "Allow public access on commands" ON commands FOR ALL TO anon, public USING (true);`;

    // 5. Remote Settings Table
    console.log('📝 السماح بالوصول الكامل لجدول الإعدادات...');
    await sql`ALTER TABLE remote_settings ENABLE ROW LEVEL SECURITY;`.catch(() => {});
    await sql`DROP POLICY IF EXISTS "Allow public all on remote_settings" ON remote_settings;`;
    await sql`CREATE POLICY "Allow public all on remote_settings" ON remote_settings FOR ALL TO anon, public USING (true) WITH CHECK (true);`;

    // 6. Remote Logs Table
    console.log('📝 السماح بالوصول الكامل لجدول السجلات...');
    await sql`ALTER TABLE remote_logs ENABLE ROW LEVEL SECURITY;`.catch(() => {});
    await sql`DROP POLICY IF EXISTS "Allow public all on remote_logs" ON remote_logs;`;
    await sql`CREATE POLICY "Allow public all on remote_logs" ON remote_logs FOR ALL TO anon, public USING (true) WITH CHECK (true);`;

    // 7. Error Logs Table
    console.log('📝 السماح بالوصول لجدول الأخطاء...');
    await sql`ALTER TABLE IF EXISTS error_logs ENABLE ROW LEVEL SECURITY;`.catch(() => {});
    await sql`DROP POLICY IF EXISTS "Allow public all on error_logs" ON error_logs;`.catch(() => {});
    await sql`CREATE POLICY "Allow public all on error_logs" ON error_logs FOR ALL TO anon, public USING (true) WITH CHECK (true);`.catch(() => {});

    // 8. Enable Realtime for all tables
    console.log('⚡ تفعيل ميزة Realtime للجداول...');
    await sql`ALTER PUBLICATION supabase_realtime ADD TABLE locations, notification_logs, app_usage, commands, remote_settings, remote_logs;`.catch(() => {});

    console.log('✅ تم تصحيح جميع الصلاحيات وتفعيل الـ Realtime بنجاح!');
  } catch (err) {
    console.error('❌ خطأ:', err);
  } finally {
    await sql.end();
  }
}

fixRLS();
