/**
 * توليد رابط تحميل مؤقت (Signed URL) للـ APK من Supabase Storage
 * الرابط صالح لمدة 24 ساعة
 */
const { createClient } = require('@supabase/supabase-js');

const DB_URL = 'https://kubowqqqawkgghxcktoe.supabase.co';
const DB_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(DB_URL, DB_KEY);

async function getLink() {
    // جلب آخر إصدار من remote_settings
    const { data: settings } = await supabase
        .from('remote_settings')
        .select('target_version, update_apk_path')
        .order('target_version', { ascending: false })
        .limit(1)
        .maybeSingle();

    const apkPath = settings?.update_apk_path || 'apk/update_v4.apk';
    const version = settings?.target_version || 4;

    // توليد Signed URL صالح 24 ساعة
    const { data, error } = await supabase.storage
        .from('updates')
        .createSignedUrl(apkPath, 60 * 60 * 24); // 24h

    if (error || !data?.signedUrl) {
        console.error('❌ فشل توليد الرابط:', error?.message);
        console.log('\n→ جرب تشغيل update_schema.sql في Supabase أولاً');
        return;
    }

    console.log('\n══════════════════════════════════════════');
    console.log(`  📱 رابط تحميل APK v${version}`);
    console.log('══════════════════════════════════════════');
    console.log('\n' + data.signedUrl);
    console.log('\n→ افتح هذا الرابط من متصفح الهاتف مباشرةً');
    console.log('→ الرابط صالح لمدة 24 ساعة');
    console.log('══════════════════════════════════════════\n');
}

getLink().catch(console.error);
