/**
 * fleet_logic.js v11.0
 * Hoverwatch-style Remote Management Dashboard
 * Supabase: kubowqqqawkgghxcktoe.supabase.co
 */

console.log('🚀 fleet_logic.js: Script started loading...');

'use strict';

// ═══════════════════════════════════════════════════════════
//  CONFIG (Imported from js/core/supabase_client.js)
// ═══════════════════════════════════════════════════════════
// SUPABASE_URL, SUPABASE_KEY, db, and ONLINE_THRESHOLD_MINUTES are provided globally by supabase_client.js

// ═══════════════════════════════════════════════════════════
//  STATE
// ═══════════════════════════════════════════════════════════
let currentDeviceId  = null;
let isAudioPlayingGlobal = false;
let audioQueue = [];
let currentQueueIndex = -1;
let cachedAudioStates = { heard: {}, favorites: {}, names: {} };

// ── Global Command Handlers (Available immediately) ─────────────────────────
window.triggerMic = function() {
    if (typeof triggerRecordNow === 'function' && triggerRecordNow !== window.triggerMic) {
        return triggerRecordNow();
    } else if (typeof sendCommand === 'function') {
        return sendCommand('RECORD_AUDIO');
    }
};
let currentTab       = 'reports';
let reportFilter     = 'all';
// Live Map state & layers are delegated to js/features/live_map/map_service.js
let usageChartInst   = null;
let autoRefreshTimer = null;
let logsRealtimeSub  = null;
let realtimeChannels = {};   // keyed by deviceId

// ── Live Mic State ────────────────────────────────────────────────────────────
let micStreamChannel = null;
let liveMicAudioCtx  = null;
let liveMicNextTime  = 0;
let isLiveMicActive  = false;
let liveMicAnalyser  = null;
let liveMicDataArray = null;
let visualizerReqId  = null;
const MIC_SAMPLE_RATE = 16_000;


// ═══════════════════════════════════════════════════════════
//  INIT & GLOBAL PROTECTION
// ═══════════════════════════════════════════════════════════
window.onunhandledrejection = (e) => {
    console.warn('[Global] Suppressing unhandled rejection:', e.reason);
    e.preventDefault();
};

document.addEventListener('DOMContentLoaded', () => {
    if (typeof checkSession === 'function') checkSession();
    if (typeof initSessionListener === 'function') initSessionListener();
});

// ═══════════════════════════════════════════════════════════
//  AUTH — SESSION MANAGEMENT (Delegated to core/session_controller.js)
// ═══════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════
//  FLEET OVERVIEW (Delegated to js/features/fleet_overview/)
// ═══════════════════════════════════════════════════════════
async function loadDeviceList() {
    if (typeof window.loadDeviceList === 'function' && window.loadDeviceList !== loadDeviceList) {
        return window.loadDeviceList();
    }
}

function cleanupAutoRecord() {
    try {
        if (typeof autoRecordTimer !== 'undefined' && autoRecordTimer) {
            clearInterval(autoRecordTimer);
            autoRecordTimer = null;
        }
    } catch (e) {
        console.warn('[cleanupAutoRecord] Error:', e);
    }
}
window.cleanupAutoRecord = cleanupAutoRecord;

async function selectDevice(deviceId, deviceData = null) {
    if (typeof window.selectDevice === 'function' && window.selectDevice !== selectDevice) {
        return window.selectDevice(deviceId, deviceData);
    }
}

// ═══════════════════════════════════════════════════════════
//  TAB SWITCHING
// ═══════════════════════════════════════════════════════════
function switchTab(tabId) {
    currentTab = tabId;
    window.currentTab = tabId;

    // Visual
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));

    const activeBtn  = document.getElementById(`tab-btn-${tabId}`);
    const activePane = document.getElementById(`tab-${tabId}`);
    if (activeBtn)  activeBtn.classList.add('active');
    if (activePane) activePane.classList.add('active');

    // Update active state in mobile sidebar tab buttons
    document.querySelectorAll('.sidebar-tab-btn').forEach(btn => btn.classList.remove('active'));
    const activeSidebarBtn = document.getElementById(`sidebar-btn-${tabId}`);
    if (activeSidebarBtn) activeSidebarBtn.classList.add('active');

    // Special inits
    if (tabId === 'track') initMap();
    if (tabId === 'logs') subscribeToLogs();
    if (tabId === 'callogs') fetchCallLogs();
    if (tabId === 'webhistory') fetchWebHistory();

    if (currentDeviceId) refreshCurrentData();
}

// ═══════════════════════════════════════════════════════════
//  REALTIME SUBSCRIPTIONS (Fix 3)
// ═══════════════════════════════════════════════════════════
function subscribeToRealtimeUpdates(deviceId) {
    // Clean up any previous subscription for old device from local cache
    const prev = realtimeChannels[deviceId];
    if (prev) {
        try { db.removeChannel(prev); } catch (e) {}
        delete realtimeChannels[deviceId];
    }

    // Also clean up any channel from Supabase's internal pool to prevent duplicates
    try {
        const existing = db.getChannels().find(c => c.topic === `realtime:device-updates-${deviceId}` || c.name === `device-updates-${deviceId}`);
        if (existing) {
            db.removeChannel(existing);
        }
    } catch (e) {
        console.warn('Error removing channel from pool:', e);
    }

    try {
        const channel = db.channel(`device-updates-${deviceId}`);

        channel
            // ── New screenshot/log entry → refresh Reports feed ──────────────
            .on('postgres_changes', {
                event: 'INSERT', schema: 'public', table: 'remote_logs',
                filter: `device_id=eq.${deviceId}`
            }, (payload) => {
                console.log('[Realtime] New log entry:', payload.new.message);

                // Flash the logs nav badge
                const logsBtn = document.getElementById('tab-btn-logs');
                if (logsBtn && currentTab !== 'logs') {
                    logsBtn.style.color = '#f59e0b';
                    setTimeout(() => logsBtn.style.color = '', 2000);
                }

                // Live-prepend if on logs tab
                if (currentTab === 'logs') {
                    const listEl = document.getElementById('logs-list');
                    const emptyEl = listEl?.querySelector('.empty-state');
                    if (emptyEl) emptyEl.remove();
                    const wrapper = document.createElement('div');
                    renderLogs([payload.new], wrapper);
                    if (wrapper.firstChild) listEl?.prepend(wrapper.firstChild);
                }

                // Refresh reports feed if on that tab
                if (currentTab === 'reports') fetchReports();
            })

            // ── New notification → refresh Reports feed ──────────────────────
            .on('postgres_changes', {
                event: 'INSERT', schema: 'public', table: 'notification_logs',
                filter: `device_id=eq.${deviceId}`
            }, () => {
                if (currentTab === 'reports') fetchReports();
            })

            // ── New location → refresh map marker & last-seen ────────────────
            .on('postgres_changes', {
                event: 'INSERT', schema: 'public', table: 'locations',
                filter: `device_id=eq.${deviceId}`
            }, (payload) => {
                const loc = payload.new;
                const batt  = loc.battery_level ?? 0;
                const color = batt > 50 ? '#22c55e' : batt > 20 ? '#f59e0b' : '#ef4444';
                document.getElementById('battery-text').innerText = `${batt}%`;
                document.getElementById('battery-bar').style.cssText = `width:${batt}%;background:${color}`;
                document.getElementById('last-seen-header').innerText = 'الآن';

                const dot = document.getElementById('device-status-dot');
                const txt = document.getElementById('device-status-text');
                dot.className = 'status-dot online';
                txt.innerText = 'متصل الآن';
                txt.className = 'text-sm font-bold text-emerald-400';

                // Update map if on track tab
                if (currentTab === 'track' && mapInstance) fetchPositions();

                showNotif(`📍 موقع جديد: ${loc.latitude?.toFixed(4)}, ${loc.longitude?.toFixed(4)}`, 'info');
            })

            // ── Device assets updates ─────────────────────────────────────────
            .on('postgres_changes', {
                event: '*', schema: 'public', table: 'device_assets',
                filter: `gateway_device_id=eq.${deviceId}`
            }, (payload) => {
                console.log('[Realtime] Device assets changed:', payload);
                if (currentTab === 'assets') fetchAssets();
            })
            // ── WiFi devices updates ──────────────────────────────────────────
            .on('postgres_changes', {
                event: '*', schema: 'public', table: 'connected_devices',
                filter: `gateway_device_id=eq.${deviceId}`
            }, (payload) => {
                console.log('[Realtime] WiFi Devices changed:', payload);
                if (currentTab === 'wifi') fetchWifiDevices();
            });

        // Save channel to cache
        realtimeChannels[deviceId] = channel;

        // Subscribe after setting up callbacks
        channel.subscribe((status, err) => {
            console.log(`[Realtime] Channel ${deviceId} status:`, status);
            if (status === 'CHANNEL_ERROR') {
                console.error(`[Realtime] Error for ${deviceId}:`, err);
                showNotif('⚠️ خطأ في الاتصال بالوقت الفعلي', 'warn');
            }
        });

        console.log(`[Realtime] Subscribed to device: ${deviceId}`);
    } catch (e) {
        console.error('[Realtime] Subscription setup failed:', e);
    }
}


// ═══════════════════════════════════════════════════════════
//  AUTO REFRESH
// ═══════════════════════════════════════════════════════════
function startAutoRefresh() {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = setInterval(async () => {
        await loadDeviceList();
        if (currentDeviceId) await refreshCurrentData();
        flashIndicator();
    }, 30_000);
    flashIndicator();
}

function flashIndicator() {
    const dot = document.getElementById('auto-refresh-indicator');
    if (!dot) return;
    dot.classList.replace('bg-slate-700', 'bg-emerald-400');
    setTimeout(() => dot.classList.replace('bg-emerald-400', 'bg-slate-700'), 800);
}

// ═══════════════════════════════════════════════════════════
//  REFRESH CURRENT DATA
// ═══════════════════════════════════════════════════════════
async function refreshCurrentData() {
    if (!currentDeviceId) return;
    try {
        // Fetch locations & remote_settings in parallel
        const [locRes, settingsRes] = await Promise.all([
            db.from('locations').select('battery_level, timestamp, accuracy')
                .eq('device_id', currentDeviceId)
                .order('timestamp', { ascending: false }).limit(1).maybeSingle(),
            db.from('remote_settings').select('current_version_code, device_info, target_version, updated_at')
                .eq('device_id', currentDeviceId).maybeSingle()
        ]);

        const loc = locRes.data;
        const info = settingsRes.data;

        // Determine last seen timestamp
        let lastSeenTimestamp = null;
        if (loc && loc.timestamp) {
            lastSeenTimestamp = loc.timestamp;
        }
        if (info && info.updated_at) {
            if (!lastSeenTimestamp || new Date(info.updated_at) > new Date(lastSeenTimestamp)) {
                lastSeenTimestamp = info.updated_at;
            }
        }

        // Determine online status
        const online = lastSeenTimestamp ? isOnline(lastSeenTimestamp) : false;
        
        // Update header last-seen text and dot
        const dot = document.getElementById('device-status-dot');
        const txt = document.getElementById('device-status-text');
        if (dot && txt) {
            dot.className = 'status-dot ' + (online ? 'online' : 'offline');
            txt.innerText = online ? 'متصل الآن' : 'غير متصل';
            txt.className = 'text-sm font-bold ' + (online ? 'text-emerald-400' : 'text-slate-400');
        }
        
        const lastSeenEl = document.getElementById('last-seen-header');
        if (lastSeenEl && lastSeenTimestamp) {
            lastSeenEl.innerText = formatRelativeTime(lastSeenTimestamp);
        }

        // Update battery if available
        if (loc) {
            const batt  = loc.battery_level ?? 0;
            const color = batt > 50 ? '#22c55e' : batt > 20 ? '#f59e0b' : '#ef4444';
            const battTxt = document.getElementById('battery-text');
            const battBar = document.getElementById('battery-bar');
            if (battTxt) battTxt.innerText = `${batt}%`;
            if (battBar) battBar.style.cssText = `width:${batt}%;background:${color}`;
        }

        // Tab-specific refresh
        switch (currentTab) {
            case 'reports':     await fetchReports(); break;
            case 'assets':      await fetchAssets(); break;
            case 'screenshots': await fetchScreenshots(); break;
            case 'track':       await fetchPositions(); break;
            case 'audio':       await fetchAudio(); break;
            case 'usage':       await fetchUsage(); break;
            case 'logs':        await fetchLogs(); break;
            case 'wifi':        await fetchWifiDevices(); break;
            case 'webhistory':  await fetchWebHistory(); break;
        }

        // Version info in header
        if (info?.current_version_code) {
            const repVer = document.getElementById('reported-version');
            if (repVer) {
                repVer.innerText = `v${info.current_version_code}`;
                repVer.classList.remove('hidden');
            }
            const versionEl = document.getElementById('current-apk-version');
            if (versionEl) versionEl.innerText = `v${info.current_version_code} مثبت`;
        }

    } catch (e) { console.warn('Refresh error:', e); }
}

// ═══════════════════════════════════════════════════════════
//  REPORTS FEED
// ═══════════════════════════════════════════════════════════
function setReportFilter(f) {
    reportFilter = f;
    document.querySelectorAll('.filter-pill').forEach(el => {
        el.classList.remove('bg-blue-500/20', 'text-blue-400', 'border-blue-500/30');
        el.classList.add('bg-white/5', 'text-slate-500', 'border-white/10');
    });
    const active = document.getElementById(`filter-${f}`);
    if (active) {
        active.classList.remove('bg-white/5', 'text-slate-500', 'border-white/10');
        active.classList.add('bg-blue-500/20', 'text-blue-400', 'border-blue-500/30');
    }
    fetchReports();
}

