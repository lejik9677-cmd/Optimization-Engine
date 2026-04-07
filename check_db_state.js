const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

async function checkDevices() {
    console.log("--- Checking Supabase Tables ---");
    
    const { data: settings } = await supabase.from('remote_settings').select('device_id');
    console.log("Remote Settings Devices:", settings);

    const { data: locations } = await supabase.from('locations').select('device_id').limit(5);
    console.log("Locations Table (recent):", locations);

    const { data: events } = await supabase.from('device_events').select('device_id').limit(5);
    console.log("Device Events Table (recent):", events);
}

checkDevices();
