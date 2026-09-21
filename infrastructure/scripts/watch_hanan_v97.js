#!/usr/bin/env node
/**
 * watch_hanan_v97.js
 * Live Watcher for Hanan (4d8f0e979b76e02c)
 * Monitors:
 * 1. Fleet update to v97 & UPDATE command status
 * 2. Incoming [AudioDiag] logs & extraction of acoustic metrics
 * 3. New audio files in Supabase Storage with signed playback URL generation
 */

require('dotenv').config();
const { createClient } = require('@supabase/supabase-js');

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_KEY;

if (!SUPABASE_URL || !SUPABASE_KEY) {
    console.error('❌ خطأ: SUPABASE_URL و SUPABASE_KEY يجب توفرهما في ملف .env');
    process.exit(1);
}

const db = createClient(SUPABASE_URL, SUPABASE_KEY, {
    auth: { persistSession: false }
});

const DEVICE_ID = '4d8f0e979b76e02c';
const DEVICE_NAME = 'Hanan (Samsung S21 5G)';
const POLL_INTERVAL_MS = 15000; // 15 ثانية

console.log('═══════════════════════════════════════════════════════════════════════');
console.log(` 🛰️ بدء استطلاع ومراقبة حية (Live Watcher) — جهاز ${DEVICE_NAME}`);
console.log(` 🎯 المعرف: ${DEVICE_ID}`);
console.log(` ⏱️ دورة الفحص: كل ${POLL_INTERVAL_MS / 1000} ثانية`);
console.log(` 🕒 وقت البدء: ${new Date().toLocaleString('ar-SA')}`);
console.log('═══════════════════════════════════════════════════════════════════════\n');

let lastVersion = null;
let lastCmdStatus = null;
let seenLogIds = new Set();
let seenFileNames = new Set();
let cycleCount = 0;
let updateAnnounced = false;

// تهيئة الملفات والسجلات الحالية لمنع تكرار القديم
async function initializeBaseline() {
    try {
        // 1. قراءة الإعدادات الأولية
        const { data: rs } = await db.from('remote_settings').select('*').eq('device_id', DEVICE_ID).maybeSingle();
        if (rs) {
            lastVersion = rs.current_version_code;
            console.log(`📌 الإصدار الأولي المسجل: v${lastVersion ?? 'غير محدد'} (المستهدف: v${rs.target_version})`);
            if (lastVersion >= 97) {
                console.log(`✅ الجهاز يعمل بالفعل على الإصدار v${lastVersion}!`);
                updateAnnounced = true;
            }
        }

        // 2. قراءة حالة أمر UPDATE
        const { data: cmd } = await db.from('commands')
            .select('*')
            .eq('device_id', DEVICE_ID)
            .eq('command', 'UPDATE')
            .order('created_at', { ascending: false })
            .limit(1)
            .maybeSingle();

        if (cmd) {
            lastCmdStatus = cmd.status;
            console.log(`📌 حالة أمر UPDATE الحالية: [${cmd.status}] (المعرف: ${cmd.id})`);
        }

        // 3. تخزين معرفات السجلات السابقة
        const { data: initialLogs } = await db.from('remote_logs')
            .select('id')
            .eq('device_id', DEVICE_ID)
            .order('created_at', { ascending: false })
            .limit(50);

        if (initialLogs) {
            initialLogs.forEach(l => seenLogIds.add(l.id));
            console.log(`📌 تم فهرسة ${seenLogIds.size} سجل سابق لتجنب التكرار.`);
        }

        // 4. تخزين أسماء الملفات الصوتية الحالية
        const { data: initialFiles } = await db.storage
            .from('monitoring_data')
            .list(`audio/${DEVICE_ID}`, { limit: 100 });

        if (initialFiles) {
            initialFiles.forEach(f => seenFileNames.add(f.name));
            console.log(`📌 تم فهرسة ${seenFileNames.size} ملف صوتي موجود مسبقاً في التخزين.\n`);
        }

        console.log('───────────────────────────────────────────────────────────────────────');
        console.log('  👀 النظام في وضع المراقبة الحية بانتظار أي تحديث أو إشارة صوتية...');
        console.log('───────────────────────────────────────────────────────────────────────\n');
    } catch (e) {
        console.error('⚠️ استثناء أثناء التهيئة الأولية:', e.message);
    }
}

