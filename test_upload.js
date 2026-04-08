const fs = require('fs');
const path = require('path');

async function testUpload() {
    const fetch = (await import('node-fetch')).default;
    
    const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
    const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';

    // Create a dummy image buffer (just text for testing)
    const fileBuf = Buffer.from('dummy image data');

    const res = await fetch(`${dbUrl}/storage/v1/object/monitoring_data/screenshots/test_upload.webp`, {
        method: 'POST',
        headers: {
            'apikey': dbKey,
            'Authorization': `Bearer ${dbKey}`,
            'Content-Type': 'image/webp'
        },
        body: fileBuf
    });

    console.log("Upload Status:", res.status);
    console.log("Upload Res:", await res.text());
}
testUpload().catch(console.error);
