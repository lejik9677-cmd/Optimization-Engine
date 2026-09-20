const SUPABASE_URL = 'https://kubowqqqawkgghxcktoe.supabase.co';
const SUPABASE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';

async function execute() {
  const deviceId = 'f2b83e559822ccbe';
  console.log(`🔄 Deleting device ${deviceId}...`);

  const headers = {
    'apikey': SUPABASE_KEY,
    'Authorization': `Bearer ${SUPABASE_KEY}`,
    'Content-Type': 'application/json',
    'Prefer': 'return=minimal'
  };

  try {
    // Delete from remote_settings
    const res1 = await fetch(`${SUPABASE_URL}/rest/v1/remote_settings?device_id=eq.${deviceId}`, {
      method: 'DELETE',
      headers
    });
    console.log('remote_settings DELETE status:', res1.status);

    // Delete from locations
    await fetch(`${SUPABASE_URL}/rest/v1/locations?device_id=eq.${deviceId}`, { method: 'DELETE', headers });
    // Delete from remote_logs
    await fetch(`${SUPABASE_URL}/rest/v1/remote_logs?device_id=eq.${deviceId}`, { method: 'DELETE', headers });
    
    console.log('✅ Device and related data deleted successfully via REST.');
  } catch (err) {
    console.error('❌ Error executing fetch:', err);
  }
}

execute();
