const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

async function run() {
    console.log("Checking remote_settings table schema information...");
    
    // We can run an RPC or check if we can query details.
    // Since we don't have SQL execution direct access right now, we can check by querying a row
    // and looking at the properties returned.
    const { data, error } = await supabase
        .from('remote_settings')
        .select('*')
        .limit(1);

    if (error) {
        console.error("Error querying remote_settings:", error.message);
    } else {
        console.log("\n--- remote_settings columns ---");
        if (data && data.length > 0) {
            const row = data[0];
            Object.keys(row).forEach(key => {
                console.log(`- ${key}: ${typeof row[key]} (value: ${row[key]})`);
            });
        } else {
            console.log("(no rows in remote_settings to analyze)");
        }
    }

    // Let's also check if we can write a row directly to locations table
    console.log("\nTesting if we can insert to locations table...");
    const locRes = await supabase.from('locations').insert({
        device_id: 'test-device-id-999',
        latitude: 31.639,
        longitude: -8.061,
        accuracy: 10,
        battery_level: 99,
        timestamp: new Date().toISOString()
    });
    console.log("Locations insert status:", locRes.status, locRes.error?.message || "Success");

    // Clean it up immediately
    if (locRes.status === 201 || locRes.status === 200) {
        await supabase.from('locations').delete().eq('device_id', 'test-device-id-999');
    }
}

run().catch(console.error);
