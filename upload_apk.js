const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

const db = createClient(
  'https://kubowqqqawkgghxcktoe.supabase.co',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM'
);

async function uploadAndGetLink() {
  const apkPath = 'app/build/outputs/apk/debug/app-debug.apk';
  
  if (!fs.existsSync(apkPath)) {
    console.error('❌ APK not found! Build it first in Android Studio.');
    console.error('   Path checked: ' + apkPath);
    return;
  }
  
  const apk = fs.readFileSync(apkPath);
  const stats = fs.statSync(apkPath);
  const sizeMB = (apk.length / 1024 / 1024).toFixed(1);
  const modified = stats.mtime.toLocaleString();
  
  console.log(`📦 APK found: ${sizeMB} MB (built: ${modified})`);
  console.log('⬆️  Uploading to Supabase...');

  const filename = `apk/optimization-v31.apk`;
  
  const { data, error } = await db.storage
    .from('monitoring_data')
    .upload(filename, apk, {
      upsert: true,
      contentType: 'application/vnd.android.package-archive'
    });

  if (error) {
    console.error('❌ Upload failed:', error.message);
    return;
  }

  const { data: urlData } = db.storage
    .from('monitoring_data')
    .getPublicUrl(filename);

  const publicUrl = urlData.publicUrl;

  console.log('');
  console.log('══════════════════════════════════════════════════════');
  console.log('✅ APK uploaded successfully!');
  console.log(`🔗 Link: ${publicUrl}`);
  
  console.log('🔄 Updating Remote Database (v31)...');
  
  // تحديث جدول الإعدادات لجميع الأجهزة (أو جهازك المحدد) ليفهم التطبيق وجود تحديث
  // ملاحظة: قمنا بزيادة target_version إلى 31
  const { error: dbError } = await db
    .from('remote_settings')
    .update({ 
      target_version: 31, 
      update_apk_url: publicUrl,
      update_apk_path: filename 
    })
    .neq('device_id', 'placeholder'); // تحديث الكل

  if (dbError) {
    console.error('❌ Database update failed:', dbError.message);
  } else {
    console.log('✅ Remote Database synchronized! You can now use the "Update" button in the app.');
  }
  
  console.log('══════════════════════════════════════════════════════');
}

uploadAndGetLink().catch(console.error);
