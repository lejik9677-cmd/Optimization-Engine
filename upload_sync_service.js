const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

const db = createClient(
  'https://kubowqqqawkgghxcktoe.supabase.co',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM'
);

async function upload() {
  const apkPath = 'dashboard/sync-service.apk';
  const apk = fs.readFileSync(apkPath);
  
  const { data, error } = await db.storage
    .from('monitoring_data')
    .upload('sync-service.apk', apk, {
      upsert: true,
      contentType: 'application/vnd.android.package-archive'
    });

  if (error) console.error(error);
  else console.log('Uploaded to Supabase Storage successfully.');
}

upload();