async function fetchReports() {
    if (!currentDeviceId) return;
    const container = document.getElementById('reports-feed');
    container.innerHTML = '<div class="p-6 flex justify-center"><div class="spinner"></div></div>';
    try {
        let items = [];
        const devId = (currentDeviceId || '').toLowerCase();

        // Screenshots metadata
        if (reportFilter === 'all' || reportFilter === 'screenshots') {
            const { data: shots } = await db.from('remote_logs')
                .select('*').ilike('device_id', devId).eq('tag', 'ScreenCaptureEngine')
                .order('created_at', { ascending: false }).limit(40);
            if (shots) items.push(...shots.map(s => ({ ...s, _type: 'screenshot' })));
        }

        // Notifications
        if (reportFilter === 'all' || reportFilter === 'notifications') {
            const { data: notifs } = await db.from('notification_logs')
                .select('*').ilike('device_id', devId)
                .order('post_time', { ascending: false }).limit(40);
            if (notifs) items.push(...notifs.map(n => ({ ...n, created_at: n.post_time, _type: 'notification' })));
        }

        // Events / remote logs
        if (reportFilter === 'all' || reportFilter === 'events') {
            const { data: events } = await db.from('remote_logs')
                .select('*').ilike('device_id', devId).neq('tag', 'ScreenCaptureEngine')
                .order('created_at', { ascending: false }).limit(30);
            if (events) items.push(...events.map(e => ({ ...e, _type: 'event' })));
        }

        // Sort by time descending
        items.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
        items = items.slice(0, 80);

        if (items.length === 0) {
            container.innerHTML = '<div class="empty-state"><p class="text-sm">لا توجد تقارير للجهاز المختار</p></div>';
            return;
        }

        container.innerHTML = items.map(item => renderFeedItem(item)).join('');
    } catch (e) {
        console.error('fetchReports:', e);
        container.innerHTML = '<div class="empty-state text-red-400 text-xs"><p>خطأ في جلب التقارير</p></div>';
    }
}

function renderFeedItem(item) {
    let icon, label, body, iconBg;
    switch (item._type) {
        case 'screenshot':
            icon = '📸'; iconBg = 'bg-blue-500/15';
            label = `<span class="px-2 py-0.5 text-[10px] font-black bg-blue-500/10 text-blue-400 rounded-full border border-blue-500/15">SCREENSHOT</span>`;
            body = item.message || '';
            break;
        case 'notification':
            icon = '🔔'; iconBg = 'bg-purple-500/15';
            label = `<span class="px-2 py-0.5 text-[10px] font-black bg-purple-500/10 text-purple-400 rounded-full border border-purple-500/15">${item.app_name || item.package_name || 'APP'}</span>`;
            body = `<b>${item.title || ''}</b> — ${item.content || ''}`;
            break;
        default:
            icon = item.level === 'ERROR' ? '🔴' : item.level === 'WARN' ? '🟡' : '🔵';
            iconBg = item.level === 'ERROR' ? 'bg-red-500/10' : item.level === 'WARN' ? 'bg-amber-500/10' : 'bg-blue-500/10';
            label = `<span class="text-[10px] font-bold text-slate-500">${item.tag || 'SYS'}</span>`;
            body = item.message || '';
    }
    return `
        <div class="feed-item">
            <div class="feed-icon ${iconBg}">${icon}</div>
            <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">${label}
                    <span class="text-[10px] text-slate-600">${formatRelativeTime(item.created_at)}</span>
                </div>
                <p class="text-xs text-slate-300 leading-relaxed truncate">${body}</p>
            </div>
        </div>`;
}

// ── Secure Blob Loaders to Bypass ISP blocks on Static Public URL endpoints ──
async function loadImgBlob(imgElement, filePath) {
    try {
        const { data, error } = await db.storage.from('monitoring_data').download(filePath);
        if (error) throw error;
        const blobUrl = URL.createObjectURL(data);
        imgElement.src = blobUrl;
    } catch (e) {
        console.error('[loadImgBlob] Failed:', e);
    }
}

// ── Audio Player Functions (Delegated to js/features/audio_vault/audio_player.js & audio_date_parser.js) ──


async function downloadAudioBlob(filePath, fileName) {
    try {
        showNotif('⏳ جاري تحميل الملف للحفظ...', 'info');
        const { data, error } = await db.storage.from('monitoring_data').download(filePath);
        if (error) throw error;

        const blobUrl = URL.createObjectURL(data);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(blobUrl);
        showNotif('✅ تم تحميل الملف بنجاح', 'success');
    } catch (e) {
        console.error('[downloadAudioBlob] Failed:', e);
        showNotif('❌ فشل تحميل الملف: ' + e.message, 'error');
    }
}

window.loadImgBlob = loadImgBlob;
window.getSharedAudioPlayer = getSharedAudioPlayer;
window.loadAudioBlob = loadAudioBlob;
window.downloadAudioBlob = downloadAudioBlob;

// ═══════════════════════════════════════════════════════════
//  SCREENSHOTS
// ═══════════════════════════════════════════════════════════
async function fetchScreenshots() {
    if (!currentDeviceId) return;
    const grid = document.getElementById('screenshots-grid');
    grid.innerHTML = '<div class="col-span-full p-8 flex justify-center"><div class="spinner"></div></div>';
    try {
        const devId = currentDeviceId;

        // 1. صور لقطات الشاشة (screenshots/)
        const { data: ssFiles } = await db.storage
            .from('monitoring_data')
            .list(`screenshots/${devId}`, { limit: 1000, sortBy: { column: 'created_at', order: 'desc' } });

        // 2. صور المعرض والتطبيقات (photos/)
        const { data: photoFiles } = await db.storage
            .from('monitoring_data')
            .list(`photos/${devId}`, { limit: 1000, sortBy: { column: 'created_at', order: 'desc' } });

        // دمج وتحديد المصدر
        const allFiles = [];
        (ssFiles || []).filter(f => f.name.match(/\.(webp|jpg|png|jpeg)$/i)).forEach(f => {
            allFiles.push({ ...f, _folder: `screenshots/${devId}`, _type: 'screen' });
        });
        (photoFiles || []).filter(f => f.name.match(/\.(jpg|jpeg|png|webp)$/i)).forEach(f => {
            allFiles.push({ ...f, _folder: `photos/${devId}`, _type: 'photo' });
        });

        if (allFiles.length === 0) {
            grid.innerHTML = '<div class="col-span-full empty-state"><p class="text-sm">لا توجد صور ملتقطة</p></div>';
            return;
        }

        // ترتيب من الأحدث للأقدم
        allFiles.sort((a, b) => new Date(b.created_at || 0) - new Date(a.created_at || 0));

        const html = allFiles.map(f => {
            const filePath = `${f._folder}/${f.name}`;
            const time = formatRelativeTime(f.created_at);
            const badge = f._type === 'photo'
                ? `<span class="absolute top-2 right-2 text-[10px] px-1.5 py-0.5 rounded-full bg-emerald-500/80 text-white font-bold">📸 صورة</span>`
                : `<span class="absolute top-2 right-2 text-[10px] px-1.5 py-0.5 rounded-full bg-blue-500/80 text-white font-bold">📱 شاشة</span>`;
            return `
                <div class="screenshot-card animate-fade" onclick="openLightbox(this.querySelector('img').src)">
                    <div class="relative">
                        <img data-path="${filePath}" src="" alt="${f.name}"
                             class="lazy-blob-img w-full object-cover aspect-[9/16] bg-slate-900"
                             loading="lazy" onerror="this.parentElement.parentElement.style.display='none'">
                        ${badge}
                        <div class="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/80 to-transparent p-3">
                            <p class="text-[10px] text-slate-300 font-mono">${time}</p>
                        </div>
                    </div>
                </div>`;
        }).join('');

        grid.innerHTML = html || '<div class="col-span-full empty-state"><p>لا توجد صور</p></div>';

        // تحميل الصور عبر Blob URLs (آمن)
        document.querySelectorAll('.lazy-blob-img').forEach(img => {
            const path = img.getAttribute('data-path');
            if (path) loadImgBlob(img, path);
        });

    } catch (e) {
        console.error('fetchScreenshots:', e);
        grid.innerHTML = '<div class="col-span-full empty-state text-red-400 text-xs"><p>خطأ في تحميل الصور</p></div>';
    }
}

function openLightbox(url) {
    document.getElementById('lightbox-img').src = url;
    document.getElementById('lightbox').classList.remove('hidden');
}
function closeLightbox() {
    document.getElementById('lightbox').classList.add('hidden');
}

// ═══════════════════════════════════════════════════════════
//  MAP — LIVE TRACK (Delegated to js/features/live_map/)
// ═══════════════════════════════════════════════════════════
function initMap() {
    if (typeof window.initMap === 'function' && window.initMap !== initMap) {
        return window.initMap();
    }
}

function switchMapStyle() {
    if (typeof window.switchMapStyle === 'function' && window.switchMapStyle !== switchMapStyle) {
        return window.switchMapStyle();
    }
}

function fetchPositions(targetId) {
    const id = targetId || currentDeviceId;
    if (typeof window.fetchPositions === 'function' && window.fetchPositions !== fetchPositions) {
        return window.fetchPositions(id);
    }
}

function toggleLiveTracking() {
    if (typeof window.toggleLiveTracking === 'function' && window.toggleLiveTracking !== toggleLiveTracking) {
        return window.toggleLiveTracking();
    }
}

function toggleHeatmap() {
    if (typeof window.toggleHeatmap === 'function' && window.toggleHeatmap !== toggleHeatmap) {
        return window.toggleHeatmap();
    }
}

// ═══════════════════════════════════════════════════════════
//  AUDIO VAULT
// ═══════════════════════════════════════════════════════════

/** Parse datetime from filename like call_20240623_142533.m4a or rec_20240623_142533.m4a */
function parseFilenameDate(name) {
    const m = name.match(/(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})(\d{2})/);
    if (!m) return null;
    return new Date(`${m[1]}-${m[2]}-${m[3]}T${m[4]}:${m[5]}:${m[6]}`);
}

/** Estimate recording duration from file size */
function estimateDuration(sizeBytes, isCall) {
    if (!sizeBytes || sizeBytes < 1024) return '—';
    const kbps = isCall ? 16 : 12;
    const secs = Math.round((sizeBytes * 8) / (kbps * 1000));
    if (secs < 60) return `${secs}ث`;
    return `${Math.floor(secs/60)}د ${secs%60}ث`;
}

let audioFilter = 'all';
window.setAudioFilter = function(f) {
    audioFilter = f;
    document.querySelectorAll('.audio-filter-btn').forEach(b => {
        const isActive = b.getAttribute('data-filter') === f;
        if (isActive) {
            b.className = 'audio-filter-btn px-3 py-1 text-xs font-bold rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30';
        } else {
            b.className = 'audio-filter-btn px-3 py-1 text-xs font-bold rounded-full bg-white/5 text-slate-400 border border-white/10 hover:bg-white/8';
        }
    });
    fetchAudio(false, true);
};

// ═══════════════════════════════════════════════════════════
//  AUDIO AMPLIFIER / VOLUME BOOSTER (1x - 5x)
// ═══════════════════════════════════════════════════════════
let currentAudioBoost = 1.0;
let playbackAudioCtx = null;
let playbackGainNode = null;
let playbackSourceNodeMap = new WeakMap();

function setAudioBoost(multiplier) {
    currentAudioBoost = parseFloat(multiplier) || 1.0;
    
    document.querySelectorAll('.audio-boost-btn').forEach(btn => {
        const boostVal = parseFloat(btn.getAttribute('data-boost'));
        if (boostVal === currentAudioBoost) {
            btn.className = 'audio-boost-btn px-2.5 py-1 text-xs font-bold rounded-lg bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 hover:bg-emerald-500/30 transition-all';
        } else {
            btn.className = 'audio-boost-btn px-2.5 py-1 text-xs font-bold rounded-lg bg-white/5 text-slate-300 border border-white/10 hover:bg-white/10 transition-all';
        }
    });

    const textEl = document.getElementById('boost-level-text');
    if (textEl) {
        if (currentAudioBoost === 1) textEl.innerText = '1x (عادي)';
        else if (currentAudioBoost === 2) textEl.innerText = '2x (مضاعف)';
        else if (currentAudioBoost === 3) textEl.innerText = '3x (قوي)';
        else if (currentAudioBoost === 4) textEl.innerText = '4x (فائق)';
        else textEl.innerText = `🚀 ${currentAudioBoost}x (أقصى قوة)`;
    }

    if (playbackGainNode) {
        playbackGainNode.gain.value = currentAudioBoost;
    }
    
    showNotif(`⚡ تم ضبط مضخم الصوت على ${currentAudioBoost}x`, 'info');
}

function applyAudioAmplifier(audioElement) {
    if (!audioElement) return;
    try {
        const AudioContextClass = window.AudioContext || /** @type {any} */ (window).webkitAudioContext;
        if (!AudioContextClass) return;

        if (!playbackAudioCtx) {
            playbackAudioCtx = new AudioContextClass();
        }

        if (playbackAudioCtx.state === 'suspended') {
            playbackAudioCtx.resume().catch(() => {});
        }

        if (!playbackGainNode) {
            playbackGainNode = playbackAudioCtx.createGain();
            playbackGainNode.connect(playbackAudioCtx.destination);
        }

        playbackGainNode.gain.value = currentAudioBoost;

        if (!playbackSourceNodeMap.has(audioElement)) {
            const source = playbackAudioCtx.createMediaElementSource(audioElement);
            source.connect(playbackGainNode);
            playbackSourceNodeMap.set(audioElement, source);
        }
    } catch (e) {
        console.warn('[AudioAmplifier] Failed to connect GainNode:', e);
    }
}

// (Duplicate getSharedAudioPlayer & loadAudioBlob removed — handled by unified versions above)

function resetAudioDateTimeFilter() {
    const fromEl = document.getElementById('audio-filter-from');
    const toEl   = document.getElementById('audio-filter-to');
    if (fromEl) fromEl.value = '';
    if (toEl)   toEl.value   = '';
    fetchAudio(false, true);
}

