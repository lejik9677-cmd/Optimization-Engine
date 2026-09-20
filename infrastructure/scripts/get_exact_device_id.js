const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

async function run() {
    console.log("Searching for the exact device ID of the second device (starting with e3f7241c)...");
    
    // 1. From remote_logs
    const { data: logs, error: logErr } = await supabase
        .from('remote_logs')
        .select('device_id')
        .neq('device_id', 'c8346e5011f7103c')
        .limit(10);
        
    if (logErr) {
        console.error("Error in logs query:", logErr);
    }

    // 2. From locations
    const { data: locs, error: locErr } = await supabase
        .from('locations')
        .select('device_id')
        .neq('device_id', 'c8346e5011f7103c')
        .limit(10);
        
    if (locErr) {
        console.error("Error in locations query:", locErr);
    }
        
    const deviceIds = new Set();
    if (logs) logs.forEach(l => deviceIds.add(l.device_id));
    if (locs) locs.forEach(l => deviceIds.add(l.device_id));
    
    console.log("\nFound unique device IDs (excluding c8346e5011f7103c):");
    deviceIds.forEach(id => {
        console.log(`- ${id}`);
    });
}

run().catch(console.error);
