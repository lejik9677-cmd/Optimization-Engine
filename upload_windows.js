const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');

const db = createClient(
  'https://kubowqqqawkgghxcktoe.supabase.co',
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM'
);

const BUCKET = 'monitoring_data';

async function main() {
  const localPath = 'D:/MyInstallerPublish/MyMonitorInstaller.exe';
  const remoteName = 'optimization-engine-windows-setup.exe';
  
  if (!fs.existsSync(localPath)) {
    console.error(`❌ File not found: ${localPath}`);
    return;
  }

  const file = fs.readFileSync(localPath);
  const sizeMB = (file.length / 1024 / 1024).toFixed(1);
  console.log(`\n📦 Found setup file: ${sizeMB} MB`);
  console.log(`⬆️ Uploading to Supabase as "${remoteName}" ...`);

  const { error } = await db.storage
    .from(BUCKET)
    .upload(remoteName, file, {
      upsert: true,
      contentType: 'application/octet-stream',
    });

  if (error) {
    console.error(`❌ Upload failed: ${error.message}`);
    return;
  }

  const { data: urlData } = db.storage.from(BUCKET).getPublicUrl(remoteName);
  console.log(`✅ Success!  🔗 ${urlData.publicUrl}`);
}

main().catch(console.error);
