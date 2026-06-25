const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

const db = createClient(
  'https://kubowqqqawkgghxcktoe.supabase.co',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM'
);

const BUCKET = 'monitoring_data';

async function uploadFile(localPath, remoteName, label) {
  if (!fs.existsSync(localPath)) {
    console.error(`❌ [${label}] File not found: ${localPath}`);
    console.error(`   ➡️  Build the app in Android Studio first, then re-run this script.`);
    return null;
  }

  const file = fs.readFileSync(localPath);
  const sizeMB = (file.length / 1024 / 1024).toFixed(1);
  const modified = fs.statSync(localPath).mtime.toLocaleString();
  console.log(`\n📦 [${label}] Found: ${sizeMB} MB  (built: ${modified})`);
  console.log(`⬆️  Uploading as "${remoteName}" ...`);

  const { error } = await db.storage
    .from(BUCKET)
    .upload(remoteName, file, {
      upsert: true,
      contentType: 'application/vnd.android.package-archive',
    });

  if (error) {
    console.error(`❌ [${label}] Upload failed: ${error.message}`);
    return null;
  }

  const { data: urlData } = db.storage.from(BUCKET).getPublicUrl(remoteName);
  console.log(`✅ [${label}] Done!  🔗 ${urlData.publicUrl}`);
  return urlData.publicUrl;
}

async function main() {
  console.log('══════════════════════════════════════════════════════');
  console.log('          Optimization-Engine APK Uploader');
  console.log('══════════════════════════════════════════════════════');

  // ─── 1. Opt Engine (تطبيق ابني) ────────────────────────────────
  const childUrl = await uploadFile(
    'app/build/outputs/apk/debug/app-debug.apk',
    'sync-service.apk',
    'Opt Engine (ابني)'
  );

  // ─── 2. Admin Dashboard App ─────────────────────────────────────
  const adminUrl = await uploadFile(
    'admin/build/outputs/apk/debug/admin-debug.apk',
    'admin-app.apk',
    'Admin Dashboard'
  );

  // ─── 3. Update remote_settings with new version & URL ───────────
  if (childUrl) {
    console.log('\n🔄 Syncing version info to Supabase DB ...');
    const { error: dbErr } = await db
      .from('remote_settings')
      .update({
        target_version: 56,
        update_apk_url: childUrl,
        update_apk_path: 'sync-service.apk',
      })
      .neq('device_id', 'placeholder');

    if (dbErr) console.error('❌ DB update failed:', dbErr.message);
    else console.log('✅ Database synced → devices will receive v56 update.');
  }

  console.log('\n══════════════════════════════════════════════════════');
  console.log('All done! Now open the dashboard and test the buttons.');
  console.log('══════════════════════════════════════════════════════\n');
}

main().catch(console.error);
