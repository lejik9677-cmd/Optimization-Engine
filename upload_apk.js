const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

const db = createClient(
  'https://kubowqqqawkgghxcktoe.supabase.co',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM'
);

async function upload() {
  const apk = fs.readFileSync('app/build/outputs/apk/debug/app-debug.apk');
  console.log(`Size: ${(apk.length/1024/1024).toFixed(1)} MB`);

  // Try monitoring_data bucket (already exists and public)
  const { data, error } = await db.storage
    .from('monitoring_data')
    .upload('apk/sync-service-v20.apk', apk, {
      upsert: true,
      contentType: 'application/vnd.android.package-archive'
    });

  if (error) { console.error('Error:', error.message); return; }

  const { data: u } = db.storage.from('monitoring_data').getPublicUrl('apk/sync-service-v20.apk');
  console.log('\n✅ Download URL:\n' + u.publicUrl);
}
upload();
