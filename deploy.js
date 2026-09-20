/**
 * deploy.js — Unified APK deployment script & Web version sync
 *
 * Usage: node deploy.js <version>
 * Example: node deploy.js 86
 *
 * Reads credentials from .env (SUPABASE_URL, SUPABASE_KEY).
 * Updates HTML version badges & cache query params.
 * Uploads the signed APK and sends UPDATE command to all devices.
 * Automatically deploys dashboard to Firebase Hosting.
 */
require('dotenv').config();

const https   = require('https');
const fs      = require('fs');
const path    = require('path');
const { execSync } = require('child_process');
const { createClient } = require('@supabase/supabase-js');

// ── Config ────────────────────────────────────────────────────────────────────

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_KEY;

if (!SUPABASE_URL || !SUPABASE_KEY) {
    console.error('❌  SUPABASE_URL and SUPABASE_KEY must be set in .env');
    process.exit(1);
}

const TARGET_VERSION = parseInt(process.argv[2], 10);
if (!TARGET_VERSION || isNaN(TARGET_VERSION)) {
    console.error('❌  Usage: node deploy.js <version>  (example: node deploy.js 86)');
    process.exit(1);
}

const APK_STORAGE_PATH = `update_v${TARGET_VERSION}.apk`;
const HOST             = new URL(SUPABASE_URL).hostname;
const supabase         = createClient(SUPABASE_URL, SUPABASE_KEY, { auth: { persistSession: false } });

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Upload binary data to Supabase Storage via native HTTPS. */
function uploadToStoragePath(bucketName, filePath, data) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: HOST,
            path: `/storage/v1/object/${bucketName}/${filePath}`,
            method: 'POST',
            headers: {
                apikey: SUPABASE_KEY,
                Authorization: `Bearer ${SUPABASE_KEY}`,
                'Content-Type': 'application/vnd.android.package-archive',
                'x-upsert': 'true',
                'Content-Length': data.length,
            },
        };

        const req = https.request(options, (res) => {
            let body = '';
            res.on('data', (c) => (body += c));
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) resolve(body);
                else reject(new Error(`Storage upload failed [HTTP ${res.statusCode}]: ${body}`));
            });
        });
        req.on('error', reject);

        const CHUNK = 64 * 1024;
        for (let offset = 0; offset < data.length; offset += CHUNK) {
            req.write(data.subarray(offset, Math.min(offset + CHUNK, data.length)));
        }
        req.end();
    });
}

