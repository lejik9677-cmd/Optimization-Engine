-- إضافة أعمدة لتتبع الإصدار الحالي ومعلومات الجهاز
ALTER TABLE remote_settings 
ADD COLUMN IF NOT EXISTS current_version_code INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS device_info TEXT DEFAULT 'unknown';

-- تحديث السياسات للتأكد من السماح بالتحديث
DROP POLICY IF EXISTS "anon_update_remote_settings" ON remote_settings;
CREATE POLICY "anon_update_remote_settings" ON remote_settings FOR UPDATE USING (true);