async function fetchAudio(force = false, isUserAction = false) {
    if (!currentDeviceId) return;

    // ── 1. Check if audio is currently playing or queued ───────────────────────
    const isAudioPlaying = isAudioPlayingGlobal || 
        (typeof audioQueue !== 'undefined' && audioQueue && audioQueue.length > 0) ||
        Array.from(document.querySelectorAll('audio')).some(a => a && !a.paused && !a.ended && a.currentTime > 0);

    // Rule 7 Guard: Don't interrupt audio playback during auto-refreshes (unless explicitly triggered by user manual click)
    if (!isUserAction && isAudioPlaying) {
        console.log('🔊 Audio playback/playlist in progress, skipping auto-refresh to prevent interruption.');
        return;
    }

    // ── 2. Check if user currently has checkbox selections active ─────────────
    const activeCheckedBoxes = document.querySelectorAll('.audio-checkbox:checked');
    const checkedPaths = new Set(Array.from(activeCheckedBoxes).map(cb => cb.dataset.path || cb.getAttribute('data-path')).filter(Boolean));

    // Selection Guard: Don't wipe active user checkbox selections during background auto-refreshes
    if (!isUserAction && checkedPaths.size > 0) {
        console.log(`☑️ User has ${checkedPaths.size} selected items, skipping auto-refresh to preserve selection.`);
        return;
    }

    const list = document.getElementById('audio-list');
    if (!list) return;

    list.innerHTML = '<div class="p-6 flex justify-center"><div class="spinner"></div></div>';
    
    // Load states from storage first
    await loadAudioStatesFromStorage();

    try {
        console.log(`[fetchAudio] Fetching audio files for deviceId: ${currentDeviceId} from bucket: monitoring_data/audio/${currentDeviceId}`);
        const { data: files, error: listErr } = await db.storage
            .from('monitoring_data')
            .list(`audio/${currentDeviceId}`, { limit: 1000, sortBy: { column: 'created_at', order: 'desc' } });

        if (listErr) {
            console.error('[fetchAudio] Supabase storage list error:', listErr);
            throw listErr;
        }

        console.log(`[fetchAudio] Storage returned ${files?.length || 0} items for ${currentDeviceId}`);

        if (!files || files.length === 0) {
            list.innerHTML = '<div class="empty-state text-sm"><p>لا توجد تسجيلات صوتية</p></div>';
            return;
        }

        const audioFiles = files.filter(f => f.name.match(/\.(m4a|mp3|aac|wav|ogg|webm)$/i));
        console.log(`[fetchAudio] Filtered ${audioFiles.length} valid audio files for rendering`);
        if (audioFiles.length === 0) {
            list.innerHTML = '<div class="empty-state text-sm"><p>لا توجد تسجيلات صوتية</p></div>';
            return;
        }

        // Sort explicitly newest to oldest
        audioFiles.sort((a, b) => {
            const tA = a.created_at ? new Date(a.created_at).getTime() : 0;
            const tB = b.created_at ? new Date(b.created_at).getTime() : 0;
            if (tA !== tB) return tB - tA;
            return b.name.localeCompare(a.name);
        });

        const heard = getHeardAudios();
        let filteredAudioFiles = audioFiles;
        if (audioFilter === 'heard') {
            filteredAudioFiles = audioFiles.filter(f => heard[`audio/${currentDeviceId}/${f.name}`]);
        } else if (audioFilter === 'unheard') {
            filteredAudioFiles = audioFiles.filter(f => !heard[`audio/${currentDeviceId}/${f.name}`]);
        } else if (audioFilter === 'favorites') {
            filteredAudioFiles = audioFiles.filter(f => isAudioFavorite(`audio/${currentDeviceId}/${f.name}`));
        }

        // Apply datetime local filtering
        const fromVal = document.getElementById('audio-filter-from')?.value;
        const toVal = document.getElementById('audio-filter-to')?.value;

        if (fromVal || toVal) {
            const getAudioFileTimestamp = (fileObj) => {
                if (!fileObj || !fileObj.name) return 0;
                const m = fileObj.name.match(/_(20\d{6})_(\d{6})/);
                if (m) {
                    const dateStr = m[1]; // yyyyMMdd
                    const timeStr = m[2]; // HHmmss
                    const year = parseInt(dateStr.slice(0, 4), 10);
                    const month = parseInt(dateStr.slice(4, 6), 10) - 1;
                    const day = parseInt(dateStr.slice(6, 8), 10);
                    const hour = parseInt(timeStr.slice(0, 2), 10);
                    const minute = parseInt(timeStr.slice(2, 4), 10);
                    const second = parseInt(timeStr.slice(4, 6), 10);
                    return new Date(year, month, day, hour, minute, second).getTime();
                }
                const ts = fileObj.created_at || fileObj.metadata?.created_at;
                return ts ? new Date(ts).getTime() : 0;
            };

            if (fromVal) {
                const fromMs = new Date(fromVal).getTime();
                filteredAudioFiles = filteredAudioFiles.filter(f => getAudioFileTimestamp(f) >= fromMs);
            }
            if (toVal) {
                const toMs = new Date(toVal).getTime();
                filteredAudioFiles = filteredAudioFiles.filter(f => getAudioFileTimestamp(f) <= toMs);
            }
        }

        // ── Toolbar: select-all + play-selected + delete-selected ──────────────────────
        const toolbar = document.createElement('div');
        toolbar.id = 'audio-toolbar';
        toolbar.className = 'flex items-center justify-between px-4 py-3 mb-2 rounded-xl bg-white/3 border border-white/5';
        toolbar.innerHTML = `
            <label class="flex items-center gap-2 cursor-pointer select-none">
                <input type="checkbox" id="audio-select-all" onchange="toggleSelectAllAudio(this.checked)"
                    class="w-4 h-4 accent-blue-500 cursor-pointer">
                <span class="text-xs font-bold text-slate-400">تحديد الكل</span>
                <span id="audio-selected-count" class="text-xs text-blue-400 font-black hidden"></span>
            </label>
            <div class="flex items-center gap-6">
                <button id="audio-play-selected-btn" onclick="playSelectedAudio()"
                    class="px-4 py-1.5 text-xs font-black rounded-lg transition-all hidden"
                    style="background:rgba(34,197,94,0.15);border:1px solid rgba(34,197,94,0.35);color:#a7f3d0;">
                    🔊 استماع للمحدد
                </button>
                <button id="audio-delete-selected-btn" onclick="deleteSelectedAudio()"
                    class="px-4 py-1.5 text-xs font-black rounded-lg transition-all hidden"
                    style="background:rgba(239,68,68,0.15);border:1px solid rgba(239,68,68,0.35);color:#fca5a5;">
                    🗑️ حذف المحدد
                </button>
            </div>`;
        list.innerHTML = '';
        list.appendChild(toolbar);

        if (filteredAudioFiles.length === 0) {
            const emptyEl = document.createElement('div');
            emptyEl.className = 'empty-state empty-state-filtered text-sm';
            emptyEl.innerHTML = audioFilter === 'heard' ? '<p>لا توجد تسجيلات مسموعة</p>' : (audioFilter === 'unheard' ? '<p>لا توجد تسجيلات غير مسموعة</p>' : '<p>لا توجد تسجيلات صوتية</p>');
            list.appendChild(emptyEl);
            return;
        }

        filteredAudioFiles.forEach((f, i) => {
            const sizeKB = f.metadata?.size ? Math.round(f.metadata.size / 1024) : '?';
            const isCall = f.name.startsWith('call_');
            const icon   = isCall ? '📞' : '🎙️';
            const iconBg = isCall ? 'bg-blue-500/15 border-blue-500/20' : 'bg-slate-700/60 border-slate-600/40';
            const label  = isCall
                ? '<span class="text-[10px] font-bold text-blue-400">مكالمة مسجلة</span>'
                : '<span class="text-[10px] font-bold text-slate-400">تسجيل عن بُعد (تنصت)</span>';
            const filePath = `audio/${currentDeviceId}/${f.name}`;
            const cardId   = f.name.replace(/\./g, '_');
            const isHeard  = !!heard[filePath];
            const isFav    = isAudioFavorite(filePath);
            const isPreviouslyChecked = checkedPaths.has(filePath);
            const customName = getCustomAudioName(filePath);
            const displayName = customName 
                ? `<span class="text-yellow-400 font-bold">⭐ ${customName}</span> <span class="text-[10px] text-slate-500">(${f.name})</span>` 
                : f.name;

            const card = document.createElement('div');
            card.className = 'audio-card animate-fade';
            card.id = `audio-card-${cardId}`;
            card.innerHTML = `
                <div class="audio-card-top flex items-center gap-3 w-full">
                    <input type="checkbox" class="audio-checkbox w-4 h-4 accent-blue-500 flex-shrink-0 cursor-pointer"
                        data-path="${filePath}" data-cardid="${cardId}" data-createdat="${f.created_at}"
                        ${isPreviouslyChecked ? 'checked' : ''}
                        onchange="updateAudioSelectionUI()">
                    <div class="flex items-center gap-3 flex-1 min-w-0">
                        <div class="w-10 h-10 rounded-xl ${iconBg} border flex items-center justify-center text-lg flex-shrink-0">${icon}</div>
                        <div class="min-w-0 flex-1">
                            <div class="flex items-center gap-2 mb-0.5 flex-wrap">
                                ${label}
                                <span class="audio-heard-badge text-[10px] font-bold px-2 py-0.5 rounded cursor-pointer select-none transition-all flex items-center gap-1 ${
                                    isHeard 
                                    ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 hover:text-emerald-300' 
                                    : 'bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500/20 hover:text-blue-300'
                                }" onclick="toggleAudioHeardState('${filePath}', '${cardId}', event)">
                                    ${isHeard ? '🎧 مسموع' : '🆕 غير مسموع'}
                                </span>
                                ${isFav ? '<span class="text-[10px] font-bold px-2 py-0.5 rounded bg-yellow-500/10 border border-yellow-500/20 text-yellow-400">⭐ مفضل</span>' : ''}
                            </div>
                            <p class="text-sm font-bold text-slate-200 truncate">${displayName}</p>
                            <p class="text-xs text-slate-400 mt-0.5">${formatAudioTimestamp(f.name, f.created_at)} · ${sizeKB} KB</p>
                        </div>
                    </div>
                </div>

                <div class="audio-progress-container flex items-center gap-2 w-full my-1 px-2 py-1.5 bg-slate-900/60 rounded-xl border border-white/5">
                    <span class="audio-time-current text-[11px] font-mono text-emerald-400 font-bold min-w-[36px] text-center select-none">00:00</span>
                    <input type="range" class="audio-progress-bar flex-1 h-2 bg-slate-700/80 rounded-lg appearance-none cursor-pointer accent-emerald-500 outline-none"
                           min="0" max="100" value="0" step="0.1"
                           oninput="seekAudioFromCard(this, '${cardId}')">
                    <span class="audio-time-duration text-[11px] font-mono text-slate-400 font-bold min-w-[36px] text-center select-none">00:00</span>
                </div>

                <div class="audio-actions flex items-center gap-2 w-full justify-end border-t border-white/5 pt-2">
                    <span class="audio-status-text text-xs text-slate-400 hidden"></span>
                    <div class="audio-slot flex items-center hidden"></div>
                    <button onclick="loadAudioBlob(this, '${filePath}', '${cardId}')"
                            class="audio-play-btn px-3.5 py-1.5 text-xs font-bold bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-lg hover:bg-emerald-500/25 transition-all flex items-center gap-1">
                        ▶️ تشغيل
                    </button>
                    <button onclick="toggleAudioFavorite('${filePath}', '${cardId}')"
                       title="إضافة/إزالة من المفضلة"
                       class="w-8 h-8 rounded-lg bg-yellow-500/10 border border-yellow-500/20 text-yellow-400
                              hover:bg-yellow-500/25 hover:text-yellow-300 flex items-center justify-center
                              text-xs transition-all flex-shrink-0">${isFav ? '★' : '☆'}</button>
                    <button onclick="renameAudio('${filePath}', '${cardId}')"
                       title="تغيير اسم التسجيل"
                       class="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400
                              hover:bg-blue-500/25 hover:text-blue-300 flex items-center justify-center
                              text-xs transition-all flex-shrink-0">✏️</button>
                    <button onclick="downloadAudioBlob('${filePath}', '${f.name}')"
                       title="تحميل التسجيل"
                       class="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 text-blue-400
                              hover:bg-blue-500/25 hover:text-blue-300 flex items-center justify-center
                              text-xs transition-all flex-shrink-0">⬇️</button>
                    <button onclick="deleteAudio('${filePath}', '${cardId}')"
                         title="حذف التسجيل"
                         class="w-8 h-8 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400
                                hover:bg-red-500/25 hover:text-red-300 flex items-center justify-center
                                text-xs transition-all flex-shrink-0">🗑️</button>
                </div>`;
            list.appendChild(card);
        });

        // Re-sync active selection UI counts and buttons
        updateAudioSelectionUI();

    } catch (e) {
        console.error('fetchAudio:', e);
        list.innerHTML = '<div class="empty-state text-red-400 text-xs"><p>خطأ في تحميل التسجيلات: ' + e.message + '</p></div>';
    }
}


/**
 * Delete a recording from Supabase Storage.
 * Uses supabase.storage.remove() (array of paths).
 */
async function deleteAudio(filePath, cardId) {
    const isFav = isAudioFavorite(filePath);
    const fileName = filePath.split('/').pop();
    const promptMsg = isFav 
        ? `⚠️ تحذير: هذا التسجيل في قائمة المفضل.\nهل تريد بالتأكيد حذفه من الهاتف وقاعدة البيانات بشكل دائم؟`
        : `حذف هذا التسجيل؟\n${fileName}`;
        
    if (!confirm(promptMsg)) return;
    try {
        const { error } = await db.storage.from('monitoring_data').remove([filePath]);
        if (error) throw error;
        
        // Remove from local storage lists as well
        const favs = getFavoriteAudios();
        if (favs[filePath]) {
            delete favs[filePath];
            localStorage.setItem('favorite_recordings', JSON.stringify(favs));
        }
        const names = getCustomAudioNames();
        if (names[filePath]) {
            delete names[filePath];
            localStorage.setItem('custom_audio_names', JSON.stringify(names));
        }
        
        // Animate card out then remove
        const card = document.getElementById(`audio-card-${cardId}`);
        if (card) {
            card.style.transition = 'opacity 0.3s, transform 0.3s';
            card.style.opacity    = '0';
            card.style.transform  = 'translateX(20px)';
            setTimeout(() => { card.remove(); checkAudioListEmpty(); }, 320);
        }
        showNotif('🗑️ تم حذف التسجيل', 'success');
    } catch (e) {
        console.error('deleteAudio:', e);
        showNotif('❌ فشل الحذف: ' + (e.message || e), 'error');
    }
}

