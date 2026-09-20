const https = require('https');
const DB_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const HOST = 'kubowqqqawkgghxcktoe.supabase.co';

function postJson(path, data) {
    return new Promise((resolve, reject) => {
        const body = JSON.stringify(data);
        const req = https.request({
            hostname: HOST,
            path: path,
            method: 'POST',
            headers: {
                'apikey': DB_KEY,
                'Authorization': 'Bearer ' + DB_KEY,
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(body)
            }
        }, (res) => {
            let s = '';
            res.on('data', c => s += c);
            res.on('end', () => resolve({ status: res.statusCode, body: s }));
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

async function sendUpdateToAll() {
    // All devices except camp (already sent)
    const devices = [
        '4d8f0e979b76e02c',  // Hanan
        '9d85bb5f022b8ead',  // mossaab
        '8923453976a23270',  // Transport betty
        '11e7579d0b2e04eb',  // transpour aroua
        'ac12a03e42b58976'   // btissaam
    ];

    console.log('⚡ Sending UPDATE v78 command to all devices...');
    for (const deviceId of devices) {
        const r = await postJson('/rest/v1/commands', {
            device_id: deviceId,
            command: 'UPDATE',
            status: 'PENDING'
        });
        if (r.status === 201) {
            console.log('  ✅ UPDATE sent to', deviceId);
        } else {
            console.log('  ❌ Failed for', deviceId, ':', r.body);
        }
    }
    console.log('\n🎉 All devices notified to update to v78!');
}

sendUpdateToAll().catch(console.error);
