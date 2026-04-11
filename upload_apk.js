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

  const filename = `apk/sync-service-v22-diag.apk`;
  
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

  console.log('');
  console.log('══════════════════════════════════════════════════════');
  console.log('✅ APK uploaded successfully!');
  console.log('');
  console.log('🔗 رابط التحميل:');
  console.log(urlData.publicUrl);
  console.log('══════════════════════════════════════════════════════');
}

uploadAndGetLink().catch(console.error);
