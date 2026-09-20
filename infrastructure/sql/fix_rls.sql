-- السماح للجميع بإدراج المواقع (الموبايل يستخدم anon)
DROP POLICY IF EXISTS "Allow public inserts" ON locations;
CREATE POLICY "Allow public inserts" ON locations FOR INSERT TO anon, public WITH CHECK (true);

-- السماح للجميع بإدراج إحصائيات الاستخدام
DROP POLICY IF EXISTS "Allow public inserts on app_usage" ON app_usage;
CREATE POLICY "Allow public inserts on app_usage" ON app_usage FOR INSERT TO anon, public WITH CHECK (true);

-- السماح للجميع بتحديث حالة الأوامر (حتى يتمكن الموبايل من الإبلاغ عن نجاح الأمر)
DROP POLICY IF EXISTS "Allow public update on commands" ON commands;
CREATE POLICY "Allow public update on commands" ON commands FOR ALL TO anon, public USING (true);