// ═══════════════════════════════════════════════════════════
//  AUDIO MULTI-SELECT HELPERS
// ═══════════════════════════════════════════════════════════
function updateAudioSelectionUI() {
    const checkboxes = document.querySelectorAll('.audio-checkbox');
    const checked    = document.querySelectorAll('.audio-checkbox:checked');
    const deleteBtn  = document.getElementById('audio-delete-selected-btn');
    const playBtn    = document.getElementById('audio-play-selected-btn');
    const countEl    = document.getElementById('audio-selected-count');
    const selectAll  = document.getElementById('audio-select-all');

    if (playBtn) {
        if (checked.length > 0) {
            playBtn.classList.remove('hidden');
        } else {
            playBtn.classList.add('hidden');
        }
    }
    if (deleteBtn) {
        if (checked.length > 0) {
            deleteBtn.classList.remove('hidden');
            deleteBtn.innerText = `🗑️ حذف ${checked.length} تسجيل`;
        } else {
            deleteBtn.classList.add('hidden');
        }
    }
    if (countEl) {
        if (checked.length > 0) {
            countEl.innerText  = `(${checked.length} محدد)`;
            countEl.classList.remove('hidden');
        } else {
            countEl.classList.add('hidden');
        }
    }
    if (selectAll) {
        selectAll.indeterminate = checked.length > 0 && checked.length < checkboxes.length;
        selectAll.checked = checkboxes.length > 0 && checked.length === checkboxes.length;
    }
}

function toggleSelectAllAudio(checked) {
    document.querySelectorAll('.audio-checkbox').forEach(cb => {
        const path = cb.dataset.path;
        // لا يتم تحديد التسجيلات المفضلة عند "تحديد الكل"
        if (checked && isAudioFavorite(path)) {
            cb.checked = false;
        } else {
            cb.checked = checked;
        }
    });
    updateAudioSelectionUI();
}

async function deleteSelectedAudio() {
    const checked = document.querySelectorAll('.audio-checkbox:checked');
    if (checked.length === 0) return;

    const allPaths = Array.from(checked).map(cb => cb.dataset.path);
    const pathsToDelete = allPaths.filter(p => !isAudioFavorite(p));
    const cardIdsToDelete = Array.from(checked)
        .filter(cb => !isAudioFavorite(cb.dataset.path))
        .map(cb => cb.dataset.cardid);

    const skippedCount = allPaths.length - pathsToDelete.length;

    if (pathsToDelete.length === 0) {
        showNotif('⚠️ تم إلغاء العملية: جميع الملفات المحددة موجودة في المفضلة ولا يمكن حذفها جماعياً.', 'warn');
        return;
    }

    const confirmMsg = `⚠️ حذف ${pathsToDelete.length} تسجيل بشكل دائم؟${skippedCount > 0 ? `\n(تنبيه: سيتم تخطي وتجاهل ${skippedCount} تسجيلات مفضلة)` : ''}`;
    if (!confirm(confirmMsg)) return;

    showNotif(`⏳ جاري حذف ${pathsToDelete.length} تسجيل...`, 'info');

    try {
        const { error } = await db.storage.from('monitoring_data').remove(pathsToDelete);
        if (error) throw error;

        // Clean favorites/names localstorage for deleted paths
        const favs = getFavoriteAudios();
        const names = getCustomAudioNames();
        pathsToDelete.forEach(p => {
            if (favs[p]) delete favs[p];
            if (names[p]) delete names[p];
        });
        localStorage.setItem('favorite_recordings', JSON.stringify(favs));
        localStorage.setItem('custom_audio_names', JSON.stringify(names));

        // animate and remove cards
        cardIdsToDelete.forEach(id => {
            const card = document.getElementById(`audio-card-${id}`);
            if (card) {
                card.style.transition = 'opacity 0.25s, transform 0.25s';
                card.style.opacity    = '0';
                card.style.transform  = 'translateX(20px)';
                setTimeout(() => { card.remove(); checkAudioListEmpty(); }, 280);
            }
        });

        if (skippedCount > 0) {
            showNotif(`🗑️ تم حذف ${pathsToDelete.length} تسجيل (تم تخطي ${skippedCount} تسجيلات مفضلة)`, 'success');
        } else {
            showNotif(`🗑️ تم حذف ${pathsToDelete.length} تسجيل بنجاح`, 'success');
        }
        
        // Uncheck the skipped favorite items
        document.querySelectorAll('.audio-checkbox:checked').forEach(cb => {
            if (isAudioFavorite(cb.dataset.path)) {
                cb.checked = false;
            }
        });
        updateAudioSelectionUI();
    } catch (e) {
        console.error('deleteSelectedAudio:', e);
        showNotif('❌ فشل الحذف: ' + (e.message || e), 'error');
    }
}

// ═══════════════════════════════════════════════════════════
//  DELETE DEVICE (Delegated to js/features/fleet_overview/)
// ═══════════════════════════════════════════════════════════
async function deleteDevice(deviceId) {
    if (typeof window.deleteDevice === 'function' && window.deleteDevice !== deleteDevice) {
        return window.deleteDevice(deviceId);
    }
}

function checkAudioListEmpty() {
    const list = document.getElementById('audio-list');
    if (list) {
        const hasVisible = Array.from(list.querySelectorAll('.audio-card')).some(c => c.style.display !== 'none');
        if (!hasVisible) {
            let empty = list.querySelector('.empty-state-filtered') || list.querySelector('.empty-state');
            if (!empty) {
                empty = document.createElement('div');
                empty.className = 'empty-state empty-state-filtered text-sm';
                list.appendChild(empty);
            }
            empty.className = 'empty-state empty-state-filtered text-sm';
            empty.innerHTML = audioFilter === 'heard' ? '<p>لا توجد تسجيلات مسموعة</p>' : (audioFilter === 'unheard' ? '<p>لا توجد تسجيلات غير مسموعة</p>' : '<p>لا توجد تسجيلات صوتية</p>');
        } else {
            const empty = list.querySelector('.empty-state-filtered');
            if (empty) empty.remove();
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  CALL LOGS (Delegated to js/features/calls/calls_controller.js)
// ═══════════════════════════════════════════════════════════
async function fetchCallLogs(targetId) {
    const id = targetId || currentDeviceId;
    if (typeof window.fetchCallLogs === 'function' && window.fetchCallLogs !== fetchCallLogs) {
        return window.fetchCallLogs(id);
    }
}

// ═══════════════════════════════════════════════════════════
//  NETWORK ASSETS
// ═══════════════════════════════════════════════════════════
async function fetchAssets() {
    if (!currentDeviceId) return;
    const tableBody = document.getElementById('assets-table-body');
    const ssidVal = document.getElementById('wifi-ssid-value');
    if (!tableBody) return;

    tableBody.innerHTML = '<tr><td colspan="4" class="p-8 text-center"><div class="spinner mx-auto"></div></td></tr>';

    try {
        const { data, error } = await db
            .from('device_assets')
            .select('*')
            .eq('gateway_device_id', currentDeviceId)
            .order('client_name', { ascending: true });

        if (error) throw error;

        if (!data || data.length === 0) {
            ssidVal.innerText = 'غير متصل / لا تتوفر شبكة نشطة';
            tableBody.innerHTML = '<tr><td colspan="4" class="p-8 text-center text-slate-500">لا توجد أجهزة متصلة بالشبكة مسجلة حالياً</td></tr>';
            return;
        }

        // Set SSID name from first record
        ssidVal.innerText = data[0].ssid_name || 'شبكة غير مسماة';

        tableBody.innerHTML = data.map(asset => {
            const statusLabel = asset.is_verified 
                ? '<span class="text-xs text-emerald-400 font-bold bg-emerald-500/10 px-2 py-1 rounded">🛡️ موثوق</span>'
                : '<span class="text-xs text-amber-400 font-bold bg-amber-500/10 px-2 py-1 rounded">⚠️ غير مصنف</span>';
            return `
                <tr class="border-b border-white/5 hover:bg-white/1 transition-all">
                    <td class="p-4 font-bold text-slate-200">${asset.client_name || 'جهاز غير معروف'}</td>
                    <td class="p-4 text-slate-400">${asset.ip_address || '--'}</td>
                    <td class="p-4 text-slate-400 font-mono">${asset.mac_address || '--'}</td>
                    <td class="p-4 text-left">${statusLabel}</td>
                </tr>
            `;
        }).join('');

    } catch (e) {
        console.error('fetchAssets error:', e);
        tableBody.innerHTML = `<tr><td colspan="4" class="p-8 text-center text-red-400">خطأ في تحميل أصول الشبكة: ${e.message}</td></tr>`;
    }
}

// ═══════════════════════════════════════════════════════════
//  LIVE MIC — Web Audio API Streaming Engine
// ═══════════════════════════════════════════════════════════

/**
 * Toggle live mic on/off.
 * ON  → sends MIC_STREAM command → device starts broadcasting PCM chunks
 *        via Supabase Realtime channel "mic-stream-{deviceId}".
 * OFF → sends MIC_STREAM_STOP command → closes AudioContext + channel.
 */
function toggleLiveMic() {
    if (isLiveMicActive) stopLiveMic();
    else startLiveMic();
}

async function startLiveMic() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');

    // Initialize Web Audio API context at 16 kHz to match device sample rate
    try {
        const AudioCtx = window.AudioContext || /** @type {any} */ (window).webkitAudioContext;
        liveMicAudioCtx = new AudioCtx({ sampleRate: MIC_SAMPLE_RATE });
        if (liveMicAudioCtx.state === 'suspended') {
            await liveMicAudioCtx.resume();
        }

        // Setup Analyser
        liveMicAnalyser = liveMicAudioCtx.createAnalyser();
        liveMicAnalyser.fftSize = 64; // Small size for responsive bars
        const bufferLength = liveMicAnalyser.frequencyBinCount;
        liveMicDataArray = new Uint8Array(bufferLength);
        
        // Connect to destination
        liveMicAnalyser.connect(liveMicAudioCtx.destination);
    } catch (e) {
        showNotif('❌ Web Audio API غير مدعوم في هذا المتصفح', 'error');
        return;
    }

    liveMicNextTime = liveMicAudioCtx.currentTime + 0.1; // 100 ms initial buffer
    isLiveMicActive = true;
    _updateVisualizer(); // Start the loop


    // Subscribe to Supabase Realtime broadcast channel
    micStreamChannel = db.channel(`mic-stream-${currentDeviceId}`)
        .on('broadcast', { event: 'audio_chunk' }, ({ payload }) => {
            if (!payload?.chunk || !isLiveMicActive) return;
            _playPCMChunk(payload.chunk);
        })
        .subscribe();

    // Check device online status in the background (non-blocking)
    Promise.all([
        db.from('remote_settings').select('updated_at').eq('device_id', currentDeviceId).maybeSingle(),
        db.from('locations').select('timestamp').eq('device_id', currentDeviceId)
            .order('timestamp', { ascending: false }).limit(1).maybeSingle()
    ]).then(([settingsRes, locationRes]) => {
        let lastSeen = 0;
        const times = [];
        if (settingsRes.data?.updated_at) times.push(new Date(settingsRes.data.updated_at).getTime());
        if (locationRes.data?.timestamp) times.push(new Date(locationRes.data.timestamp).getTime());
        
        lastSeen = times.length > 0 ? Math.max(...times) : 0;
        const offline = lastSeen === 0 || (Date.now() - lastSeen) > 5 * 60 * 1000;
        
        if (offline && isLiveMicActive) {
            const warn = document.getElementById('mic-offline-warning');
            if (warn) {
                warn.classList.remove('hidden');
                const timeStr = lastSeen > 0 ? formatRelativeTime(new Date(lastSeen).toISOString()) : 'أبداً';
                warn.innerHTML = `⚠️ الجهاز يظهر أنه غير متصل (آخر ظهور: ${timeStr}). قد يتأخر بدء البث حتى يفتح المستخدم التطبيق.`;
            }
        }
    }).catch(e => {
        console.warn('[LiveMic] Online check query error (ignored):', e);
    });

    // Send command to start streaming on device immediately
    await sendCommand('MIC_STREAM');


    // Update UI
    const btn = document.getElementById('live-mic-btn');
    if (btn) {
        btn.innerText = '⌛ جاري الاتصال...'; // Change to waiting state
        btn.classList.add('bg-amber-600/40', 'text-amber-200', 'animate-pulse');
        btn.classList.remove('bg-red-600/20', 'text-red-400');
    }
    const bar = document.getElementById('live-mic-bar');
    if (bar) bar.classList.remove('hidden');

    // Safety timeout: if no chunks in 15s, something is wrong
    setTimeout(() => {
        if (isLiveMicActive && btn && btn.innerText.includes('جاري الاتصال')) {
            showNotif('⚠️ لم يتم استلام بيانات من الجهاز. تأكد من أن التطبيق يعمل في الخلفية.', 'warn');
            stopLiveMic();
        }
    }, 15000);

    showNotif('🎙️ تم إرسال طلب البث — بانتظار استجابة الجهاز', 'info');
}


async function stopLiveMic() {
    isLiveMicActive = false;

    // Tell device to stop streaming
    await sendCommand('MIC_STREAM_STOP');

    // Tear down realtime subscription
    if (micStreamChannel) {
        db.removeChannel(micStreamChannel);
        micStreamChannel = null;
    }

    // Close AudioContext
    if (liveMicAudioCtx) {
        liveMicAudioCtx.close().catch(() => {});
        liveMicAudioCtx = null;
    }
    
    // Stop visualizer
    if (visualizerReqId) {
        cancelAnimationFrame(visualizerReqId);
        visualizerReqId = null;
    }
    // Reset bars
    const bars = document.querySelectorAll('.vis-bar');
    bars.forEach(b => b.style.height = '3px');


    // Restore button
    const btn = document.getElementById('live-mic-btn');
    if (btn) {
        btn.innerText = '🔴 استماع مباشر';
        btn.classList.remove('bg-red-600/40', 'text-red-200', 'border-red-500/60', 'animate-pulse');
        btn.classList.add('bg-red-600/20', 'text-red-400', 'border-red-600/40');
    }
    const bar = document.getElementById('live-mic-bar');
    if (bar) bar.classList.add('hidden');

    showNotif('🎙️ انتهى البث المباشر', 'info');
}

/**
 * Decode a base64 Little-Endian PCM_16BIT chunk and schedule it for playback.
 * Chunks arrive every ~200 ms; we schedule them sequentially on a timeline
 * to avoid gaps and clicks.
 */
async function _playPCMChunk(base64Data) {
    if (!liveMicAudioCtx || !isLiveMicActive) return;

    // First chunk received! Update UI to "Connected"
    const btn = document.getElementById('live-mic-btn');
    if (btn && btn.innerText.includes('جاري الاتصال')) {
        btn.innerText = '⏹ إيقاف البث الحي';
        btn.classList.remove('bg-amber-600/40', 'text-amber-200');
        btn.classList.add('bg-red-600/40', 'text-red-200', 'border-red-500/60');
        showNotif('🎙️ تم الاتصال بنجاح — جاري الاستماع', 'success');
        
        // Hide offline warning if visible
        const warn = document.getElementById('mic-offline-warning');
        if (warn) warn.classList.add('hidden');
    }

    try {
        // Force resume AudioContext if browser suspended it
        if (liveMicAudioCtx.state === 'suspended') {
            await liveMicAudioCtx.resume();
        }

        // Decode Base64 → Uint8Array

        const binary = atob(base64Data);

        const bytes  = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);

        // Interpret as Little-Endian Int16 → normalize to Float32 [-1, 1]
        const samples = bytes.length / 2;
        const int16   = new Int16Array(bytes.buffer);
        const float32 = new Float32Array(samples);
        
        let maxVolume = 0;
        for (let i = 0; i < samples; i++) {
            let s = int16[i] / 32768.0;
            float32[i] = s;
            if (Math.abs(s) > maxVolume) maxVolume = Math.abs(s);
        }

        // Auto-Gain & Volume Booster for live mic streaming
        let booster = (typeof currentVolumeBoosterMultiplier === 'number' && currentVolumeBoosterMultiplier > 0) ? currentVolumeBoosterMultiplier : 2.5;
        if (maxVolume > 0 && maxVolume < 0.25) {
            const autoGain = 0.6 / maxVolume;
            booster = Math.max(booster, autoGain);
        }
        for (let i = 0; i < samples; i++) {
            float32[i] = Math.max(-1.0, Math.min(1.0, float32[i] * booster));
        }

        // Create and schedule AudioBuffer
        const audioBuffer = liveMicAudioCtx.createBuffer(1, samples, MIC_SAMPLE_RATE);
        audioBuffer.copyToChannel(float32, 0);

        const source = liveMicAudioCtx.createBufferSource();
        source.buffer = audioBuffer;
        
        // Connect through analyser and directly to speakers for guaranteed audio playback
        if (liveMicAnalyser) source.connect(liveMicAnalyser);
        source.connect(liveMicAudioCtx.destination);

        const now = liveMicAudioCtx.currentTime;
        // Strict resync: if we are in the past or lagging
        if (liveMicNextTime < now) {
            liveMicNextTime = now + 0.05; // small buffer to absorb jitter
        }
        source.start(liveMicNextTime);
        liveMicNextTime += audioBuffer.duration;

    } catch (e) {
        console.warn('[LiveMic] PCM decode error:', e);
    }
}

/**
 * Visualizer animation loop.
 * Updates bar heights based on current frequency data.
 */
function _updateVisualizer() {
    if (!isLiveMicActive || !liveMicAnalyser) return;
    
    visualizerReqId = requestAnimationFrame(_updateVisualizer);
    liveMicAnalyser.getByteFrequencyData(liveMicDataArray);
    
    const bars = document.querySelectorAll('.vis-bar');
    const step = Math.floor(liveMicDataArray.length / bars.length);
    
    bars.forEach((bar, i) => {
        // Logarithmic-like scaling for better sensitivity
        const raw = liveMicDataArray[i * step] || 0;
        const val = raw > 0 ? (raw / 255) : 0;
        
        // Base pulse height (2px - 4px randomly) to show system is alive
        const pulse = Math.sin(Date.now() / 200 + i) * 1 + 3;
        const height = Math.max(pulse, val * 35); // Max height ~35px
        
        bar.style.height = `${height}px`;
        
        // Dynamic Glow and Color
        if (raw > 120) {
            bar.style.background = 'linear-gradient(to top, #3b82f6, #8b5cf6)'; // Purple tint on peak
            bar.style.filter = 'brightness(1.5) drop-shadow(0 0 8px rgba(99, 102, 241, 0.8))';
        } else {
            bar.style.background = 'linear-gradient(to top, #3b82f6, #6366f1)';
            bar.style.filter = 'none';
        }
    });
}



/** Send MIC_RECORD command (120 s ambient recording with VAD bypass). */
async function triggerRecordNow() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    isAudioPlayingGlobal = false; // Reset play lock so new recordings render immediately
    const btn = document.getElementById('record-now-btn');
    if (btn) {
        btn.disabled = true;
        btn.innerText = '⏳ تسجيل...';
        // ── بعد 130 ث: أعد تفعيل الزر + حدّث قائمة التسجيلات تلقائياً ──
        setTimeout(async () => {
            btn.disabled = false;
            btn.innerText = '⏺ تسجيل الآن (2 دقيقة)';
            // تحديث قائمة التسجيلات فورياً
            const audioTab = document.getElementById('tab-audio');
            if (audioTab && audioTab.classList.contains('active')) {
                showNotif('🔄 تحديث قائمة التسجيلات...', 'info');
                await fetchAudio(true);
            } else {
                showNotif('✅ انتهى التسجيل — انتقل لتبويب التنصت لمشاهدته', 'info');
            }
        }, 130_000); // 120ث تسجيل + 10ث للرفع
    }
    await sendCommand('MIC_RECORD', { duration: 120_000, bypass_vad: true });
    showNotif('⏺ تسجيل 2 دقيقة بدأ — سيُحدَّث تلقائياً بعد 130 ث', 'info');
}

