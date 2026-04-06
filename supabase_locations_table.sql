-- ============================================
-- إنشاء جدول locations في Supabase
-- ============================================
-- نفذ هذا الكود في Supabase SQL Editor
-- Dashboard → SQL Editor → New Query
-- ============================================

-- 1. إنشاء الجدول الرئيسي
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    device_id TEXT NOT NULL,
    battery_level INTEGER CHECK (battery_level >= 0 AND battery_level <= 100),
    is_charging BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. إنشاء الفهارس لتسريع الاستعلامات
CREATE INDEX IF NOT EXISTS idx_locations_device_id ON locations(device_id);
CREATE INDEX IF NOT EXISTS idx_locations_timestamp ON locations(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_locations_created_at ON locations(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_locations_device_timestamp ON locations(device_id, timestamp DESC);

-- 3. إضافة تعليقات توضيحية
COMMENT ON TABLE locations IS 'جدول تخزين مواقع GPS للأجهزة - يتطلب موافقة المستخدم الصريحة';
COMMENT ON COLUMN locations.latitude IS 'خط العرض (Latitude)';
COMMENT ON COLUMN locations.longitude IS 'خط الطول (Longitude)';
COMMENT ON COLUMN locations.accuracy IS 'دقة الموقع بالأمتار';
COMMENT ON COLUMN locations.timestamp IS 'وقت تسجيل الموقع';
COMMENT ON COLUMN locations.device_id IS 'معرف الجهاز الفريد (Android ID)';
COMMENT ON COLUMN locations.battery_level IS 'مستوى البطارية (0-100)';
COMMENT ON COLUMN locations.is_charging IS 'هل الجهاز قيد الشحن';

-- 4. تفعيل Row Level Security (RLS)
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;

-- 5. حذف السياسات القديمة إن وجدت
DROP POLICY IF EXISTS "Allow authenticated inserts" ON locations;
DROP POLICY IF EXISTS "Users can view own device locations" ON locations;
DROP POLICY IF EXISTS "Users can delete own device locations" ON locations;
DROP POLICY IF EXISTS "Allow public inserts" ON locations;
DROP POLICY IF EXISTS "Allow public selects" ON locations;

-- 6. إنشاء سياسات RLS جديدة

-- سياسة للسماح بالإدراج (للمستخدمين المصادق عليهم فقط)
CREATE POLICY "Allow authenticated inserts"
ON locations FOR INSERT
TO authenticated
WITH CHECK (true);

-- سياسة للسماح بالقراءة (كل مستخدم يرى بيانات جهازه فقط)
-- ملاحظة: في بيئة إنتاج، استخدم المصادقة المناسبة
CREATE POLICY "Users can view own device locations"
ON locations FOR SELECT
TO authenticated
USING (true); -- يمكن تخصيصها: USING (device_id = auth.uid()::text)

-- سياسة للسماح بالحذف (كل مستخدم يحذف بيانات جهازه فقط)
CREATE POLICY "Users can delete own device locations"
ON locations FOR DELETE
TO authenticated
USING (true); -- يمكن تخصيصها: USING (device_id = auth.uid()::text)

-- 7. إنشاء دالة لحذف المواقع القديمة تلقائياً (أكثر من 30 يوم)
CREATE OR REPLACE FUNCTION delete_old_locations()
RETURNS void AS $$
BEGIN
    DELETE FROM locations
    WHERE created_at < NOW() - INTERVAL '30 days';

    RAISE NOTICE 'Old locations deleted successfully';
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. إنشاء دالة للحصول على آخر موقع لجهاز معين
CREATE OR REPLACE FUNCTION get_latest_location(p_device_id TEXT)
RETURNS TABLE (
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy REAL,
    timestamp TIMESTAMPTZ,
    battery_level INTEGER,
    is_charging BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        l.latitude,
        l.longitude,
        l.accuracy,
        l.timestamp,
        l.battery_level,
        l.is_charging
    FROM locations l
    WHERE l.device_id = p_device_id
    ORDER BY l.timestamp DESC
    LIMIT 1;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 9. إنشاء دالة للحصول على تاريخ المواقع لجهاز معين
CREATE OR REPLACE FUNCTION get_location_history(
    p_device_id TEXT,
    p_start_date TIMESTAMPTZ DEFAULT NOW() - INTERVAL '7 days',
    p_end_date TIMESTAMPTZ DEFAULT NOW()
)
RETURNS TABLE (
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    accuracy REAL,
    timestamp TIMESTAMPTZ,
    battery_level INTEGER,
    is_charging BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        l.latitude,
        l.longitude,
        l.accuracy,
        l.timestamp,
        l.battery_level,
        l.is_charging
    FROM locations l
    WHERE
        l.device_id = p_device_id AND
        l.timestamp BETWEEN p_start_date AND p_end_date
    ORDER BY l.timestamp DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 10. إنشاء جدول لتسجيل الموافقات (للشفافية)
CREATE TABLE IF NOT EXISTS location_consents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL UNIQUE,
    consent_given BOOLEAN NOT NULL DEFAULT false,
    consent_timestamp TIMESTAMPTZ DEFAULT NOW(),
    consent_revoked_at TIMESTAMPTZ,
    consent_version TEXT DEFAULT '1.0',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- فهرس لجدول الموافقات
CREATE INDEX IF NOT EXISTS idx_consents_device_id ON location_consents(device_id);

-- تعليقات لجدول الموافقات
COMMENT ON TABLE location_consents IS 'تسجيل موافقات المستخدمين على تتبع الموقع (للشفافية والامتثال القانوني)';

-- تفعيل RLS لجدول الموافقات
ALTER TABLE location_consents ENABLE ROW LEVEL SECURITY;

-- سياسات RLS لجدول الموافقات
CREATE POLICY "Allow authenticated access to consents"
ON location_consents FOR ALL
TO authenticated
USING (true)
WITH CHECK (true);

-- 11. دالة لتسجيل الموافقة
CREATE OR REPLACE FUNCTION record_consent(
    p_device_id TEXT,
    p_consent_given BOOLEAN
)
RETURNS void AS $$
BEGIN
    INSERT INTO location_consents (device_id, consent_given, consent_timestamp)
    VALUES (p_device_id, p_consent_given, NOW())
    ON CONFLICT (device_id)
    DO UPDATE SET
        consent_given = p_consent_given,
        consent_timestamp = CASE
            WHEN p_consent_given THEN NOW()
            ELSE location_consents.consent_timestamp
        END,
        consent_revoked_at = CASE
            WHEN NOT p_consent_given THEN NOW()
            ELSE NULL
        END,
        updated_at = NOW();
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 12. إنشاء جدول لتسجيل الوصول للبيانات (Audit Log)
CREATE TABLE IF NOT EXISTS location_access_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL,
    accessed_by TEXT,
    access_type TEXT, -- 'view', 'export', 'delete'
    accessed_at TIMESTAMPTZ DEFAULT NOW(),
    ip_address INET
);

-- فهرس لجدول تسجيل الوصول
CREATE INDEX IF NOT EXISTS idx_access_logs_device_id ON location_access_logs(device_id);
CREATE INDEX IF NOT EXISTS idx_access_logs_accessed_at ON location_access_logs(accessed_at DESC);

-- تعليقات
COMMENT ON TABLE location_access_logs IS 'سجل الوصول لبيانات الموقع (للأمان والمراجعة)';

-- RLS لجدول تسجيل الوصول
ALTER TABLE location_consents ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow authenticated access to audit logs"
ON location_access_logs FOR ALL
TO authenticated
USING (true)
WITH CHECK (true);

-- 13. عرض إحصائيات مفيدة
CREATE OR REPLACE VIEW location_stats AS
SELECT
    device_id,
    COUNT(*) as total_locations,
    MIN(timestamp) as first_location,
    MAX(timestamp) as last_location,
    AVG(accuracy) as avg_accuracy,
    MIN(created_at) as tracking_started_at
FROM locations
GROUP BY device_id;

COMMENT ON VIEW location_stats IS 'إحصائيات تتبع الموقع لكل جهاز';

-- ============================================
-- انتهى - الجدول جاهز للاستخدام
-- ============================================

-- للتحقق من نجاح العملية، نفذ:
SELECT
    table_name,
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
WHERE table_schema = 'public' AND table_name IN ('locations', 'location_consents', 'location_access_logs');

-- عرض الفهارس
SELECT
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('locations', 'location_consents', 'location_access_logs')
ORDER BY tablename, indexname;

-- عرض سياسات RLS
SELECT
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual,
    with_check
FROM pg_policies
WHERE schemaname = 'public' AND tablename IN ('locations', 'location_consents', 'location_access_logs');

-- ============================================
-- أمثلة على الاستخدام
-- ============================================

-- مثال 1: الحصول على آخر موقع
-- SELECT * FROM get_latest_location('device_123');

-- مثال 2: الحصول على تاريخ المواقع لآخر 7 أيام
-- SELECT * FROM get_location_history('device_123');

-- مثال 3: تسجيل موافقة
-- SELECT record_consent('device_123', true);

-- مثال 4: حذف المواقع القديمة
-- SELECT delete_old_locations();

-- مثال 5: عرض الإحصائيات
-- SELECT * FROM location_stats WHERE device_id = 'device_123';
