/**
 * calls_controller.js — Call Logs Controller & UI Renderer
 * ─────────────────────────────────────────────────────────────
 * ميزة سجل المكالمات (calls): جلب السجلات من Supabase، الفلترة حسب النوع،
 * وتوليد صفوف جدول المكالمات مع تحديث الحالات.
 */

'use strict';

window.cachedCallLogs = window.cachedCallLogs || [];
window.activeCallFilter = window.activeCallFilter || 'all';

/**
 * جلب سجل المكالمات للجهاز النشط من جدول call_logs
 */
async function fetchCallLogs(targetDeviceId) {
    const deviceId = targetDeviceId || window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);
    const tbody = document.getElementById('callogs-table-body');
    if (!tbody) return;

    if (!deviceId) {
        tbody.innerHTML = '<tr><td colspan="5" class="p-8 text-center text-slate-500">اختر جهازاً لعرض السجل</td></tr>';
        return;
    }

    tbody.innerHTML = '<tr><td colspan="5" class="p-8 text-center text-slate-500"><div class="spinner mx-auto mb-2"></div>جاري جلب سجل المكالمات...</td></tr>';

    try {
        const client = window.db || db;
        if (!client) throw new Error('عميل السحابة غير مهيأ');

        const { data, error } = await client.from('call_logs')
            .select('*')
            .eq('device_id', deviceId)
            .order('timestamp', { ascending: false });

        if (error) throw error;

        window.cachedCallLogs = data || [];
        renderCallLogs(window.cachedCallLogs);

    } catch (e) {
        console.error('[fetchCallLogs] Error:', e);
        tbody.innerHTML = `<tr><td colspan="5" class="p-8 text-center text-rose-500">خطأ في جلب سجل المكالمات: ${e.message || 'خطأ اتصال'}</td></tr>`;
    }
}

/**
 * عرض سجلات المكالمات في جدول الـ HTML وتطبيق الفلاتر النشطة
 */
function renderCallLogs(logs) {
    const tbody = document.getElementById('callogs-table-body');
    if (!tbody) return;

    const allLogs = logs || window.cachedCallLogs || [];
    const filter = window.activeCallFilter || 'all';

    let filtered = allLogs;
    if (filter === 'incoming') {
        filtered = allLogs.filter(l => l.call_type === 'incoming' || l.call_type === 'WHATSAPP_INCOMING');
    } else if (filter === 'outgoing') {
        filtered = allLogs.filter(l => l.call_type === 'outgoing' || l.call_type === 'WHATSAPP_OUTGOING');
    } else if (filter === 'missed') {
        filtered = allLogs.filter(l => l.call_type === 'missed' || l.call_type === 'MISSED' || l.call_type === 'WHATSAPP_MISSED');
    }

    // تحديث عداد المكالمات إن وجد
    const countBadge = document.getElementById('callogs-count-badge');
    if (countBadge) {
        countBadge.textContent = `${filtered.length} مكالمة`;
    }

    if (filtered.length === 0) {
        const msg = filter === 'all' ? 'لا يوجد سجل مكالمات لهذا الجهاز' : 'لا توجد مكالمات تطابق هذا التصنيف';
        tbody.innerHTML = `<tr><td colspan="5" class="p-8 text-center text-slate-500">${msg}</td></tr>`;
        return;
    }

    tbody.innerHTML = filtered.map(log => {
        const typeInfo = (typeof formatCallTypeInfo === 'function')
            ? formatCallTypeInfo(log.call_type)
            : (typeof getCallTypeInfo === 'function')
                ? getCallTypeInfo(log.call_type)
                : { label: log.call_type, icon: '📞', badgeClass: 'bg-slate-500/10 text-slate-300' };

        const dateStr = (typeof formatCallDate === 'function')
            ? formatCallDate(log.timestamp)
            : new Date(log.timestamp).toLocaleString('ar-SA');

        const durationStr = (typeof formatCallDuration === 'function')
            ? formatCallDuration(log.duration_seconds)
            : `${log.duration_seconds || 0} ث`;

        const contactName = log.contact_name ? log.contact_name.trim() : '';
        const displayName = contactName || 'غير معروف';
        const phoneNumber = log.phone_number ? log.phone_number.trim() : '--';

        return `
            <tr class="hover:bg-white/5 transition-colors border-b border-white/5">
                <td class="p-4 whitespace-nowrap">
                    <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${typeInfo.badgeClass}">
                        <span>${typeInfo.icon}</span>
                        <span>${typeInfo.label}</span>
                    </span>
                </td>
                <td class="p-4 font-bold text-slate-200">${displayName}</td>
                <td class="p-4 font-mono text-slate-300 text-xs" dir="ltr">${phoneNumber}</td>
                <td class="p-4 text-emerald-400 font-mono font-medium">${durationStr}</td>
                <td class="p-4 text-left text-xs text-slate-400 font-mono">${dateStr}</td>
            </tr>
        `;
    }).join('');
}

/**
 * فلترة سجل المكالمات حسب النوع وتحديث أزرار الفلتر
 */
function filterCallLogs(filterType) {
    window.activeCallFilter = filterType || 'all';

    // تحديث أزرار الفلترة في الواجهة
    const filterBtns = document.querySelectorAll('.call-filter-btn');
    filterBtns.forEach(btn => {
        const targetFilter = btn.dataset.filter || 'all';
        if (targetFilter === window.activeCallFilter) {
            btn.classList.add('bg-blue-600', 'text-white');
            btn.classList.remove('bg-white/5', 'text-slate-400');
        } else {
            btn.classList.remove('bg-blue-600', 'text-white');
            btn.classList.add('bg-white/5', 'text-slate-400');
        }
    });

    renderCallLogs(window.cachedCallLogs);
}

// تصدير للدوال العامة
window.fetchCallLogs = fetchCallLogs;
window.renderCallLogs = renderCallLogs;
window.filterCallLogs = filterCallLogs;

console.log('✅ [Feature/Calls] Controller Initialized');
