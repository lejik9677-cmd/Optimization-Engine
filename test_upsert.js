const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

async function test() {
    console.log("Testing UPSERT into remote_settings with anon key...");
    const res = await supabase.from('remote_settings').upsert({
        device_id: 'test-device-id-999',
        current_version_code: 35,
        device_info: 'Test Device Info',
        updated_at: new Date().toISOString()
    }, { onConflict: 'device_id' });
    
    console.log("\n--- RESULT ---");
    console.log("Status:", res.status);
    console.log("Status Text:", res.statusText);
    console.log("Error:", res.error);
    console.log("Data:", res.data);
}

test().catch(console.error);