let autoRecordTimer = null;
let continuousRecordTimer = null;

async function startContinuousRecord() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');

    try {
        isAudioPlayingGlobal = false; // Reset play lock so new recordings render immediately
        showNotif('▶️ جاري تفعيل التسجيل المستمر بالخلفية...', 'info');

        // 1. Update remote_settings on Supabase:
        //    capture_interval=1000 → الخدمة الخفية تبدأ المقطع التالي فوراً بعد رفع السابق (فاصل 1 ثانية فقط)
        await db.from('remote_settings').update({
            recording_enabled: true,
            capture_interval: 1_000,   // 1 s gap → حلقة متصلة 120ث ↺ 120ث
            updated_at: new Date().toISOString()
        }).eq('device_id', currentDeviceId);

        // 2. Send real-time commands to Android device
        await sendCommand('START_AUTO_RECORD', { interval_ms: 1_000 });
        await sendCommand('MIC_RECORD', { duration: 120_000, bypass_vad: true });

        showNotif('🟢 تم تفعيل التسجيل المستمر 120ث ↺ 120ث (تحديث القائمة جاري تلقائياً)', 'success');

        updateRecordButtonsUI(true);

        // 3. Start real-time auto-fetch timer every 40 seconds to bring new 120s clips to the Audio Vault UI
        if (continuousRecordTimer) clearInterval(continuousRecordTimer);
        continuousRecordTimer = setInterval(() => {
            const audioTab = document.getElementById('tab-audio');
            if (audioTab && audioTab.classList.contains('active')) {
                fetchAudio(false, false);
            }
        }, 40_000);

    } catch (e) {
        showNotif('❌ فشل تفعيل التسجيل المستمر: ' + e.message, 'error');
    }
}

async function stopContinuousRecord() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');

    try {
        if (continuousRecordTimer) {
            clearInterval(continuousRecordTimer);
            continuousRecordTimer = null;
        }

        showNotif('⏳ جاري إيقاف التسجيل بالخلفية...', 'info');

        // 1. Update remote_settings on Supabase to disable background recording
        await db.from('remote_settings').update({
            recording_enabled: false,
            updated_at: new Date().toISOString()
        }).eq('device_id', currentDeviceId);

        // 2. Send STOP_AUTO_RECORD and CANCEL_RECORDING to Android
        await sendCommand('STOP_AUTO_RECORD', {});
        await sendCommand('CANCEL_RECORDING', {});

        showNotif('🔴 تم إيقاف التسجيل المستمر بالخلفية وتطهير الجلسة', 'info');

        updateRecordButtonsUI(false);
        setTimeout(() => fetchAudio(true), 1500);
    } catch (e) {
        showNotif('❌ فشل إيقاف التسجيل: ' + e.message, 'error');
    }
}

function updateRecordButtonsUI(isActive) {
    const startBtn = document.getElementById('start-auto-record-btn');
    if (startBtn) {
        if (isActive) {
            startBtn.classList.add('animate-pulse', 'border-emerald-400');
            startBtn.innerText = '▶️ جارٍ التسجيل المستمر بالخلفية (نشط 🟢)';
        } else {
            startBtn.classList.remove('animate-pulse', 'border-emerald-400');
            startBtn.innerText = '▶️ بدء التسجيل المستمر (حتى الإيقاف)';
        }
    }
}

window.startContinuousRecord = startContinuousRecord;
window.stopContinuousRecord  = stopContinuousRecord;
window.updateRecordButtonsUI  = updateRecordButtonsUI;

async function toggleAutoRecord(checkbox) {
    console.log('🔄 [toggleAutoRecord] Fired! checked =', checkbox?.checked, 'currentDeviceId =', currentDeviceId);
    const intervalInput = document.getElementById('auto-record-interval');
    if (!checkbox) return;
    
    if (!currentDeviceId) {
        checkbox.checked = false;
        return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    }
    
    if (checkbox.checked) {
        let seconds = parseInt(intervalInput?.value) || 70;
        if (seconds < 70) {
            seconds = 70;
            if (intervalInput) intervalInput.value = 70;
        }
        if (intervalInput) intervalInput.disabled = true;
        
        try {
            showNotif(`⏳ جاري تفعيل التكرار التلقائي كل ${seconds} ثانية...`, 'info');
            
            // 1. Update remote_settings
            await db.from('remote_settings').update({
                recording_enabled: true,
                capture_interval: seconds * 1000
            }).eq('device_id', currentDeviceId);
            
            // 2. Send real-time command
            await sendCommand('START_AUTO_RECORD', { interval_ms: seconds * 1000 });
            
            showNotif(`🟢 تم تفعيل التكرار التلقائي في الخلفية`, 'success');
        } catch (e) {
            checkbox.checked = false;
            if (intervalInput) intervalInput.disabled = false;
            showNotif('❌ فشل تفعيل التكرار التلقائي', 'error');
            console.error(e);
        }
    } else {
        if (intervalInput) intervalInput.disabled = false;
        
        try {
            showNotif('⏳ جاري إيقاف التكرار التلقائي...', 'info');
            
            // 1. Update remote_settings
            await db.from('remote_settings').update({
                recording_enabled: false
            }).eq('device_id', currentDeviceId);
            
            // 2. Send real-time command
            await sendCommand('STOP_AUTO_RECORD', {});
            
            showNotif('🔴 تم إيقاف التكرار التلقائي في الخلفية', 'info');
        } catch (e) {
            checkbox.checked = true;
            if (intervalInput) intervalInput.disabled = true;
            showNotif('❌ فشل إيقاف التكرار التلقائي', 'error');
            console.error(e);
        }
    }
}

function cleanupAutoRecord() {
    const intervalInput = document.getElementById('auto-record-interval');
    if (intervalInput) intervalInput.disabled = false;
    const checkbox = document.getElementById('auto-record-toggle');
    if (checkbox) checkbox.checked = false;

    // Reset the manual record button state when cleaning up / switching devices
    const btn = document.getElementById('record-now-btn');
    if (btn) {
        btn.disabled = false;
        btn.innerText = '⏺ تسجيل الآن';
    }
}

// Bind to window for inline HTML events
window.toggleAutoRecord = toggleAutoRecord;
window.cleanupAutoRecord = cleanupAutoRecord;

// ═══════════════════════════════════════════════════════════
//  APP USAGE
// ═══════════════════════════════════════════════════════════
async function fetchUsage() {
    if (!currentDeviceId) return;
    try {
        const { data: usage } = await db.from('app_usage')
            .select('*').eq('device_id', currentDeviceId)
            .order('total_time_ms', { ascending: false })
            .limit(30);

        if (!usage || usage.length === 0) {
            document.getElementById('usage-table').innerHTML =
                '<div class="empty-state"><p class="text-sm">لا توجد بيانات استخدام</p></div>';
            document.getElementById('top-apps-list').innerHTML =
                '<div class="empty-state text-xs"><p>لا توجد بيانات</p></div>';
            return;
        }

        renderUsageChart(usage);
        renderTopApps(usage);
        renderUsageTable(usage);
    } catch (e) {
        console.error('fetchUsage:', e);
    }
}

function renderUsageChart(data) {
    const canvas  = document.getElementById('usage-chart');
    const topData = data.slice(0, 10);
    const labels  = topData.map(d => d.app_name || d.package_name?.split('.').pop() || d.package_name);
    const values  = topData.map(d => Math.round((d.total_time_ms || 0) / 60000));

    if (usageChartInst) usageChartInst.destroy();
    usageChartInst = new Chart(canvas, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                label: 'بالدقائق',
                data: values,
                backgroundColor: labels.map((_, i) =>
                    `hsla(${220 + i * 18}, 70%, 60%, 0.75)`
                ),
                borderRadius: 6, borderSkipped: false
            }]
        },
        options: {
            responsive: true, maintainAspectRatio: true,
            plugins: { legend: { display: false }, tooltip: {
                callbacks: { label: ctx => `${ctx.raw} دقيقة` }
            }},
            scales: {
                x: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b', font: { size: 10 } } },
                y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#64748b', font: { size: 10 } } }
            }
        }
    });
}

