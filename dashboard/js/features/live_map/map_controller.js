/**
 * map_controller.js — Location Data Fetcher & Live Tracking Controller
 * ─────────────────────────────────────────────────────────────────────────────
 * ميزة الخريطة والتتبع المباشر (live_map):
 * جلب نقاط الموقع من جدول locations في Supabase، معالجة التاريخ،
 * وإدارة وضع التتبع الحي المستمر كل 30 ثانية.
 */

'use strict';

window.liveTrackingActive = window.liveTrackingActive || false;
window.liveTrackingTimer  = window.liveTrackingTimer || null;

/**
 * تهيئة منتقي التاريخ الافتراضي لليوم الحالي
 */
function initRouteDatePicker() {
    const picker = document.getElementById('route-date-picker');
    if (picker && !picker.value) {
        const today = new Date();
        const yyyy = today.getFullYear();
        const mm = String(today.getMonth() + 1).padStart(2, '0');
        const dd = String(today.getDate()).padStart(2, '0');
        picker.value = `${yyyy}-${mm}-${dd}`;
    }
}

/**
 * تشغيل أو إيقاف وضع التتبع المباشر المستمر (تحديث كل 30 ثانية)
 */
function toggleLiveTracking() {
    window.liveTrackingActive = !window.liveTrackingActive;
    const btn = document.getElementById('live-track-btn');

    if (window.liveTrackingActive) {
        if (btn) {
            btn.innerText = '🔴 إيقاف المتابعة';
            btn.style.background = 'rgba(239,68,68,0.2)';
            btn.style.borderColor = 'rgba(239,68,68,0.4)';
            btn.style.color = '#fca5a5';
        }
        if (typeof showNotif === 'function') {
            showNotif('📡 وضع التتبع المباشر نشط — تحديث كل 30 ثانية', 'info');
        }

        // تحديث فوري أولي
        fetchPositions();

        // تحديث دوري كل 30 ثانية
        if (window.liveTrackingTimer) clearInterval(window.liveTrackingTimer);
        window.liveTrackingTimer = setInterval(() => {
            const activeId = window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);
            const activeTab = window.currentTab || (typeof currentTab !== 'undefined' ? currentTab : null);
            if (activeId && window.mapInstance && activeTab === 'track') {
                fetchPositions(activeId);
            }
        }, 30_000);
    } else {
        if (window.liveTrackingTimer) {
            clearInterval(window.liveTrackingTimer);
            window.liveTrackingTimer = null;
        }
        if (btn) {
            btn.innerText = '📡 تتبع مباشر';
            btn.style.background = '';
            btn.style.borderColor = '';
            btn.style.color = '';
        }
        if (typeof showNotif === 'function') {
            showNotif('⏹️ تم إيقاف وضع التتبع المباشر', 'info');
        }
    }
}

/**
 * جلب سجلات الموقع للجهاز المحدد من Supabase وتمريرها للمحرك الرسومي
 */
async function fetchPositions(targetDeviceId) {
    const deviceId = targetDeviceId || window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);
    const map = window.mapInstance || (typeof mapInstance !== 'undefined' ? mapInstance : null);

    if (!deviceId || !map) return;

    const refreshBtn = document.getElementById('refresh-route-btn');
    if (refreshBtn) {
        refreshBtn.innerText = '⏳ جاري...';
        refreshBtn.disabled = true;
    }

    try {
        initRouteDatePicker();

        const client = window.db || (typeof db !== 'undefined' ? db : null);
        if (!client) throw new Error('عميل السحابة غير متوفر');

        const datePicker = document.getElementById('route-date-picker');
        let query = client.from('locations')
            .select('latitude, longitude, accuracy, timestamp, battery_level')
            .eq('device_id', deviceId)
            .order('timestamp', { ascending: false });

        if (datePicker && datePicker.value) {
            const dateStr = datePicker.value;
            const startDate = new Date(dateStr + 'T00:00:00');
            const endDate = new Date(dateStr + 'T23:59:59.999');

            query = query
                .gte('timestamp', startDate.toISOString())
                .lte('timestamp', endDate.toISOString())
                .limit(1000);
        } else {
            query = query.limit(200);
        }

        const { data: locs, error } = await query;

        if (refreshBtn) {
            refreshBtn.innerText = '🔄 تحديث المسار';
            refreshBtn.disabled = false;
        }

        if (error) {
            console.error('[fetchPositions] Database query error:', error);
            if (typeof showMapToast === 'function') {
                showMapToast('⚠️ فشل في جلب سجلات الموقع!', 'error');
            }
            return;
        }

        if (!locs || locs.length === 0) {
            if (typeof clearMapLayers === 'function') clearMapLayers();
            const statsEl = document.getElementById('map-stats');
            if (statsEl) statsEl.classList.add('hidden');
            const infoEl = document.getElementById('last-location-info');
            if (infoEl) infoEl.classList.add('hidden');
            if (typeof showMapToast === 'function') {
                showMapToast('⚠️ لا توجد مسارات مسجلة لهذا التاريخ!', 'error');
            }
            return;
        }

        window.allLocations = locs;

        // 1. تنقية وفلترة النقاط الصالحة
        const filterFn = window.filterGPSPoints || (typeof filterGPSPoints === 'function' ? filterGPSPoints : null);
        const filteredLocs = filterFn ? filterFn(locs) : locs;
        if (!filteredLocs || filteredLocs.length === 0) return;

        // 2. مطابقة الطرق عبر OSRM
        const snapFn = window.snapPointsToRoads || (typeof snapPointsToRoads === 'function' ? snapPointsToRoads : null);
        const snappedPts = snapFn ? await snapFn(filteredLocs) : filteredLocs.map(l => [l.latitude, l.longitude]);

        // 3. رسم المسار على الخريطة
        const renderFn = window.renderRouteOnMap || (typeof renderRouteOnMap === 'function' ? renderRouteOnMap : null);
        if (renderFn) {
            renderFn(filteredLocs, snappedPts);
        }

    } catch (e) {
        console.error('[fetchPositions] Error:', e);
        if (refreshBtn) {
            refreshBtn.innerText = '🔄 تحديث المسار';
            refreshBtn.disabled = false;
        }
    }
}

// تصدير الدوال العامة
window.initRouteDatePicker = initRouteDatePicker;
window.toggleLiveTracking = toggleLiveTracking;
window.fetchPositions = fetchPositions;

console.log('✅ [Feature/LiveMap] Controller Initialized');
