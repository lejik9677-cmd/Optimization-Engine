const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

async function cleanup() {
    console.log("Cleaning up test device from remote_settings...");
    const res = await supabase.from('remote_settings').delete().eq('device_id', 'test-device-id-999');
    console.log("Status:", res.status);
}

cleanup().catch(console.error);
