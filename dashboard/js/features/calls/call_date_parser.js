/**
 * call_date_parser.js — Call Log Date, Duration & Type Formatter
 * ─────────────────────────────────────────────────────────────
 * ميزة سجل المكالمات (calls): معالجة وتنسيق التواريخ، المدة الزمنية،
 * وتحديد مسميات وشارات أنواع المكالمات (هاتف، واتساب، محيطية).
 */

'use strict';

/**
 * تنسيق تاريخ ووقت المكالمة مع أرقام لاتينية قياسية
 */
function formatCallDate(timestamp) {
    if (!timestamp) return '--';
    try {
        const d = new Date(timestamp);
        if (isNaN(d.getTime())) return '--';
        return d.toLocaleString('ar-SA', { numberingSystem: 'latn' });
    } catch (e) {
        return String(timestamp);
    }
}

/**
 * تنسيق مدة المكالمة (بالثواني أو الدقائق والثواني)
 */
function formatCallDuration(seconds) {
    const sec = parseInt(seconds, 10);
    if (isNaN(sec) || sec <= 0) return '0 ث';

    if (sec < 60) {
        return `${sec} ث`;
    }

    const mins = Math.floor(sec / 60);
    const remainingSecs = sec % 60;
    if (remainingSecs === 0) {
        return `${mins} د`;
    }
    return `${mins} د ${remainingSecs} ث`;
}

/**
 * خريطة أنواع المكالمات وتسمياتها وشاراتها اللونية
 */
function getCallTypeInfo(type) {
    const rawType = (type || '').trim();
    switch (rawType) {
        case 'incoming':
            return {
                label: 'واردة',
                icon: '📞',
                badgeClass: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
            };
        case 'outgoing':
            return {
                label: 'صادرة',
                icon: '📱',
                badgeClass: 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
            };
        case 'missed':
        case 'MISSED':
            return {
                label: 'فائتة',
                icon: '📵',
                badgeClass: 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
            };
        case 'ambient':
            return {
                label: 'عادية',
                icon: '🎙️',
                badgeClass: 'bg-slate-500/10 text-slate-300 border border-slate-500/20'
            };
        case 'WHATSAPP_INCOMING':
            return {
                label: 'واتساب (واردة)',
                icon: '💬',
                badgeClass: 'bg-teal-500/10 text-teal-400 border border-teal-500/20'
            };
        case 'WHATSAPP_OUTGOING':
            return {
                label: 'واتساب (صادرة)',
                icon: '💬',
                badgeClass: 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20'
            };
        case 'WHATSAPP_MISSED':
            return {
                label: 'واتساب (فائتة)',
                icon: '💬',
                badgeClass: 'bg-red-500/10 text-red-400 border border-red-500/20'
            };
        case 'WHATSAPP_ONGOING':
            return {
                label: 'واتساب (جارية)',
                icon: '💬',
                badgeClass: 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
            };
        default:
            return {
                label: rawType || 'مكالمة',
                icon: '📞',
                badgeClass: 'bg-slate-500/10 text-slate-400 border border-slate-500/20'
            };
    }
}

// تصدير للدوال العامة
window.formatCallDate = formatCallDate;
window.formatCallDuration = formatCallDuration;
window.getCallTypeInfo = getCallTypeInfo;

console.log('✅ [Feature/Calls] Date Parser & Formatter Initialized');
