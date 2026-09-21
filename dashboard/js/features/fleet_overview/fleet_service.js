/**
 * fleet_service.js — Fleet Device Data Service & Realtime Tracker
 * ─────────────────────────────────────────────────────────────────────────────
 * ميزة إدارة الأسطول (fleet_overview):
 * جلب بيانات الأجهزة وحالتها من السحابة، تصنيف حالة الاتصال (Online/Offline)،
 * تمييز أجهزة ويندوز وأندرويد، ومراقبة التحديثات اللحظية (Realtime).
 */

'use strict';

window.realtimeChannels = window.realtimeChannels || {};

/**
 * فحص ما إذا كان الجهاز متصلاً حالياً استناداً إلى عتبة الاتصال المعتمدة
 */
function isOnline(lastSeenTimestamp) {
    if (!lastSeenTimestamp) return false;
    try {
        const thresholdMinutes = window.ONLINE_THRESHOLD_MINUTES || 30;
        const last = new Date(lastSeenTimestamp).getTime();
        const now = Date.now();
        const diffMinutes = (now - last) / (1000 * 60);
        return diffMinutes <= thresholdMinutes;
    } catch (e) {
        return false;
    }
}

/**
 * استخراج أيقونة نوع الجهاز (دعم أجهزة ويندوز 🖥️ وهواتف أندرويد 📱)
 * إلزامي وفق ميزات المشروع الأساسية (البند 10)
 */
function getDeviceIcon(device) {
    if (!device) return '📱';
    const id = (device.device_id || '').toUpperCase();
    const info = (device.device_info || '').toUpperCase();
    if (id.startsWith('DESKTOP-') || id.includes('WIN') || info.includes('WINDOWS') || info.includes('DESKTOP')) {
        return '🖥️';
    }
    return '📱';
}

/**
 * استخراج طراز الجهاز المختصر من سلسلة معلومات الجهاز
 */
function extractModel(deviceInfo) {
    if (!deviceInfo) return '--';
    const match = deviceInfo.match(/^([^\(]+)/);
    return match ? match[1].trim() : deviceInfo;
}

/**
 * جلب قائمة الأجهزة وحالة البطارية وآخر ظهور من جداول السحابة
 */
async function fetchDevicesWithStatus() {
    const client = window.db || (typeof db !== 'undefined' ? db : null);
    if (!client) throw new Error('عميل Supabase غير مهيأ');

    const { data: devices, error } = await client
        .from('remote_settings')
        .select('*')
        .order('updated_at', { ascending: false });

    if (error) throw error;
    if (!devices || devices.length === 0) return [];

    // جلب آخر قراءة موقع وبطارية لكل جهاز بالتوازي
    const locationPromises = devices.map(d =>
        client.from('locations')
            .select('battery_level, timestamp')
            .eq('device_id', d.device_id)
            .order('timestamp', { ascending: false })
            .limit(1)
            .maybeSingle()
    );

    const locationResults = await Promise.all(locationPromises);

    // دمج وتنسيق البيانات
    return devices.map((device, i) => {
        const locData  = locationResults[i]?.data;
        const battery  = locData?.battery_level ?? '--';
        const lastSeen = locData?.timestamp || device.updated_at;
        const online   = isOnline(lastSeen);
        const icon     = getDeviceIcon(device);
        const model    = extractModel(device.device_info);
        const name     = device.nickname || (device.device_id ? device.device_id.substring(0, 14) : 'جهاز غير معروف');

        return {
            ...device,
            lastSeen,
            battery,
            isOnline: online,
            icon,
            model,
            name
        };
    });
}

/**
 * الاشتراك في التحديثات اللحظية للجهاز النشط
 */
function subscribeToRealtimeUpdates(deviceId) {
    if (!deviceId) return;
    const client = window.db || (typeof db !== 'undefined' ? db : null);
    if (!client) return;

    // تنظيف أي اشتراك سابق للجهاز
    const prev = window.realtimeChannels[deviceId];
    if (prev) {
        try { client.removeChannel(prev); } catch (e) {}
        delete window.realtimeChannels[deviceId];
    }

    try {
        const channel = client.channel(`device-updates-${deviceId}`)
            .on('postgres_changes', {
                event: '*',
                schema: 'public',
                table: 'remote_settings',
                filter: `device_id=eq.${deviceId}`
            }, payload => {
                if (typeof syncSettingsFromDB === 'function') {
                    syncSettingsFromDB(payload.new);
                }
            })
            .subscribe();

        window.realtimeChannels[deviceId] = channel;
    } catch (e) {
        console.warn('[subscribeToRealtimeUpdates] Error:', e);
    }
}

// تصدير الدوال للنافذة العامة
window.isOnline = isOnline;
window.getDeviceIcon = getDeviceIcon;
window.extractModel = extractModel;
window.fetchDevicesWithStatus = fetchDevicesWithStatus;
window.subscribeToRealtimeUpdates = subscribeToRealtimeUpdates;

console.log('✅ [Feature/FleetOverview] Service Initialized');
