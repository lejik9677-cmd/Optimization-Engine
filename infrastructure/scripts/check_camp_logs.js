const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

async function run() {
    console.log("=== REMOTE LOGS FOR DEVICE camp (13e6247e91c50fc9) ===");
    const { data: logs, error } = await supabase
        .from('remote_logs')
        .select('*')
        .eq('device_id', '13e6247e91c50fc9')
        .order('created_at', { ascending: false })
        .limit(50);
        
    if (error) {
        console.error("Error fetching logs:", error);
    } else {
        logs.forEach(l => {
            console.log(`[${l.created_at}] [${l.level}] [${l.tag}]: ${l.message}`);
        });
    }

    console.log("\n=== LOCATIONS FOR DEVICE camp ===");
    const { data: locations, error: locError } = await supabase
        .from('locations')
        .select('*')
        .eq('device_id', '13e6247e91c50fc9')
        .order('timestamp', { ascending: false })
        .limit(10);

    if (locError) {
        console.error("Error fetching locations:", locError);
    } else {
        locations.forEach(loc => {
            console.log(`[${loc.timestamp}] Lat: ${loc.latitude}, Lng: ${loc.longitude}, Acc: ${loc.accuracy}m, Batt: ${loc.battery_level}%`);
        });
    }
}

run().catch(console.error);