async function checkCycle() {
    cycleCount++;
    try {
        // ── 1. فحص الإصدار وأمر UPDATE ──────────────────────────────────────────
        const { data: rs } = await db.from('remote_settings').select('*').eq('device_id', DEVICE_ID).maybeSingle();
        if (rs) {
            const currentVer = rs.current_version_code;
            if (lastVersion !== null && currentVer !== lastVersion) {
                console.log('\n🔔═══════════════════════════════════════════════════════════════════════');
                console.log(` 🎉 [إشعار فوري] انتقل الجهاز ${DEVICE_NAME} إلى الإصدار: v${currentVer}!`);
                console.log(` 📅 التوقيت: ${new Date().toLocaleString('ar-SA')}`);
                console.log(` 🔄 التغيير: v${lastVersion} ➔ v${currentVer}`);
                console.log('═══════════════════════════════════════════════════════════════════════\n');
                lastVersion = currentVer;
                updateAnnounced = true;
            }
        }

        const { data: cmd } = await db.from('commands')
            .select('*')
            .eq('device_id', DEVICE_ID)
            .eq('command', 'UPDATE')
            .order('created_at', { ascending: false })
            .limit(1)
            .maybeSingle();

        if (cmd && cmd.status !== lastCmdStatus) {
            console.log(`\n🔄 [تحديث أمر UPDATE] تحولت الحالة من [${lastCmdStatus}] إلى [${cmd.status}]`);
            if (cmd.executed_at) {
                console.log(`   ⏱️ تاريخ التنفيذ الفعلي: ${cmd.executed_at}`);
            }
            lastCmdStatus = cmd.status;
        }

        // ── 2. فحص السجلات الجديدة و [AudioDiag] ──────────────────────────────────
        const { data: newLogs } = await db.from('remote_logs')
            .select('*')
            .eq('device_id', DEVICE_ID)
            .order('created_at', { ascending: false })
            .limit(20);

        if (newLogs && newLogs.length > 0) {
            const freshLogs = newLogs.filter(l => !seenLogIds.has(l.id)).reverse();
            for (const log of freshLogs) {
                seenLogIds.add(log.id);
                const timeStr = new Date(log.created_at).toLocaleTimeString('ar-SA', { hour12: false });

                if (log.message && log.message.includes('[AudioDiag]')) {
                    // استخراج تفاصيل AudioDiag
                    console.log('\n🎙️═══════════════════════════════════════════════════════════════════════');
                    console.log(` 📊 [AudioDiag] تم رصد تقرير تشخيص صوتي مباشر!`);
                    console.log(` 🕒 التوقيت: ${timeStr} | السجل: ${log.id}`);
                    console.log('───────────────────────────────────────────────────────────────────────');

                    const sizeMatch = log.message.match(/size=(\d+)B \((\d+)KB\)/);
                    const peakMatch = log.message.match(/peak=(\d+)/);
                    const framesMatch = log.message.match(/activeFrames=(\d+)\/(\d+)/);
                    const speechMatch = log.message.match(/hasSpeech=(true|false)/);
                    const fileMatch = log.message.match(/file=([\w_.]+)/);

                    console.log(` 📁 اسم الملف:        ${fileMatch ? fileMatch[1] : 'غير محدد'}`);
                    console.log(` 📦 الحجم الفعلي:       ${sizeMatch ? `${sizeMatch[1]} بايت (${sizeMatch[2]} KB)` : 'غير محدد'}`);
                    console.log(` 📈 ذروة الإشارة (Peak): ${peakMatch ? peakMatch[1] : 'غير محدد'}`);
                    console.log(` 🎞️ الإطارات النشطة:   ${framesMatch ? `${framesMatch[1]} من أصل ${framesMatch[2]}` : 'غير محدد'}`);
                    console.log(` 🗣️ وجود صوت بشري:     ${speechMatch ? (speechMatch[1] === 'true' ? 'نعم ✅' : 'صمت 🔇') : 'غير محدد'}`);
                    console.log('═══════════════════════════════════════════════════════════════════════\n');
                } else if (log.tag === 'AudioRecorderEngine' || log.tag === 'CallMonitoringService') {
                    console.log(`🔊 [${timeStr}] [${log.tag}] [${log.level}]: ${log.message}`);
                } else if (log.level === 'WARN' || log.level === 'ERROR') {
                    console.log(`⚠️ [${timeStr}] [${log.tag}] [${log.level}]: ${log.message}`);
                }
            }
        }

        // ── 3. فحص التخزين لملفات صوتية جديدة ─────────────────────────────────────
        const { data: storageFiles } = await db.storage
            .from('monitoring_data')
            .list(`audio/${DEVICE_ID}`, {
                limit: 10,
                sortBy: { column: 'created_at', order: 'desc' }
            });

        if (storageFiles && storageFiles.length > 0) {
            for (const file of storageFiles) {
                if (!seenFileNames.has(file.name) && file.name.endsWith('.m4a')) {
                    seenFileNames.add(file.name);
                    const sizeBytes = file.metadata?.size || 0;
                    const sizeKb = Math.round(sizeBytes / 1024);

                    console.log('\n🌟═══════════════════════════════════════════════════════════════════════');
                    console.log(` 🎧 تم رصد ملف صوتي جديد تم رفعه إلى التخزين السحابي!`);
                    console.log(` 📁 الملف: ${file.name} (${sizeKb} KB / ${sizeBytes} B)`);
                    console.log(` 📅 وقت الإنشاء: ${file.created_at}`);

                    // توليد رابط تشغيل موقّع صالح للاختبار
                    const { data: signedData, error: signErr } = await db.storage
                        .from('monitoring_data')
                        .createSignedUrl(`audio/${DEVICE_ID}/${file.name}`, 86400);

                    if (signedData?.signedUrl) {
                        console.log(` 🔗 رابط التشغيل الفوري (صالحة لـ 24 ساعة):`);
                        console.log(`    ${signedData.signedUrl}`);
                        console.log(` ▶️ يمكن تمريره مباشرة إلى getSharedAudioPlayer() للاختبار!`);
                    } else if (signErr) {
                        console.log(` ⚠️ فشل توليد الرابط الموقّع: ${signErr.message}`);
                    }
                    console.log('═══════════════════════════════════════════════════════════════════════\n');
                }
            }
        }

        // ── 4. نبضة دورية هادئة (كل 4 دورات = 60 ثانية) ─────────────────────────
        if (cycleCount % 4 === 0) {
            const nowTime = new Date().toLocaleTimeString('ar-SA', { hour12: false });
            const lastUpdated = rs?.updated_at ? new Date(rs.updated_at) : null;
            const diffMin = lastUpdated ? Math.round((Date.now() - lastUpdated.getTime()) / 60000) : '?';
            console.log(`⏱️ [نبضة فحص - ${nowTime}] الإصدار: v${lastVersion ?? '?'} | أمر التحديث: [${lastCmdStatus ?? '؟'}] | آخر اتصال: منذ ${diffMin} دقيقة | بانتظار أحداث جديدة...`);
        }
    } catch (err) {
        console.error(`⚠️ خطأ في دورة الفحص #${cycleCount}:`, err.message);
    }
}

async function main() {
    await initializeBaseline();
    // تشغيل أول فحص فوراً
    await checkCycle();
    // تفعيل التكرار الدوري
    setInterval(checkCycle, POLL_INTERVAL_MS);
}

main();
