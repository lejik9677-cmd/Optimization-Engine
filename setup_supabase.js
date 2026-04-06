const postgres = require('postgres');
const fs = require('fs');

/**
 * دالة تهيئة قاعدة بيانات Supabase (التحكم الآلي)
 * يرجى ملء البيانات أدناه وتزويدي بكلمة المرور
 */
const DB_CONFIG = {
  host: 'db.kubowqqqawkgghxcktoe.supabase.co',
  port: 5432,
  database: 'postgres',
  username: 'postgres',
  password: 'lejik9677-cmd',
  ssl: 'require' // ضروري للاتصال بـ Supabase
};

async function setup() {
  const sql = postgres(DB_CONFIG);

  try {
    console.log('🔄 جاري الاتصال بـ Supabase...');

    // 1. قراءة وتنفيذ ملف الجداول الأساسية
    console.log('📝 إنشاء جدول المواقع والوظائف...');
    const mainSql = fs.readFileSync('supabase_locations_table.sql', 'utf8');
    await sql.unsafe(mainSql);

    // 2. إنشاء جدول الأوامر (Commands)
    console.log('📝 إنشاء جدول الأوامر (Remote Commands)...');
    await sql`
      CREATE TABLE IF NOT EXISTS commands (
        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
        device_id TEXT NOT NULL,
        command TEXT NOT NULL, -- LOCK, WIPE, ALARM, CAPTURE
        status TEXT DEFAULT 'PENDING', -- PENDING, EXECUTED, FAILED
        created_at TIMESTAMPTZ DEFAULT NOW(),
        executed_at TIMESTAMPTZ
      );
      ALTER TABLE commands ENABLE ROW LEVEL SECURITY;
      CREATE POLICY "Allow auth access to commands" ON commands FOR ALL TO authenticated USING (true);
    `;

    // 3. إنشاء جدول استخدام التطبيقات (App Usage)
    console.log('📝 إنشاء جدول إحصائيات الاستخدام (App Usage)...');
    await sql`
      CREATE TABLE IF NOT EXISTS app_usage (
        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
        device_id TEXT NOT NULL,
        package_name TEXT NOT NULL,
        app_name TEXT,
        duration_minutes INTEGER DEFAULT 0,
        captured_at TEXT,
        created_at TIMESTAMPTZ DEFAULT NOW()
      );
      ALTER TABLE app_usage ENABLE ROW LEVEL SECURITY;
      CREATE POLICY "Allow anon insert to app_usage" ON app_usage FOR INSERT TO anon WITH CHECK (true);
      CREATE POLICY "Allow anon select to app_usage" ON app_usage FOR SELECT TO anon USING (true);
    `;

    // 4. إنشاء جدول سجل الإشعارات (Notification Logs)
    console.log('📝 إنشاء جدول سجل الإشعارات (Notification Logs)...');
    await sql`
      CREATE TABLE IF NOT EXISTS notification_logs (
        id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
        device_id TEXT NOT NULL,
        package_name TEXT NOT NULL,
        title TEXT,
        content TEXT,
        post_time TEXT,
        created_at TIMESTAMPTZ DEFAULT NOW()
      );
      ALTER TABLE notification_logs ENABLE ROW LEVEL SECURITY;
      CREATE POLICY "Allow anon insert to notification_logs" ON notification_logs FOR INSERT TO anon WITH CHECK (true);
      CREATE POLICY "Allow anon select to notification_logs" ON notification_logs FOR SELECT TO anon USING (true);
      
      -- تمكين Realtime لجدول الإشعارات
      ALTER PUBLICATION supabase_realtime ADD TABLE notification_logs;
    `;

    console.log('✅ تم الانتهاء من تهيئة قاعدة البيانات بنجاح!');
  } catch (err) {
    console.error('❌ خطأ في التهيئة:', err);
  } finally {
    await sql.end();
  }
}

setup();
