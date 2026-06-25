-- سياسة إدراج الأجهزة الجديدة
DROP POLICY IF EXISTS "Allow anon insert to remote_settings" ON remote_settings;
CREATE POLICY "Allow anon insert to remote_settings" ON remote_settings FOR INSERT TO anon WITH CHECK (true);
