/**
 * audio_date_parser.js — Audio Date and Timestamp Parser & Formatter
 * ─────────────────────────────────────────────────────────────
 * ميزة خزينة الصوت (audio_vault): استخراج التاريخ والوقت من أسماء التسجيلات
 * وتنسيقها بالصيغة الإلزامية الصارمة، بالإضافة لتنسيق توقيت المشغل.
 */

'use strict';

/**
 * استخراج كائن Date من اسم ملف التسجيل (yyyyMMdd_HHmmss) مع Fallback للتاريخ السحابي
 */
function parseAudioDate(fileObjOrName, ts) {
    const name = typeof fileObjOrName === 'string' ? fileObjOrName : (fileObjOrName?.name || '');
    if (name) {
        const m = name.match(/_(20\d{6})_(\d{6})/);
        if (m) {
            const dateStr = m[1]; // yyyyMMdd
            const timeStr = m[2]; // HHmmss
            const year = parseInt(dateStr.slice(0, 4), 10);
            const month = parseInt(dateStr.slice(4, 6), 10) - 1;
            const day = parseInt(dateStr.slice(6, 8), 10);
            const hour = parseInt(timeStr.slice(0, 2), 10);
            const minute = parseInt(timeStr.slice(2, 4), 10);
            const second = parseInt(timeStr.slice(4, 6), 10);
            return new Date(year, month, day, hour, minute, second);
        }
    }
    const fallbackTs = ts || (typeof fileObjOrName === 'object' ? (fileObjOrName?.created_at || fileObjOrName?.metadata?.created_at) : null);
    return fallbackTs ? new Date(fallbackTs) : new Date(0);
}

/**
 * الحصول على الطابع الزمني بالمللي ثانية لفلترة وترتيب الملفات
 */
function getAudioFileTimestamp(fileObj) {
    if (!fileObj) return 0;
    return parseAudioDate(fileObj).getTime();
}

/**
 * تنسيق الطابع الزمني الإلزامي لقواعد المشروع: 📅 DD/MM/YYYY - 🕒 HH:MM:SS
 */
function formatAudioTimestamp(name, ts) {
    if (!name) return ts ? new Date(ts).toLocaleString('ar-EG') : '--';
    
    // filenames: call_20260704_121500.m4a or rec_20260704_121500.m4a
    const match = name.match(/_(20\d{6})_(\d{6})/);
    if (match) {
        const dateStr = match[1]; // yyyyMMdd
        const timeStr = match[2]; // HHmmss
        const year = dateStr.slice(0, 4);
        const month = dateStr.slice(4, 6);
        const day = dateStr.slice(6, 8);
        const hour = timeStr.slice(0, 2);
        const minute = timeStr.slice(2, 4);
        const second = timeStr.slice(4, 6);
        return `📅 ${day}/${month}/${year} - 🕒 ${hour}:${minute}:${second}`;
    }
    
    if (ts) {
        const d = new Date(ts);
        return `📅 ${d.toLocaleDateString('ar-EG')} - 🕒 ${d.toLocaleTimeString('ar-EG', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}`;
    }
    return '--';
}

/**
 * تنسيق دقائق وثواني مشغل الصوت: MM:SS
 */
function formatAudioSeconds(sec) {
    if (isNaN(sec) || !isFinite(sec) || sec < 0) return '00:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`;
}

// تصدير للدوال العامة
window.parseAudioDate = parseAudioDate;
window.getAudioFileTimestamp = getAudioFileTimestamp;
window.formatAudioTimestamp = formatAudioTimestamp;
window.formatAudioSeconds = formatAudioSeconds;

console.log('✅ [Feature/AudioVault] Date Parser Initialized');
