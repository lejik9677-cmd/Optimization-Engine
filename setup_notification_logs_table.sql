-- إنشاء جدول سجلات الإشعارات (notification_logs)
CREATE TABLE IF NOT EXISTS notification_logs (
    id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id       TEXT        NOT NULL,
    package_name    TEXT        NOT NULL,
    app_name        TEXT        NOT NULL,
    title           TEXT        NOT NULL,
    content         TEXT        NOT NULL,
    post_time       TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- إنشاء فهرس لتسريع البحث باستخدام معرف الجهاز
CREATE INDEX IF NOT EXISTS idx_notification_logs_device
    ON notification_logs (device_id, post_time DESC);

-- إيقاف تشغيل حماية RLS لكي يتمكن التطبيق من الإضافة بدون مصادقة
ALTER TABLE notification_logs DISABLE ROW LEVEL SECURITY;

-- تفعيل البث المباشر (Realtime) للجدول
ALTER PUBLICATION supabase_realtime ADD TABLE notification_logs;
