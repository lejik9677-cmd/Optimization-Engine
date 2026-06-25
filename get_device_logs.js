const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

async function run() {
    console.log("=== DIAGNOSTIC LOGS FOR DEVICE e3f7241c ===");
    
    // 1. Fetch remote logs for the second device
    const { data: logs, error: lErr } = await supabase
        .from('remote_logs')
        .select('*')
        .or('device_id.ilike.%e3f7241c%')
        .order('created_at', { ascending: false })
        .limit(30);
        
    if (lErr) {
        console.error("Error fetching remote logs:", lErr);
    } else {
        console.log(`\n--- REMOTE LOGS (${logs.length} found) ---`);
        logs.forEach(l => {
            console.log(`[${l.created_at}] [${l.level}] [${l.tag}]: ${l.message}`);
        });
    }

    // 2. Fetch error logs if any
    const { data: errLogs, error: eErr } = await supabase
        .from('error_logs')
        .select('*')
        .or('device_id.ilike.%e3f7241c%')
        .order('timestamp', { ascending: false })
        .limit(10);
        
    if (eErr) {
        console.error("\nError fetching error_logs:", eErr.message);
    } else {
        console.log(`\n--- ERROR LOGS (${errLogs.length} found) ---`);
        errLogs.forEach(e => {
            console.log(`[${e.timestamp}] Message: ${e.error_message}`);
            console.log(`Stack Trace: ${e.stack_trace}`);
        });
    }

    // 3. Let's try to fetch remote_settings to see if device exists under similar id
    const { data: settings, error: sErr } = await supabase
        .from('remote_settings')
        .select('*');
        
    if (sErr) {
        console.error("\nError fetching remote_settings:", sErr.message);
    } else {
        console.log(`\n--- ALL DEVICES IN REMOTE_SETTINGS (${settings.length} found) ---`);
        settings.forEach(s => {
            console.log(`Device ID: ${s.device_id}, Nickname: ${s.nickname}, Info: ${s.device_info}, Updated: ${s.updated_at}`);
        });
    }
}

run().catch(console.error);