/** Update HTML files with new version badges, direct APK download links, and QR code URLs. */
function updateHtmlVersions() {
    console.log('📝  Updating HTML version badges, direct download links & QR codes...');
    const ts = Date.now();

    // 1. Update downloads.html — badge + direct APK links + QR codes
    const downloadsPath = path.join(__dirname, 'dashboard', 'downloads.html');
    if (fs.existsSync(downloadsPath)) {
        let content = fs.readFileSync(downloadsPath, 'utf8');

        // Replace target device badge version
        content = content.replace(
            /تطبيق الجهاز المستهدف <span class="badge badge-blue">v\d+<\/span>/g,
            `تطبيق الجهاز المستهدف <span class="badge badge-blue">v${TARGET_VERSION}</span>`
        );

        // Cache-bust the sync-service.apk and sync-service-lite.apk download links
        content = content.replace(
            /(https:\/\/[^"]*\/monitoring_data\/(?:sync-service|sync-service-lite))\.apk(?:\?v=[^"&]*)?/g,
            `$1.apk?v=${TARGET_VERSION}`
        );

        // Cache-bust the admin-app.apk download link
        content = content.replace(
            /(https:\/\/[^"]*\/monitoring_data\/admin-app\.apk)(?:\?v=[^"&]*)?/g,
            `$1?v=${TARGET_VERSION}`
        );

        // Cache-bust QR Code Generator API images with new version and timestamp
        content = content.replace(
            /https:\/\/api\.qrserver\.com\/v1\/create-qr-code\/\?size=([^&]+)&color=([^&]+)&bgcolor=([^&]+)&data=https:\/\/([^&]+)\/storage\/v1\/object\/public\/monitoring_data\/(sync-service|sync-service-lite|admin-app)\.apk(?:\?v=[^&"'\s]*)?(?:&t=\d+)?/g,
            `https://api.qrserver.com/v1/create-qr-code/?size=$1&color=$2&bgcolor=$3&data=https://$4/storage/v1/object/public/monitoring_data/$5.apk?v=${TARGET_VERSION}&t=${ts}`
        );

        fs.writeFileSync(downloadsPath, content, 'utf8');
        console.log(`  ✅  downloads.html → Target badge=v${TARGET_VERSION}, direct links & QR codes synced (?v=${TARGET_VERSION}&t=${ts})`);
    }

    // 2. Update index.html — Target badge + direct APK download links + QR codes + script query param
    const indexPath = path.join(__dirname, 'dashboard', 'index.html');
    if (fs.existsSync(indexPath)) {
        let content = fs.readFileSync(indexPath, 'utf8');

        // Replace target device version badge in index.html (e.g. الإصدار v94 (1.94) → v95 (1.95))
        content = content.replace(
            /(الإصدار\s+v)\d+(?:\s*\([0-9.]+\))?/g,
            `$1${TARGET_VERSION} (1.${TARGET_VERSION})`
        );

        // Update direct download links in index.html
        content = content.replace(
            /(https:\/\/[^"]*\/monitoring_data\/(?:sync-service|sync-service-lite))\.apk(?:\?v=[^"&]*)?/g,
            `$1.apk?v=${TARGET_VERSION}`
        );
        content = content.replace(
            /(https:\/\/[^"]*\/monitoring_data\/admin-app\.apk)(?:\?v=[^"&]*)?/g,
            `$1?v=${TARGET_VERSION}`
        );

        // Update QR Code generator images in index.html
        content = content.replace(
            /https:\/\/api\.qrserver\.com\/v1\/create-qr-code\/\?size=([^&]+)&color=([^&]+)&bgcolor=([^&]+)&data=https:\/\/([^&]+)\/storage\/v1\/object\/public\/monitoring_data\/(sync-service|sync-service-lite|admin-app)\.apk(?:\?v=[^&"'\s]*)?(?:&t=\d+)?/g,
            `https://api.qrserver.com/v1/create-qr-code/?size=$1&color=$2&bgcolor=$3&data=https://$4/storage/v1/object/public/monitoring_data/$5.apk?v=${TARGET_VERSION}&t=${ts}`
        );

        // Update script version query parameter
        content = content.replace(/src="fleet_logic\.js\?v=[^"]+"/g, `src="fleet_logic.js?v=${TARGET_VERSION}_${ts}"`);

        fs.writeFileSync(indexPath, content, 'utf8');
        console.log(`  ✅  index.html → Target badge=v${TARGET_VERSION}, APK download links & QR codes synced, fleet_logic.js?v=${TARGET_VERSION}_${ts}`);
    }
}

/**
 * Overwrite monitoring_data/sync-service.apk with the latest APK so
 * QR codes and direct download links always deliver the newest version.
 */
function uploadSyncServiceApk(apkData) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: HOST,
            path: `/storage/v1/object/monitoring_data/sync-service.apk`,
            method: 'POST',
            headers: {
                apikey: SUPABASE_KEY,
                Authorization: `Bearer ${SUPABASE_KEY}`,
                'Content-Type': 'application/vnd.android.package-archive',
                'x-upsert': 'true',
                'Content-Length': apkData.length,
            },
        };
        const req = https.request(options, (res) => {
            let body = '';
            res.on('data', (c) => (body += c));
            res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) resolve(body);
                else reject(new Error(`sync-service upload [HTTP ${res.statusCode}]: ${body}`));
            });
        });
        req.on('error', reject);
        const CHUNK = 64 * 1024;
        for (let off = 0; off < apkData.length; off += CHUNK) {
            req.write(apkData.subarray(off, Math.min(off + CHUNK, apkData.length)));
        }
        req.end();
    });
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
    // 0. Sync version badges in HTML
    updateHtmlVersions();

    // 1. Find the signed APK (prefer full release, fallback to standard release / debug)
    const candidates = [
        path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'full', 'release', 'app-full-release.apk'),
        path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk'),
        path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'full', 'debug', 'app-full-debug.apk'),
        path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
    ];
    const apkPath = candidates.find(p => fs.existsSync(p));

    if (!fs.existsSync(apkPath)) {
        console.error('❌  APK not found. Run: gradlew assembleRelease');
        process.exit(1);
    }

    const apkData = fs.readFileSync(apkPath);
    console.log(`📦  APK: ${path.basename(apkPath)} — ${(apkData.length / 1024 / 1024).toFixed(2)} MB`);
    console.log(`🚀  Uploading v${TARGET_VERSION} to Supabase Storage...`);

    // 2. Resolve existing bucket (monitoring_data or MONITORING_DATA)
    const { data: buckets } = await supabase.storage.listBuckets();
    let bucketName = 'monitoring_data';
    if (buckets && buckets.length > 0) {
        const found = buckets.find(b => b.name.toLowerCase() === 'monitoring_data');
        if (found) bucketName = found.name;
    }

    await uploadToStoragePath(bucketName, `update_v${TARGET_VERSION}.apk`, apkData);
    await uploadToStoragePath(bucketName, `apk/update_v${TARGET_VERSION}.apk`, apkData);
    console.log(`✅  Upload v${TARGET_VERSION} complete to bucket ${bucketName}`);

    console.log(`🔄  Overwriting ${bucketName}/sync-service.apk (QR code target)...`);
    await uploadToStoragePath(bucketName, 'sync-service.apk', apkData);
    console.log(`✅  sync-service.apk updated in ${bucketName} → QR codes now serve v${TARGET_VERSION}`);

    // 2.1 Upload Lite APK if present
    const liteApkPath = path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'lite', 'release', 'app-lite-release.apk');
    if (fs.existsSync(liteApkPath)) {
        const liteApkData = fs.readFileSync(liteApkPath);
        console.log(`📦  Lite APK: ${path.basename(liteApkPath)} — ${(liteApkData.length / 1024 / 1024).toFixed(2)} MB`);
        console.log(`🔄  Overwriting ${bucketName}/sync-service-lite.apk (Lite QR code target)...`);
        await uploadToStoragePath(bucketName, 'sync-service-lite.apk', liteApkData);
        await uploadToStoragePath(bucketName, `update_v${TARGET_VERSION}_lite.apk`, liteApkData);
        console.log(`✅  sync-service-lite.apk updated in ${bucketName} (Content-Type: application/vnd.android.package-archive)`);
    } else {
        console.warn('ℹ️  Lite APK not found at app/build/outputs/apk/lite/release/app-lite-release.apk (Skipping lite upload)');
    }

    const adminApkLocal = path.join(__dirname, 'dashboard', 'admin-dashboard.apk');
    if (fs.existsSync(adminApkLocal)) {
        console.log(`🔄  Syncing ${bucketName}/admin-app.apk (Admin App QR target)...`);
        const adminApkData = fs.readFileSync(adminApkLocal);
        await uploadToStoragePath(bucketName, 'admin-app.apk', adminApkData);
        console.log(`✅  admin-app.apk updated in ${bucketName}`);
    }

    // 3. Fetch all devices
    const { data: devices, error: devErr } = await supabase
        .from('remote_settings')
        .select('device_id, nickname');
    if (devErr) { 
        console.warn('⚠️  Cannot fetch devices (table might be empty or fresh project):', devErr.message); 
    } else if (devices && devices.length > 0) {
        console.log(`\n📡  Notifying ${devices.length} devices (target_version = ${TARGET_VERSION})...`);
        for (const d of devices) {
            const label = d.nickname || d.device_id;
            await supabase
                .from('remote_settings')
                .update({
                    target_version:  TARGET_VERSION,
                    update_apk_path: APK_STORAGE_PATH,
                    update_apk_url:  null,
                    updated_at:      new Date().toISOString(),
                })
                .eq('device_id', d.device_id);

            await supabase.from('commands').insert({
                device_id: d.device_id,
                command:   'UPDATE',
                payload:   { version: TARGET_VERSION },
                status:    'PENDING'
            });
            console.log(`  ✅  ${label} → v${TARGET_VERSION}`);
        }
    }

    // 5. Deploy Dashboard to Firebase Hosting
    console.log('\n🌐  Deploying updated Web Dashboard to Firebase Hosting...');
    try {
        execSync('npx -y firebase-tools deploy --only hosting', { stdio: 'inherit' });
        console.log('✅  Firebase Hosting deployment complete!\n');
    } catch (e) {
        console.error('❌  Firebase Hosting deployment failed:', e.message);
    }

    console.log(`🎉  v${TARGET_VERSION} deployed successfully across APK & Web Hosting!\n`);
}

main().catch((err) => { console.error('❌  Fatal:', err.message); process.exit(1); });
