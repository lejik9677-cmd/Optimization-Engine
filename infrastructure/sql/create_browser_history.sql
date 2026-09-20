-- إنشاء جدول سجل المتصفح (browser_history)
CREATE TABLE IF NOT EXISTS browser_history (
    id              UUID        DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id       TEXT        NOT NULL,
    browser         TEXT        NOT NULL,
    url             TEXT        NOT NULL,
    title           TEXT,
    visited_at      TIMESTAMPTZ DEFAULT NOW(),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- إنشاء فهرس لتسريع البحث باستخدام معرف الجهاز
CREATE INDEX IF NOT EXISTS idx_browser_history_device
    ON browser_history (device_id, visited_at DESC);

-- إيقاف تشغيل حماية RLS لكي يتمكن التطبيق من الإضافة بدون مصادقة
ALTER TABLE browser_history DISABLE ROW LEVEL SECURITY;