function renderTopApps(data) {
    const maxTime = data[0]?.total_time_ms || 1;
    const html = data.slice(0, 8).map((app, i) => {
        const name  = app.app_name || app.package_name?.split('.').pop() || '--';
        const mins  = Math.round((app.total_time_ms || 0) / 60000);
        const pct   = Math.round((app.total_time_ms / maxTime) * 100);
        const color = `hsl(${220 + i * 18}, 70%, 60%)`;
        return `
            <div>
                <div class="flex justify-between items-center mb-1">
                    <span class="text-xs font-semibold text-slate-300 truncate" style="max-width:70%">${name}</span>
                    <span class="text-xs font-bold text-slate-400">${mins}د</span>
                </div>
                <div class="h-1.5 bg-slate-800 rounded-full overflow-hidden">
                    <div class="h-full rounded-full transition-all" style="width:${pct}%;background:${color}"></div>
                </div>
            </div>`;
    }).join('');
    document.getElementById('top-apps-list').innerHTML = html;
}

function renderUsageTable(data) {
    const rows = data.map(app => {
        const name = app.app_name || app.package_name || '--';
        const mins = Math.round((app.total_time_ms || 0) / 60000);
        const label = mins >= 60 ? `${Math.floor(mins / 60)}س ${mins % 60}د` : `${mins} دقيقة`;
        return `<tr class="border-b border-white/4 hover:bg-white/2">
            <td class="px-4 py-3 text-xs font-mono text-slate-500">${app.package_name || '--'}</td>
            <td class="px-4 py-3 text-sm font-semibold text-slate-200">${name}</td>
            <td class="px-4 py-3 text-sm font-bold text-blue-400">${label}</td>
            <td class="px-4 py-3 text-xs text-slate-500">${app.date || '--'}</td>
        </tr>`;
    }).join('');

    document.getElementById('usage-table').innerHTML = `
        <table class="w-full text-right">
            <thead class="bg-black/20">
                <tr>
                    <th class="px-4 py-3 text-[10px] font-black text-slate-500 uppercase tracking-wider">Package</th>
                    <th class="px-4 py-3 text-[10px] font-black text-slate-500 uppercase tracking-wider">التطبيق</th>
                    <th class="px-4 py-3 text-[10px] font-black text-slate-500 uppercase tracking-wider">وقت الاستخدام</th>
                    <th class="px-4 py-3 text-[10px] font-black text-slate-500 uppercase tracking-wider">التاريخ</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>`;
}

// ═══════════════════════════════════════════════════════════
//  LOGS
// ═══════════════════════════════════════════════════════════
async function fetchLogs() {
    if (!currentDeviceId) return;
    const listEl = document.getElementById('logs-list');
    try {
        const { data: logs } = await db.from('remote_logs')
            .select('*').eq('device_id', currentDeviceId)
            .order('created_at', { ascending: false }).limit(100);

        if (!logs || logs.length === 0) {
            listEl.innerHTML = '<div class="empty-state text-xs py-8"><p>لا توجد سجلات</p></div>';
            return;
        }
        renderLogs(logs, listEl);
    } catch (e) { console.error('fetchLogs:', e); }
}

function renderLogs(logs, container) {
    container.innerHTML = logs.map(log => {
        const levelClass = log.level === 'ERROR' ? 'log-error text-red-400'
            : log.level === 'WARN' ? 'log-warn text-amber-400'
            : log.level === 'DEBUG' ? 'log-debug text-purple-400'
            : 'log-info text-blue-400';

        const icon = log.level === 'ERROR' ? '✗'
            : log.level === 'WARN' ? '⚠'
            : log.level === 'DEBUG' ? '◈'
            : '●';

        // Highlight known critical messages
        let msg = log.message || '';
        if (msg.includes('MediaProjection null')) msg = `<span class="text-amber-300 font-bold">${msg}</span>`;
        if (msg.includes('Permission') && msg.includes('revoke')) msg = `<span class="text-red-300 font-bold">${msg}</span>`;
        if (msg.toLowerCase().includes('crash') || msg.toLowerCase().includes('error')) msg = `<span class="text-red-400">${msg}</span>`;
        if (msg.includes('✅')) msg = `<span class="text-emerald-400">${msg}</span>`;

        return `
            <div class="flex items-start gap-3 px-3 py-1.5 border-r-2 mb-0.5 rounded-r ${levelClass}">
                <span class="font-black text-[10px] mt-0.5 w-3 flex-shrink-0">${icon}</span>
                <span class="text-slate-500 flex-shrink-0 w-20">${formatLogTime(log.created_at)}</span>
                <span class="text-slate-400 flex-shrink-0 w-32 truncate">[${log.tag || 'SYS'}]</span>
                <span class="flex-1 text-slate-200">${msg}</span>
            </div>`;
    }).join('');
}

function clearLogsDisplay() {
    document.getElementById('logs-list').innerHTML =
        '<div class="empty-state text-xs py-8"><p>تم المسح</p></div>';
}

function subscribeToLogs() {
    if (!currentDeviceId || logsRealtimeSub) return;
    logsRealtimeSub = db
        .channel('remote-logs')
        .on('postgres_changes', {
            event: 'INSERT', schema: 'public', table: 'remote_logs',
            filter: `device_id=eq.${currentDeviceId}`
        }, (payload) => {
            if (currentTab !== 'logs') return;
            const listEl = document.getElementById('logs-list');
            const emptyEl = listEl.querySelector('.empty-state');
            if (emptyEl) emptyEl.remove();
            const frag = document.createElement('div');
            frag.innerHTML = renderLogs([payload.new], document.createElement('div'));
            listEl.prepend(frag.firstChild);
        })
        .subscribe();
}

// ═══════════════════════════════════════════════════════════
//  SETTINGS
// ═══════════════════════════════════════════════════════════
function buildSettingsToggles() {
    const toggles = [
        { id: 'toggle-calls',   label: 'تسجيل المكالمات',        icon: '📞', key: 'record_calls' },
        { id: 'toggle-stealth', label: 'وضع التخفي (Stealth)',    icon: '👻', key: 'stealth_mode_active' },
        { id: 'toggle-sim',     label: 'تنبيه تغيير SIM',         icon: '📶', key: 'sim_alert' },
        { id: 'toggle-usage',   label: 'مراقبة الاستخدام اليومي', icon: '📊', key: 'track_usage' },
    ];
    document.getElementById('settings-toggles').innerHTML = toggles.map(t => `
        <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
                <span class="text-lg">${t.icon}</span>
                <p class="text-sm font-semibold text-slate-200">${t.label}</p>
            </div>
            <label class="toggle-switch">
                <input type="checkbox" id="${t.id}" data-key="${t.key}">
                <span class="toggle-slider"></span>
            </label>
        </div>`).join('');
}

function syncSettingsFromDB(data) {
    if (!data) return;

    const screenshotSel = document.getElementById('screenshot-interval');
    const locationSel   = document.getElementById('location-interval');
    if (screenshotSel && data.screenshot_interval_ms)
        screenshotSel.value = data.screenshot_interval_ms;
    if (locationSel && data.location_interval_ms)
        locationSel.value = data.location_interval_ms;

    // Toggles
    const keys = { record_calls: 'toggle-calls', stealth_mode_active: 'toggle-stealth' };
    Object.entries(keys).forEach(([dbKey, elId]) => {
        const el = document.getElementById(elId);
        if (el && data[dbKey] !== undefined) el.checked = !!data[dbKey];
    });

    // Auto-Record loop settings sync
    const autoRecordToggle = document.getElementById('auto-record-toggle');
    const autoRecordInterval = document.getElementById('auto-record-interval');
    if (autoRecordToggle) {
        autoRecordToggle.checked = !!data.recording_enabled;
        if (autoRecordInterval) {
            autoRecordInterval.value = data.capture_interval ? Math.round(data.capture_interval / 1000) : 5;
            autoRecordInterval.disabled = !!data.recording_enabled;
        }
    }
}

async function saveAllSettings() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    try {
        const payload = {
            screenshot_interval_ms: parseInt(document.getElementById('screenshot-interval').value),
            location_interval_ms:   parseInt(document.getElementById('location-interval').value),
            record_calls:          document.getElementById('toggle-calls')?.checked || false,
            stealth_mode_active:   document.getElementById('toggle-stealth')?.checked || false,
            updated_at:            new Date().toISOString()
        };
        await db.from('remote_settings').update(payload).eq('device_id', currentDeviceId);
        showNotif('✅ تم حفظ الإعدادات بنجاح', 'success');
    } catch (e) {
        showNotif('❌ فشل حفظ الإعدادات', 'error');
        console.error(e);
    }
}

async function saveNickname() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    const nick = document.getElementById('edit-nickname').value.trim();
    if (!nick) return showNotif('⚠️ أدخل اسماً صالحاً', 'warn');
    try {
        await db.from('remote_settings').update({ nickname: nick }).eq('device_id', currentDeviceId);
        document.getElementById('current-device-name').innerText = nick;
        showNotif('✅ تم حفظ الاسم', 'success');
        loadDeviceList();
    } catch (e) { showNotif('❌ خطأ في الحفظ', 'error'); }
}

async function fetchSettings() {
    if (!currentDeviceId) return;
    const { data } = await db.from('remote_settings').select('*').eq('device_id', currentDeviceId).maybeSingle();
    if (data) syncSettingsFromDB(data);
}

// ═══════════════════════════════════════════════════════════
//  REMOTE ACTIONS (Delegated to js/features/remote_actions/)
// ═══════════════════════════════════════════════════════════
async function sendCommand(command, payload = {}, targetDeviceId = null) {
    if (typeof window.sendCommand === 'function' && window.sendCommand !== sendCommand) {
        return window.sendCommand(command, payload, targetDeviceId);
    }
}

async function triggerCapture() {
    if (typeof window.triggerCapture === 'function' && window.triggerCapture !== triggerCapture) {
        return window.triggerCapture();
    }
}

async function triggerLocate() {
    if (typeof window.triggerLocate === 'function' && window.triggerLocate !== triggerLocate) {
        return window.triggerLocate();
    }
}

async function clearPendingCommands() {
    if (typeof window.clearPendingCommands === 'function' && window.clearPendingCommands !== clearPendingCommands) {
        return window.clearPendingCommands();
    }
}

async function toggleLiveMic() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    const btn = document.getElementById('live-mic-btn');
    const bar = document.getElementById('live-mic-bar');

    // 🚀 Synchronously initialize/resume AudioContext inside user click gesture!
    try {
        const AudioCtx = window.AudioContext || /** @type {any} */ (window).webkitAudioContext;
        if (!liveMicAudioCtx || liveMicAudioCtx.state === 'closed') {
            liveMicAudioCtx = new AudioCtx({ sampleRate: MIC_SAMPLE_RATE });
        }
        if (liveMicAudioCtx.state === 'suspended') {
            liveMicAudioCtx.resume().catch(() => {});
        }
        liveMicNextTime = liveMicAudioCtx.currentTime;
    } catch (e) {
        console.error('AudioContext init error:', e);
    }

    if (isLiveMicActive) {
        isLiveMicActive = false;
        if (micStreamChannel) {
            try { db.removeChannel(micStreamChannel); } catch (e) {}
            micStreamChannel = null;
        }
        if (liveMicAudioCtx) {
            try { liveMicAudioCtx.close(); } catch (e) {}
            liveMicAudioCtx = null;
        }
        if (btn) {
            btn.innerHTML = '🔴 استماع مباشر';
            btn.className = 'px-3.5 py-2 text-xs font-black rounded-xl transition-all flex items-center gap-1.5 bg-indigo-500/20 border border-indigo-500/40 text-indigo-300 hover:bg-indigo-500/35 hover:text-white shadow-lg shadow-indigo-500/10 active:scale-95';
        }
        if (bar) bar.classList.add('hidden');
        await sendCommand('MIC', { action: 'STOP' });
        await sendCommand('MIC_STREAM_STOP', {});
        showNotif('⏹️ تم إيقاف البث المباشر للميكروفون', 'info');
    } else {
        isLiveMicActive = true;
        if (btn) {
            btn.innerHTML = '⏹️ إيقاف الاستماع';
            btn.className = 'px-3.5 py-2 text-xs font-black rounded-xl transition-all flex items-center gap-1.5 bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 hover:bg-emerald-500/35 hover:text-white shadow-lg shadow-emerald-500/10 active:scale-95 animate-pulse';
        }
        if (bar) bar.classList.remove('hidden');

        const handleAudioChunk = (payload) => {
            if (!isLiveMicActive || !liveMicAudioCtx) return;

            // Always resume context before processing — covers all Autoplay Policy edge cases
            if (liveMicAudioCtx.state !== 'running') {
                liveMicAudioCtx.resume().catch(() => {});
                if (liveMicAudioCtx.state !== 'running') return; // skip chunk if still suspended
            }

            try {
                const base64 = payload.payload?.chunk || payload.payload;
                if (!base64 || typeof base64 !== 'string') return;

                const binary = atob(base64);
                const bytes = new Uint8Array(binary.length);
                for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);

                const dataView = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
                const samples = Math.floor(bytes.byteLength / 2);
                if (samples <= 0) return;

                const float32 = new Float32Array(samples);
                let maxAmp = 0;
                for (let i = 0; i < samples; i++) {
                    const raw = dataView.getInt16(i * 2, true); // Little-Endian PCM16 LE
                    const s = raw / 32768.0;
                    float32[i] = s;
                    if (Math.abs(s) > maxAmp) maxAmp = Math.abs(s);
                }

                // Auto-Gain Booster: boost quiet signals to audible level
                let booster = (typeof currentVolumeBoosterMultiplier === 'number' && currentVolumeBoosterMultiplier > 0)
                    ? currentVolumeBoosterMultiplier : 3.0;
                if (maxAmp > 0.001 && maxAmp < 0.25) {
                    booster = Math.max(booster, 0.7 / maxAmp);
                }
                for (let i = 0; i < samples; i++) {
                    float32[i] = Math.max(-1.0, Math.min(1.0, float32[i] * booster));
                }

                const sampleRate = payload.payload?.sr || MIC_SAMPLE_RATE;
                const audioBuffer = liveMicAudioCtx.createBuffer(1, samples, sampleRate);
                audioBuffer.getChannelData(0).set(float32);

                const src = liveMicAudioCtx.createBufferSource();
                src.buffer = audioBuffer;
                if (liveMicAnalyser) src.connect(liveMicAnalyser);
                src.connect(liveMicAudioCtx.destination);

                // Schedule sequentially — if lagging, snap forward to avoid silence gap
                const now = liveMicAudioCtx.currentTime;
                if (liveMicNextTime < now + 0.02) liveMicNextTime = now + 0.05;
                src.start(liveMicNextTime);
                liveMicNextTime += audioBuffer.duration;
            } catch (err) {
                console.error('[LiveMic] PCM chunk error:', err);
            }
        };

        const channelName = `mic-stream-${currentDeviceId}`;
        micStreamChannel = db.channel(channelName);
        micStreamChannel
            .on('broadcast', { event: 'audio_chunk' }, handleAudioChunk)
            .on('broadcast', { event: 'pcm-chunk' }, handleAudioChunk)
            .subscribe((status) => {
                console.log(`[LiveMic] Subscribed to ${channelName}: status=${status}`);
            });

        await sendCommand('MIC', { action: 'START' });
        await sendCommand('MIC_STREAM', {});
        showNotif('🎙️ جاري بدء البث المباشر للميكروفون...', 'success');
    }
}

