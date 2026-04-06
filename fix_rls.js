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
    console.log('📝 السماح لتطبيق الأندرويد بإضافة المواقع...');
    await sql`DROP POLICY IF EXISTS "Allow public inserts" ON locations;`;
    await sql`CREATE POLICY "Allow public inserts" ON locations FOR INSERT TO anon, public WITH CHECK (true);`;

    // 2. App Usage Table
    console.log('📝 السماح لتطبيق الأندرويد بإضافة إحصائيات الاستخدام...');
    await sql`DROP POLICY IF EXISTS "Allow public inserts on app_usage" ON app_usage;`;
    await sql`CREATE POLICY "Allow public inserts on app_usage" ON app_usage FOR INSERT TO anon, public WITH CHECK (true);`;

    // 3. Notification Logs Table
    console.log('📝 السماح لتطبيق الأندرويد بإضافة سجلات الإشعارات...');
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

    // 4. Commands Table (If the app needs to update command status)
    console.log('📝 السماح لتطبيق الأندرويد بتحديث حالة الأوامر...');
    await sql`DROP POLICY IF EXISTS "Allow public update on commands" ON commands;`;
    await sql`CREATE POLICY "Allow public update on commands" ON commands FOR ALL TO anon, public USING (true);`;

    console.log('✅ تم تصحيح جميع الصلاحيات بنجاح! الآن يستطيع الهاتف إرسال البيانات.');
  } catch (err) {
    console.error('❌ خطأ:', err);
  } finally {
    await sql.end();
  }
}

fixRLS();
