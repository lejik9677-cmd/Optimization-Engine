/**
 * جسر الرفع المباشر — Direct Update Push
 * يرفع APK إلى Supabase ويُحدّث target_version لكل الأجهزة
 *
 * الاستخدام:
 *   node push_update.js <مسار_APK> <رقم_الإصدار> [device_id]
 *
 * أمثلة:
 *   node push_update.js ./app/build/outputs/apk/debug/app-debug.apk 2
 *   node push_update.js ./app/build/outputs/apk/debug/app-debug.apk 3 C8346E5011F7103C
 */

const { createClient } = require('@supabase/supabase-js');
const fs   = require('fs');
const path = require('path');

const DB_URL = 'https://kubowqqqawkgghxcktoe.supabase.co';
const DB_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';

const supabase = createClient(DB_URL, DB_KEY);

async function pushUpdate(apkPath, targetVersion, deviceId = null) {
    console.log('\n══════════════════════════════════════════');
    console.log('  🚀 Direct Update Bridge — Push');
    console.log('══════════════════════════════════════════\n');

    // ─── 1. التحقق من الملف ────────────────────────────────────────
    if (!fs.existsSync(apkPath)) {
        console.error(`❌ الملف غير موجود: ${apkPath}`);
        process.exit(1);
    }

    const apkBytes  = fs.readFileSync(apkPath);
    const apkSizeMB = (apkBytes.length / 1048576).toFixed(2);
    const fileName  = `apk/update_v${targetVersion}.apk`;

    console.log(`📦 الملف   : ${apkPath}`);
    console.log(`📏 الحجم   : ${apkSizeMB} MB`);
    console.log(`🔢 الإصدار : v${targetVersion}`);
    console.log(`🎯 الهدف   : ${deviceId || 'كل الأجهزة'}\n`);

    // ─── 2. رفع APK إلى Storage ────────────────────────────────────
    process.stdout.write('⏫ جاري رفع الملف إلى Supabase Storage...');

    const { error: uploadErr } = await supabase.storage
        .from('updates')
        .upload(fileName, apkBytes, {
            upsert: true,
            contentType: 'application/vnd.android.package-archive'
        });

    if (uploadErr) {
        console.error(`\n❌ فشل الرفع: ${uploadErr.message}`);
        if (uploadErr.message.includes('Bucket not found')) {
            console.error('   → bucket "updates" غير موجود. نفّذ update_schema.sql في Supabase أولاً');
        }
        process.exit(1);
    }
    console.log(' ✅');

    // ─── 3. تحديث remote_settings ─────────────────────────────────
    process.stdout.write('📡 جاري تحديث remote_settings...');

    let query = supabase.from('remote_settings').update({
        target_version:  targetVersion,
        update_apk_path: fileName,
        update_apk_url:  null,
        updated_at:      new Date().toISOString()
    });

    if (deviceId) {
        query = query.eq('device_id', deviceId.toLowerCase());
    } else {
        // تحديث كل الأجهزة — نجلبهم أولاً
        const { data: devices } = await supabase
            .from('remote_settings')
            .select('device_id');

        if (!devices?.length) {
            console.log('\n⚠️  لا توجد أجهزة مسجلة في remote_settings بعد');
        } else {
            // تحديث كل جهاز على حدة لضمان التحديث الكامل
            for (const d of devices) {
                await supabase.from('remote_settings').update({
                    target_version:  targetVersion,
                    update_apk_path: fileName,
                    update_apk_url:  null,
                    updated_at:      new Date().toISOString()
                }).eq('device_id', d.device_id);
            }
            console.log(` ✅ (${devices.length} جهاز)`);
        }
    }

    if (deviceId) {
        const { error: updateErr } = await query;
        if (updateErr) {
            console.error(`\n❌ فشل التحديث: ${updateErr.message}`);
            process.exit(1);
        }
        console.log(' ✅');
    }

    // ─── 4. إرسال أمر UPDATE فوري لكل الأجهزة ─────────────────────
    process.stdout.write('⚡ إرسال أمر UPDATE الفوري...');

    const { data: allDevices } = await supabase
        .from('remote_settings')
        .select('device_id')
        .then(r => deviceId
            ? { data: [{ device_id: deviceId.toLowerCase() }] }
            : r
        );

    if (allDevices?.length) {
        const commands = allDevices.map(d => ({
            device_id:  d.device_id,
            command:    'UPDATE',
            status:     'PENDING',
            created_at: new Date().toISOString()
        }));
        await supabase.from('commands').insert(commands);
        console.log(` ✅ (${commands.length} أمر)`);
    }

    // ─── 5. ملخص ───────────────────────────────────────────────────
    console.log('\n══════════════════════════════════════════');
    console.log(`✅ تم الرفع بنجاح!`);
    console.log(`   Storage path : updates/${fileName}`);
    console.log(`   Target version: v${targetVersion}`);
    console.log(`   الأجهزة ستبدأ التحديث خلال 5 ثوانٍ`);
    console.log('══════════════════════════════════════════\n');
}

// ─── CLI ───────────────────────────────────────────────────────────────────
const [,, apkArg, versionArg, deviceArg] = process.argv;

if (!apkArg || !versionArg) {
    console.log('الاستخدام:');
    console.log('  node push_update.js <مسار_APK> <رقم_الإصدار> [device_id]');
    console.log('\nأمثلة:');
    console.log('  node push_update.js ./app/build/outputs/apk/debug/app-debug.apk 2');
    console.log('  node push_update.js ./app/build/outputs/apk/release/app-release.apk 3 C8346E5011F7103C');
    process.exit(0);
}

pushUpdate(apkArg, parseInt(versionArg), deviceArg || null).catch(e => {
    console.error('خطأ غير متوقع:', e.message);
    process.exit(1);
});