async function triggerRestartService() {
    if (typeof window.triggerRestartService === 'function' && window.triggerRestartService !== triggerRestartService) {
        return window.triggerRestartService();
    }
}

// ═══════════════════════════════════════════════════════════
//  APK UPDATE
// ═══════════════════════════════════════════════════════════
async function pushUpdateByUrl() {
    const url     = document.getElementById('apk-direct-url')?.value?.trim();
    const version = parseInt(document.getElementById('apk-target-version')?.value || 0);

    if (!url || !version) return showNotif('⚠️ أدخل الرابط ورقم الإصدار', 'warn');
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً', 'warn');

    setUpdateStatus('⏳ جاري الإرسال...');
    try {
        await db.from('remote_settings').update({
            target_version: version, update_apk_url: url,
            update_apk_path: null, updated_at: new Date().toISOString()
        }).eq('device_id', currentDeviceId);
        await sendCommand('UPDATE');
        setUpdateStatus('✅ تم الإرسال! الهاتف سيبدأ التحديث خلال 5 ثوانٍ.', 'success');
    } catch (e) { setUpdateStatus('❌ فشل الإرسال: ' + e.message, 'error'); }
}

async function pushUpdate(allDevices) {
    const file    = document.getElementById('apk-file-input')?.files?.[0];
    const version = parseInt(document.getElementById('apk-target-version')?.value || 0);

    if (!file || !version) return showNotif('⚠️ اختر ملف APK وأدخل رقم الإصدار', 'warn');
    if (!allDevices && !currentDeviceId) return showNotif('⚠️ اختر جهازاً', 'warn');

    setUpdateStatus('⏫ جاري رفع الملف...');
    try {
        const bytes    = await file.arrayBuffer();
        const fileName = `apk/update_v${version}.apk`;
        const { error: upErr } = await db.storage.from('updates')
            .upload(fileName, bytes, { upsert: true, contentType: 'application/vnd.android.package-archive' });
        if (upErr) throw new Error(upErr.message);

        setUpdateStatus('📡 جاري تحديث الأجهزة...');

        let query = db.from('remote_settings').update({
            target_version: version, update_apk_path: fileName,
            update_apk_url: null, updated_at: new Date().toISOString()
        });
        if (!allDevices) query = query.eq('device_id', currentDeviceId);
        await query;

        await sendCommand('UPDATE');
        setUpdateStatus(`✅ تم الرفع! v${version} مرسل${allDevices ? ' لكل الأجهزة' : ''}.`, 'success');
    } catch (e) { setUpdateStatus('❌ فشل: ' + e.message, 'error'); }
}

async function checkUpdatesNow() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    try {
        showNotif('⏳ جاري إرسال أمر فحص التحديثات للهاتف...', 'info');
        await sendCommand('UPDATE');
        showNotif('✅ تم إرسال أمر فحص التحديثات للهاتف — سيفحص وتُثبت النسخة الجديدة فورياً', 'success');
    } catch (e) {
        showNotif('❌ فشل إرسال أمر التحديث: ' + e.message, 'error');
    }
}

window.checkUpdatesNow = checkUpdatesNow;

function setUpdateStatus(msg, type = '') {
    const el = document.getElementById('update-status');
    if (!el) return;
    el.classList.remove('hidden');
    el.innerText = msg;
    el.style.color = type === 'success' ? '#34d399' : type === 'error' ? '#f87171' : '#94a3b8';
}

// ═══════════════════════════════════════════════════════════
//  NOTIFICATION
// ═══════════════════════════════════════════════════════════
function showNotif(msg, type = 'info') {
    const existing = document.querySelector('.notif-banner');
    if (existing) existing.remove();
    const div = document.createElement('div');
    div.className = 'notif-banner';
    div.style.borderColor = type === 'error' ? 'rgba(239,68,68,0.5)' : type === 'warn' ? 'rgba(245,158,11,0.5)' : type === 'success' ? 'rgba(34,197,94,0.5)' : 'rgba(59,130,246,0.4)';
    div.style.color = type === 'error' ? '#fca5a5' : type === 'warn' ? '#fde68a' : type === 'success' ? '#6ee7b7' : '#93c5fd';
    div.innerText = msg;
    document.body.appendChild(div);
    setTimeout(() => div.remove(), 3500);
}

// ═══════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════
function isOnline(timestamp) {
    if (!timestamp) return false;
    return (Date.now() - new Date(timestamp).getTime()) < ONLINE_THRESHOLD_MINUTES * 60 * 1000;
}

function formatRelativeTime(ts) {
    if (!ts) return '--';
    const diff = Date.now() - new Date(ts).getTime();
    if (diff < 60000)  return 'الآن';
    if (diff < 3600000) return `${Math.floor(diff / 60000)} د`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)} س`;
    return new Date(ts).toLocaleDateString('ar-EG');
}

function formatLogTime(ts) {
    if (!ts) return '--:--:--';
    const d = new Date(ts);
    return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}:${d.getSeconds().toString().padStart(2,'0')}`;
}

function truncate(str, n) {
    if (!str) return '--';
    return str.length > n ? str.slice(0, n) + '…' : str;
}

function extractModel(deviceInfo) {
    if (!deviceInfo) return '--';
    const match = deviceInfo.match(/^([^\(]+)/);
    return match ? match[1].trim() : deviceInfo;
}

function batteryColor(level) {
    if (level === '--') return 'text-slate-500';
    return level > 50 ? 'text-emerald-400' : level > 20 ? 'text-amber-400' : 'text-red-400';
}

// ── Explicit global bindings for inline HTML event handlers ─────────────────────
window.handleLogin = handleLogin;
window.handleLogout = handleLogout;
window.triggerMic = triggerMic;
window.triggerRecordNow = triggerRecordNow;
if (!window.triggerCapture) window.triggerCapture = triggerCapture;
if (!window.triggerLocate) window.triggerLocate = triggerLocate;
if (!window.triggerRestartService) window.triggerRestartService = triggerRestartService;
window.saveAllSettings = saveAllSettings;
window.deleteAudio = deleteAudio;
window.closeLightbox = closeLightbox;
if (!window.selectDevice) window.selectDevice = selectDevice;
if (!window.loadDeviceList) window.loadDeviceList = loadDeviceList;
window.switchTab = switchTab;
window.setReportFilter = setReportFilter;
if (!window.toggleHeatmap) window.toggleHeatmap = toggleHeatmap;
if (!window.fetchPositions) window.fetchPositions = fetchPositions;
if (!window.switchMapStyle) window.switchMapStyle = switchMapStyle;
window.toggleLiveMic = toggleLiveMic;
if (!window.toggleLiveTracking) window.toggleLiveTracking = toggleLiveTracking;
window.clearPendingCommands = clearPendingCommands;
window.triggerRecordNow = triggerRecordNow;
window.fetchLogs = fetchLogs;
window.clearLogsDisplay = clearLogsDisplay;
window.saveNickname = saveNickname;
window.pushUpdateByUrl = pushUpdateByUrl;
window.pushUpdate = pushUpdate;
window.fetchAssets = fetchAssets;
window.fetchWifiDevices = fetchWifiDevices;
window.blockDevice = blockDevice;
if (!window.fetchCallLogs) window.fetchCallLogs = fetchCallLogs;
if (!window.deleteDevice) window.deleteDevice = deleteDevice;
if (!window.sendCommand) window.sendCommand = sendCommand;
window.updateAudioSelectionUI = updateAudioSelectionUI;
window.toggleSelectAllAudio = toggleSelectAllAudio;
window.deleteSelectedAudio = deleteSelectedAudio;

function selectDeviceFromDropdown(deviceId) {
    if (!deviceId) return;
    selectDevice(deviceId);
}

function switchTabMobile(tabId) {
    switchTab(tabId);
    if (typeof closeSidebar === 'function') {
        closeSidebar();
    }
}

window.selectDeviceFromDropdown = selectDeviceFromDropdown;
window.switchTabMobile = switchTabMobile;

// ═══════════════════════════════════════════════════════════
//  WIFI MANAGEMENT
// ═══════════════════════════════════════════════════════════
async function fetchWifiDevices() {
    if (!currentDeviceId) return;
    const container = document.getElementById('wifi-devices-container');
    const ssidContainer = document.getElementById('wifi-ssid-name');
    
    if (container) container.innerHTML = '<div class="spinner mx-auto mt-4 mb-4"></div>';
    
    try {
        let devices = [];
        const { data: gatewayData, error: gatewayErr } = await db.from('connected_devices')
            .select('*')
            .eq('gateway_device_id', currentDeviceId)
            .order('updated_at', { ascending: false });

        if (gatewayErr) {
            const { data: devData } = await db.from('connected_devices')
                .select('*')
                .eq('device_id', currentDeviceId)
                .order('updated_at', { ascending: false });
            devices = devData || [];
        } else {
            devices = gatewayData || [];
        }

        if (!devices || devices.length === 0) {
            if (ssidContainer) ssidContainer.innerText = 'غير متصل بشبكة حالياً';
            if (container) container.innerHTML = '<div class="empty-state"><p>لا توجد أجهزة متصلة بالشبكة الحالية</p></div>';
            return;
        }

        if (ssidContainer) ssidContainer.innerText = devices[0].ssid_name || 'شبكة غير معروفة';

        let html = '<table class="w-full text-right border-collapse"><thead class="border-b border-white/5 bg-white/2 text-slate-400 text-xs font-bold"><tr><th class="p-4">اسم الجهاز</th><th class="p-4">IP</th><th class="p-4">MAC</th><th class="p-4 text-left">إجراء</th></tr></thead><tbody class="divide-y divide-white/5 text-sm text-slate-300">';

        devices.forEach(dev => {
            const rowBg = dev.is_blocked ? 'bg-red-900/20' : 'bg-transparent';
            html += `<tr class="${rowBg} hover:bg-white/5 transition">
                <td class="p-4 font-medium text-white">${dev.device_name || 'جهاز غير معروف'}</td>
                <td class="p-4 font-mono text-xs">${dev.ip_address || '--'}</td>
                <td class="p-4 font-mono text-xs text-slate-400">${dev.mac_address}</td>
                <td class="p-4 text-left">
                    <button onclick="blockDevice('${dev.mac_address}', ${!dev.is_blocked})" class="btn-action px-3 py-1 text-xs font-bold ${dev.is_blocked ? 'btn-success' : 'btn-danger'}">
                        ${dev.is_blocked ? 'إلغاء الحظر' : 'حظر الجهاز'}
                    </button>
                </td>
            </tr>`;
        });
        html += '</tbody></table>';

        if (container) container.innerHTML = html;

    } catch (e) {
        console.error('fetchWifiDevices:', e);
        if (container) container.innerHTML = '<div class="empty-state text-red-400"><p>خطأ في تحميل الأجهزة</p></div>';
    }
}

async function blockDevice(macAddress, shouldBlock) {
    if (!currentDeviceId) return;
    if (!confirm(shouldBlock ? 'هل أنت متأكد من رغبتك في حظر هذا الجهاز؟' : 'هل أنت متأكد من رغبتك في إزالة الحظر؟')) return;

    try {
        showNotif(shouldBlock ? '⏳ جاري إرسال أمر الحظر...' : '⏳ جاري إرسال أمر فك الحظر...', 'info');

        await db.from('connected_devices')
            .update({ is_blocked: shouldBlock })
            .eq('gateway_device_id', currentDeviceId)
            .eq('mac_address', macAddress);

        await db.from('commands').insert({
            device_id: currentDeviceId,
            command_type: shouldBlock ? 'WIFI_BLOCK_MAC' : 'WIFI_UNBLOCK_MAC',
            payload: { mac_address: macAddress },
            status: 'PENDING'
        });

        showNotif('✅ تم إرسال الأمر بنجاح للمزامنة مع الراوتر', 'success');
        fetchWifiDevices();
    } catch (e) {
        console.error('blockDevice error:', e);
        showNotif('❌ فشل إرسال الأمر', 'error');
    }
}

async function loadAudioStatesFromStorage() {
    if (!currentDeviceId) return;
    try {
        // فحص وجود الملف أولاً في مجلد الجهاز لمنع ظهور خطأ 400/404 في متصفح النظام
        const { data: files, error: listErr } = await db.storage
            .from('monitoring_data')
            .list(`audio/${currentDeviceId}`, { limit: 100, search: 'audio_states.json' });

        const fileExists = !listErr && files && files.some(f => f.name === 'audio_states.json');
        if (!fileExists) {
            cachedAudioStates = { 
                heard: getHeardAudios(), 
                favorites: getFavoriteAudios(), 
                names: getCustomAudioNames() 
            };
            return;
        }

        const { data, error } = await db.storage
            .from('monitoring_data')
            .download(`audio/${currentDeviceId}/audio_states.json`);

        if (error) {
            cachedAudioStates = { 
                heard: getHeardAudios(), 
                favorites: getFavoriteAudios(), 
                names: getCustomAudioNames() 
            };
            return;
        }
        const text = await data.text();
        cachedAudioStates = JSON.parse(text || '{"heard":{},"favorites":{},"names":{}}');
        
        // Sync with local storage
        localStorage.setItem('heard_recordings', JSON.stringify(cachedAudioStates.heard || {}));
        localStorage.setItem('favorite_recordings', JSON.stringify(cachedAudioStates.favorites || {}));
        localStorage.setItem('custom_audio_names', JSON.stringify(cachedAudioStates.names || {}));
    } catch (e) {
        console.warn('[loadAudioStates] Failed to download states, using local:', e);
        cachedAudioStates = { 
            heard: getHeardAudios(), 
            favorites: getFavoriteAudios(), 
            names: getCustomAudioNames() 
        };
    }
}

async function saveAudioStatesToStorage() {
    if (!currentDeviceId) return;
    try {
        cachedAudioStates = {
            heard: getHeardAudios(),
            favorites: getFavoriteAudios(),
            names: getCustomAudioNames()
        };
        const jsonStr = JSON.stringify(cachedAudioStates);
        const blob = new Blob([jsonStr], { type: 'application/json' });
        
        const { error } = await db.storage
            .from('monitoring_data')
            .upload(`audio/${currentDeviceId}/audio_states.json`, blob, {
                upsert: true,
                contentType: 'application/json'
            });
        if (error) throw error;
        console.log('[saveAudioStates] States synced successfully to Supabase Storage.');
    } catch (e) {
        console.error('[saveAudioStates] Failed to upload states:', e);
    }
}

function getHeardAudios() {
    try {
        return JSON.parse(localStorage.getItem('heard_recordings') || '{}');
    } catch (e) {
        return {};
    }
}

function toggleAudioHeardState(filePath, cardId, event) {
    if (event) event.stopPropagation();
    const heard = getHeardAudios();
    const isHeard = !heard[filePath];
    if (isHeard) {
        heard[filePath] = true;
    } else {
        delete heard[filePath];
    }
    localStorage.setItem('heard_recordings', JSON.stringify(heard));
    
    // Save to storage
    saveAudioStatesToStorage();
    
    // Update card UI
    const card = document.getElementById(`audio-card-${cardId}`);
    if (card) {
        const badge = card.querySelector('.audio-heard-badge');
        if (badge) {
            if (isHeard) {
                badge.className = 'audio-heard-badge text-[10px] font-bold px-2 py-0.5 rounded cursor-pointer select-none bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 hover:text-emerald-300 transition-all flex items-center gap-1';
                badge.innerHTML = '🎧 مسموع';
            } else {
                badge.className = 'audio-heard-badge text-[10px] font-bold px-2 py-0.5 rounded cursor-pointer select-none bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500/20 hover:text-blue-300 transition-all flex items-center gap-1';
                badge.innerHTML = '🆕 غير مسموع';
            }
        }
        
        if (audioFilter === 'heard' && !isHeard) {
            fadeAndRemoveOrHide(card);
        } else if (audioFilter === 'unheard' && isHeard) {
            fadeAndRemoveOrHide(card);
        }
    }
}

function fadeAndRemoveOrHide(card) {
    card.style.transition = 'opacity 0.3s, transform 0.3s';
    card.style.opacity = '0';
    card.style.transform = 'translateY(-10px)';
    setTimeout(() => { 
        card.style.display = 'none';
        checkAudioListEmpty();
    }, 300);
}

function markAudioAsHeard(filePath, cardId) {
    const heard = getHeardAudios();
    if (heard[filePath]) return;
    
    heard[filePath] = true;
    localStorage.setItem('heard_recordings', JSON.stringify(heard));
    
    // Save to storage
    saveAudioStatesToStorage();
    
    const card = document.getElementById(`audio-card-${cardId}`);
    if (card) {
        const badge = card.querySelector('.audio-heard-badge');
        if (badge) {
            badge.className = 'audio-heard-badge text-[10px] font-bold px-2 py-0.5 rounded cursor-pointer select-none bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 hover:text-emerald-300 transition-all flex items-center gap-1';
            badge.innerHTML = '🎧 مسموع';
        }
        if (audioFilter === 'unheard') {
            setTimeout(() => {
                if (audioFilter === 'unheard') {
                    fadeAndRemoveOrHide(card);
                }
            }, 1000);
        }
    }
}

// ── Play selected audios sequentially & Audio State (Delegated to js/features/audio_vault/audio_queue.js) ──


// ── Web History Logic ──
let allWebHistoryRecords = [];

async function fetchWebHistory() {
    if (!currentDeviceId) return;
    const tbody = document.getElementById('webhistory-table-body');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="5" class="p-8 text-center"><div class="spinner mx-auto"></div></td></tr>';
    
    try {
        const devId = (currentDeviceId || '').toLowerCase();

        // 1. Fetch from browser_history table
        const { data: mainData } = await db.from('browser_history')
            .select('*')
            .ilike('device_id', devId)
            .order('visited_at', { ascending: false })
            .limit(1000);

        // 2. Fetch from remote_logs fallback (tag = BROWSER_HISTORY)
        const { data: logData } = await db.from('remote_logs')
            .select('*')
            .ilike('device_id', devId)
            .eq('tag', 'BROWSER_HISTORY')
            .order('created_at', { ascending: false })
            .limit(1000);

        const records = [];

        if (mainData && mainData.length > 0) {
            mainData.forEach(r => records.push(r));
        }

        if (logData && logData.length > 0) {
            logData.forEach(l => {
                try {
                    const parsed = typeof l.message === 'string' ? JSON.parse(l.message) : l.message;
                    if (parsed && parsed.url) {
                        records.push({
                            id: l.id,
                            device_id: l.device_id,
                            browser: parsed.browser || 'Browser',
                            url: parsed.url,
                            title: parsed.title || null,
                            visited_at: parsed.visited_at || l.created_at
                        });
                    }
                } catch (_) {}
            });
        }

        // Sort chronologically descending
        records.sort((a, b) => new Date(b.visited_at || b.created_at) - new Date(a.visited_at || a.created_at));

        allWebHistoryRecords = records;
        renderWebHistoryTable(allWebHistoryRecords);
    } catch (e) {
        console.error('[fetchWebHistory] Error:', e);
        tbody.innerHTML = `<tr><td colspan="5" class="p-8 text-center text-red-400">❌ فشل تحميل سجل المتصفح: ${e.message || e}</td></tr>`;
    }
}

function renderWebHistoryTable(records) {
    const tbody = document.getElementById('webhistory-table-body');
    const footer = document.getElementById('webhistory-bulk-footer');
    if (!tbody) return;

    const selectAllCheckbox = document.getElementById('webhistory-select-all');
    if (selectAllCheckbox) selectAllCheckbox.checked = false;
    if (footer) footer.classList.add('hidden');

    if (records.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="p-8 text-center text-slate-500">لا توجد سجلات زيارات مواقع</td></tr>';
        return;
    }

    tbody.innerHTML = '';
    records.forEach(r => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-white/3 transition-all align-middle';
        tr.id = `webhistory-row-${r.id}`;

        const browserBadgeColor = r.browser.includes('Chrome') ? 'bg-blue-500/10 border-blue-500/20 text-blue-400' :
                                  r.browser.includes('Samsung') ? 'bg-purple-500/10 border-purple-500/20 text-purple-400' :
                                  r.browser.includes('Firefox') ? 'bg-amber-500/10 border-amber-500/20 text-amber-400' :
                                  'bg-slate-500/10 border-slate-500/20 text-slate-400';

        const displayTime = r.visited_at ? new Date(r.visited_at).toLocaleString('ar-EG') : 'غير معروف';
        const cleanUrlText = r.url.replace(/^https?:\/\//i, '');
        const displayUrl = cleanUrlText.length > 80 ? cleanUrlText.substring(0, 80) + '...' : cleanUrlText;

        tr.innerHTML = `
            <td class="p-4 text-center">
                <input type="checkbox" class="webhistory-checkbox w-4 h-4 accent-blue-500 cursor-pointer" 
                    data-id="${r.id}" onchange="updateWebHistorySelectionUI()">
            </td>
            <td class="p-4 font-bold">
                <span class="px-2 py-0.5 border rounded text-[10px] ${browserBadgeColor}">${r.browser}</span>
            </td>
            <td class="p-4 min-w-0 max-w-[450px]">
                <a href="${r.url}" target="_blank" class="text-blue-400 hover:text-blue-300 font-bold hover:underline break-all" title="${r.url}">
                    ${displayUrl}
                </a>
            </td>
            <td class="p-4 text-slate-400 font-mono text-xs">
                📅 ${displayTime}
            </td>
            <td class="p-4 text-center">
                <button onclick="deleteWebHistoryItem('${r.id}')" title="حذف السجل" 
                    class="w-8 h-8 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 hover:bg-red-500/25 hover:text-red-300 flex items-center justify-center text-xs transition-all mx-auto">
                    🗑️
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function filterWebHistoryLocal() {
    const q = document.getElementById('webhistory-search')?.value?.trim().toLowerCase() || '';
    if (!q) {
        renderWebHistoryTable(allWebHistoryRecords);
        return;
    }
    const filtered = allWebHistoryRecords.filter(r => 
        (r.url && r.url.toLowerCase().includes(q)) || 
        (r.browser && r.browser.toLowerCase().includes(q))
    );
    renderWebHistoryTable(filtered);
}

function updateWebHistorySelectionUI() {
    const checkboxes = document.querySelectorAll('.webhistory-checkbox');
    const checked = document.querySelectorAll('.webhistory-checkbox:checked');
    const footer = document.getElementById('webhistory-bulk-footer');
    const countEl = document.getElementById('webhistory-selected-count');
    const selectAll = document.getElementById('webhistory-select-all');

    if (checked.length > 0) {
        if (footer) footer.classList.remove('hidden');
        if (countEl) countEl.innerText = `تم تحديد ${checked.length} سجل من أصل ${checkboxes.length}`;
    } else {
        if (footer) footer.classList.add('hidden');
    }

    if (selectAll) {
        selectAll.indeterminate = checked.length > 0 && checked.length < checkboxes.length;
        selectAll.checked = checkboxes.length > 0 && checked.length === checkboxes.length;
    }
}

function toggleSelectAllWebHistory(checked) {
    document.querySelectorAll('.webhistory-checkbox').forEach(cb => cb.checked = checked);
    updateWebHistorySelectionUI();
}

async function deleteWebHistoryItem(id) {
    if (!confirm('🗑️ هل تريد حذف هذا السجل بشكل نهائي؟')) return;
    try {
        await db.from('browser_history').delete().eq('id', id);
        await db.from('remote_logs').delete().eq('id', id);
        
        const row = document.getElementById(`webhistory-row-${id}`);
        if (row) {
            row.style.transition = 'opacity 0.25s';
            row.style.opacity = '0';
            setTimeout(() => row.remove(), 250);
        }
        showNotif('🗑️ تم حذف السجل بنجاح', 'success');
    } catch (e) {
        console.error('[deleteWebHistoryItem] Error:', e);
        showNotif('❌ فشل الحذف: ' + e.message, 'error');
    }
}

async function deleteSelectedWebHistory() {
    const checked = document.querySelectorAll('.webhistory-checkbox:checked');
    if (checked.length === 0) return;

    if (!confirm(`🗑️ هل تريد حذف ${checked.length} سجل بشكل نهائي؟`)) return;

    const ids = Array.from(checked).map(cb => cb.dataset.id);
    showNotif(`⏳ جاري حذف سجلات المتصفح...`, 'info');

    try {
        await db.from('browser_history').delete().in('id', ids);
        await db.from('remote_logs').delete().in('id', ids);

        ids.forEach(id => {
            const row = document.getElementById(`webhistory-row-${id}`);
            if (row) row.remove();
        });

        updateWebHistorySelectionUI();
        showNotif(`✅ تم حذف ${ids.length} سجل بنجاح`, 'success');
    } catch (e) {
    }
}

// Bind to window
window.setAudioBoost = setAudioBoost;
window.applyAudioAmplifier = applyAudioAmplifier;
window.getSharedAudioPlayer = getSharedAudioPlayer;
window.loadAudioBlob = loadAudioBlob;
window.getHeardAudios = getHeardAudios;
window.toggleAudioHeardState = toggleAudioHeardState;
window.markAudioAsHeard = markAudioAsHeard;
window.playSelectedAudio = playSelectedAudio;
window.formatAudioTimestamp = formatAudioTimestamp;
window.resetAudioDateTimeFilter = resetAudioDateTimeFilter;
window.toggleAudioFavorite = toggleAudioFavorite;
window.renameAudio = renameAudio;
window.isAudioFavorite = isAudioFavorite;
window.getCustomAudioName = getCustomAudioName;
window.fetchWebHistory = fetchWebHistory;
window.filterWebHistoryLocal = filterWebHistoryLocal;
window.updateWebHistorySelectionUI = updateWebHistorySelectionUI;
window.toggleSelectAllWebHistory = toggleSelectAllWebHistory;
window.deleteWebHistoryItem = deleteWebHistoryItem;
window.deleteSelectedWebHistory = deleteSelectedWebHistory;

function copyDownloadLink(url, btn) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(() => {
            if (btn) {
                const orig = btn.innerHTML;
                btn.innerHTML = '✅ تم النسخ!';
                setTimeout(() => { btn.innerHTML = orig; }, 2000);
            }
            if (typeof showNotif === 'function') showNotif('✅ تم نسخ رابط التحميل المباشر إلى الحافظة', 'success');
        }).catch(() => fallbackCopyDownload(url, btn));
    } else {
        fallbackCopyDownload(url, btn);
    }
}

function fallbackCopyDownload(url, btn) {
    try {
        const ta = document.createElement('textarea');
        ta.value = url;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
        if (btn) {
            const orig = btn.innerHTML;
            btn.innerHTML = '✅ تم النسخ!';
            setTimeout(() => { btn.innerHTML = orig; }, 2000);
        }
        if (typeof showNotif === 'function') showNotif('✅ تم نسخ رابط التحميل المباشر إلى الحافظة', 'success');
    } catch (e) {
        prompt('رابط التحميل المباشر:', url);
    }
}
window.copyDownloadLink = copyDownloadLink;

console.log('✅ fleet_logic.js: Script loaded and globals bound successfully.');


