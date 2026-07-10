/**
 * fleet_logic.js v5.0
 * Hoverwatch-style Remote Management Dashboard
 * Supabase: kubowqqqawkgghxcktoe.supabase.co
 */

console.log('🚀 fleet_logic.js: Script started loading...');

'use strict';

// ═══════════════════════════════════════════════════════════
//  CONFIG
// ═══════════════════════════════════════════════════════════
const SUPABASE_URL = 'https://kubowqqqawkgghxcktoe.supabase.co';
const SUPABASE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';

const db = supabase.createClient(SUPABASE_URL, SUPABASE_KEY, {
    auth: {
        persistSession: true,
        autoRefreshToken: true,
        detectSessionInUrl: false
    }
});

const ONLINE_THRESHOLD_MINUTES = 10; // رُفعت من 5 إلى 10 دقائق لتتوافق مع heartbeat interval

// ═══════════════════════════════════════════════════════════
//  STATE
// ═══════════════════════════════════════════════════════════
let currentDeviceId  = null;
let currentTab       = 'reports';
let reportFilter     = 'all';
let mapInstance      = null;
let heatLayer        = null;
let routeLayer       = null;
let routeMarkers     = [];
let heatmapVisible   = false;
let mapStyleIndex    = 0;
let allLocations     = [];

const MAP_STYLES = [
    {
        name: '🌍 خريطة الشوارع',
        url: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
        attr: '© OpenStreetMap, © CARTO'
    },
    {
        name: '🛰️ صور الأقمار',
        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
        attr: '© Esri, Maxar, Earthstar Geographics'
    },
    {
        name: '🗺️ هجين (شوارع+صور)',
        url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',
        attr: '© Esri, HERE, Garmin'
    }
];
let usageChartInst   = null;
let autoRefreshTimer = null;
let logsRealtimeSub  = null;
let realtimeChannels = {};   // keyed by deviceId

// ── Live Tracking State ───────────────────────────────────────────────────────
let liveTrackingActive = false;
let liveTrackingTimer  = null;

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
    checkSession();

    // Supabase auth state listener — يحافظ على الجلسة تلقائياً
    db.auth.onAuthStateChange((event, session) => {
        console.log('[Auth] State changed:', event, !!session);
        if (event === 'SIGNED_IN' || event === 'TOKEN_REFRESHED') {
            toggleDashboard(true);
        } else if (event === 'SIGNED_OUT') {
            toggleDashboard(false);
        }
    });
});

// ═══════════════════════════════════════════════════════════
//  AUTH — SESSION MANAGEMENT
// ═══════════════════════════════════════════════════════════
async function checkSession() {
    try {
        const { data: { session } } = await db.auth.getSession();
        toggleDashboard(!!session);
    } catch (e) {
        console.error('[Auth] checkSession error:', e);
        toggleDashboard(false);
    }
}

function toggleDashboard(isLoggedIn) {
    const loginScreen = document.getElementById('loginScreen');
    const dashboard   = document.getElementById('dashboard');

    if (loginScreen) loginScreen.style.display = isLoggedIn ? 'none' : 'flex';
    if (dashboard)   dashboard.style.display   = isLoggedIn ? 'flex' : 'none';

    if (isLoggedIn) {
        const isEnabled = localStorage.getItem('autoRecordOnSelect') === 'true';
        document.querySelectorAll('.auto-record-select-toggle').forEach(el => {
            el.checked = isEnabled;
        });
        loadDeviceList();
        startAutoRefresh();
        buildSettingsToggles();
    } else {
        currentDeviceId = null;
        clearInterval(autoRefreshTimer);
        cleanupAutoRecord();
        cleanupAutoRecordOnSelectTimer();
    }
}

async function handleLogin() {
    const email    = document.getElementById('login-email')?.value?.trim();
    const password = document.getElementById('login-password')?.value;
    const btn      = document.getElementById('login-btn');
    const errorEl  = document.getElementById('login-error');

    if (!email || !password) {
        if (errorEl) { errorEl.innerText = 'الرجاء إدخال البريد الإلكتروني وكلمة المرور'; errorEl.classList.remove('hidden'); }
        return;
    }

    if (btn) { btn.disabled = true; btn.innerText = '⏳ جاري التحقق...'; }
    if (errorEl) errorEl.classList.add('hidden');

    const { error } = await db.auth.signInWithPassword({ email, password });

    if (error) {
        if (errorEl) { errorEl.innerText = '❌ ' + error.message; errorEl.classList.remove('hidden'); }
        if (btn) { btn.disabled = false; btn.innerText = '🔐 دخول النظام'; }
    } else {
        toggleDashboard(true);
    }
}

async function handleLogout() {
    if (!confirm('هل تريد تسجيل الخروج؟')) return;
    await db.auth.signOut();
    toggleDashboard(false);
}

// ═══════════════════════════════════════════════════════════
//  DEVICE LIST
// ═══════════════════════════════════════════════════════════
async function loadDeviceList() {
    try {
        const { data: devices, error } = await db
            .from('remote_settings')
            .select('device_id, nickname, current_version_code, device_info, updated_at')
            .order('updated_at', { ascending: false });

        if (error) throw error;

        const list = document.getElementById('device-list');

        if (!devices || devices.length === 0) {
            list.innerHTML = `
                <p class="px-4 py-2 text-[10px] font-black text-slate-500 uppercase tracking-widest">الأجهزة المسجلة</p>
                <div class="empty-state text-xs"><p>لا توجد أجهزة مسجلة بعد</p></div>`;
            return;
        }

        // Fetch battery from last location
        const locationPromises = devices.map(d =>
            db.from('locations').select('battery_level, timestamp').eq('device_id', d.device_id)
              .order('timestamp', { ascending: false }).limit(1).maybeSingle()
        );
        const locationResults = await Promise.all(locationPromises);

        // Update stats
        const onlineCount = devices.filter((d, i) => isOnline(locationResults[i]?.data?.timestamp || d.updated_at)).length;
        document.getElementById('stat-devices').innerText = devices.length;
        document.getElementById('stat-online').innerText = onlineCount;

        // Update mobile device dropdown
        const dropdown = document.getElementById('mobile-device-dropdown');
        if (dropdown) {
            dropdown.innerHTML = `<option value="">— اختر جهازاً (${devices.length}) —</option>`;
            devices.forEach(device => {
                const name = device.nickname || truncate(device.device_id, 14);
                const opt = document.createElement('option');
                opt.value = device.device_id;
                opt.text = name;
                opt.selected = (device.device_id === currentDeviceId);
                dropdown.appendChild(opt);
            });
        }

        list.innerHTML = `<p class="px-4 py-2 text-[10px] font-black text-slate-500 uppercase tracking-widest">الأجهزة المسجلة</p>`;

        devices.forEach((device, i) => {
            const locData  = locationResults[i]?.data;
            const battery  = locData?.battery_level ?? '--';
            const lastSeen = locData?.timestamp || device.updated_at;
            const online   = isOnline(lastSeen);
            const name     = device.nickname || truncate(device.device_id, 14);
            const model    = extractModel(device.device_info);

            const item = document.createElement('div');
            item.className = 'device-item' + (device.device_id === currentDeviceId ? ' active' : '');
            item.dataset.id = device.device_id;
            item.onclick = () => selectDevice(device.device_id, device);
            item.innerHTML = `
                <div class="status-dot ${online ? 'online' : 'offline'}"></div>
                <div class="flex-1 min-w-0">
                    <p class="text-sm font-bold text-slate-200 truncate">${name}</p>
                    <p class="text-[10px] text-slate-500 truncate">${model}</p>
                </div>
                <div class="text-right flex-shrink-0">
                    <div class="flex items-center gap-1 mb-0.5">
                        <span class="text-[11px] font-bold ${batteryColor(battery)}">${battery !== '--' ? battery + '%' : '--'}</span>
                    </div>
                    <p class="text-[9px] text-slate-600">${formatRelativeTime(lastSeen)}</p>
                </div>`;
            list.appendChild(item);
        });

        // Fetch pending commands count
        const { count } = await db.from('commands').select('*', { count: 'exact', head: true }).eq('status', 'PENDING');
        document.getElementById('stat-pending').innerText = count || 0;

    } catch (e) {
        console.error('loadDeviceList error:', e);
        showNotif('⚠️ خطأ في تحميل قائمة الأجهزة', 'error');
    }
}

// ═══════════════════════════════════════════════════════════
//  SELECT DEVICE
// ═══════════════════════════════════════════════════════════
async function selectDevice(deviceId, deviceData = null) {
    cleanupAutoRecord();
    if (!deviceId) {
        currentDeviceId = null;
        document.getElementById('current-device-name').innerText = 'اختر جهازاً من القائمة';
        document.getElementById('current-device-id').innerText   = 'لم يُختر جهاز';
        document.getElementById('device-model').innerText        = '--';
        document.getElementById('reported-version').classList.add('hidden');
        document.querySelectorAll('.device-item').forEach(el => el.classList.remove('active'));
        const dropdown = document.getElementById('mobile-device-dropdown');
        if (dropdown) dropdown.value = '';
        return;
    }
    currentDeviceId = deviceId;

    // Update sidebar active state
    document.querySelectorAll('.device-item').forEach(el => {
        el.classList.toggle('active', el.dataset.id === deviceId);
    });

    // Update mobile dropdown selection
    const dropdown = document.getElementById('mobile-device-dropdown');
    if (dropdown) {
        dropdown.value = deviceId;
    }

    // Fetch full device data if not provided
    if (!deviceData) {
        const { data } = await db.from('remote_settings').select('*').eq('device_id', deviceId).maybeSingle();
        deviceData = data;
    }

    // Update header
    const name = deviceData?.nickname || 'جهاز غير مسمى';
    document.getElementById('current-device-name').innerText = name;
    document.getElementById('current-device-id').innerText   = truncate(deviceId, 22);
    document.getElementById('device-model').innerText        = extractModel(deviceData?.device_info) || '--';

    const vCode = deviceData?.current_version_code;
    const vEl   = document.getElementById('reported-version');
    if (vCode) {
        vEl.innerText = `v${vCode}`;
        vEl.classList.remove('hidden');
    } else {
        vEl.classList.add('hidden');
    }

    // Update settings nickname field
    const nickInput = document.getElementById('edit-nickname');
    if (nickInput) nickInput.value = deviceData?.nickname || '';

    // Populate settings from DB
    syncSettingsFromDB(deviceData);

    // Trigger auto-record immediately if enabled (non-blocking, fast startup)
    const isEnabled = localStorage.getItem('autoRecordOnSelect') === 'true';
    document.querySelectorAll('.auto-record-select-toggle').forEach(el => {
        el.checked = isEnabled;
    });
    cleanupAutoRecordOnSelectTimer();
    if (isEnabled) {
        console.log('⏺ Auto-recording triggered immediately for device:', deviceId);
        setTimeout(() => {
            if (currentDeviceId === deviceId) {
                triggerRecordNow();
            }
        }, 100);

        // Start periodic recording every 75 seconds while dashboard is open
        autoRecordOnSelectTimer = setInterval(() => {
            if (currentDeviceId === deviceId) {
                console.log('🔄 [autoRecordOnSelectTimer] Triggering periodic record for:', deviceId);
                triggerRecordNow();
            }
        }, 75_000);
    }

    // Load current tab content
    await refreshCurrentData();
    
    // Subscribe to realtime updates for this device
    subscribeToRealtimeUpdates(deviceId);
}

// ═══════════════════════════════════════════════════════════
//  TAB SWITCHING
// ═══════════════════════════════════════════════════════════
function switchTab(tabId) {
    currentTab = tabId;

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

        // Screenshots metadata
        if (reportFilter === 'all' || reportFilter === 'screenshots') {
            const { data: shots } = await db.from('remote_logs')
                .select('*').eq('device_id', currentDeviceId).eq('tag', 'ScreenCaptureEngine')
                .order('created_at', { ascending: false }).limit(40);
            if (shots) items.push(...shots.map(s => ({ ...s, _type: 'screenshot' })));
        }

        // Notifications
        if (reportFilter === 'all' || reportFilter === 'notifications') {
            const { data: notifs } = await db.from('notification_logs')
                .select('*').eq('device_id', currentDeviceId)
                .order('post_time', { ascending: false }).limit(40);
            if (notifs) items.push(...notifs.map(n => ({ ...n, created_at: n.post_time, _type: 'notification' })));
        }

        // Events / remote logs
        if (reportFilter === 'all' || reportFilter === 'events') {
            const { data: events } = await db.from('remote_logs')
                .select('*').eq('device_id', currentDeviceId).neq('tag', 'ScreenCaptureEngine')
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

async function loadAudioBlob(btn, filePath, cardId) {
    const card = document.getElementById(`audio-card-${cardId}`);
    if (!card) return;
    const audio = card.querySelector('audio');
    const status = card.querySelector('.audio-status-text');
    
    if (status) {
        status.innerText = '⏳ جاري التحميل من السحاب...';
        status.classList.remove('hidden');
    }
    btn.style.display = 'none';

    try {
        const { data, error } = await db.storage.from('monitoring_data').download(filePath);
        if (error) throw error;

        const blobUrl = URL.createObjectURL(data);
        audio.src = blobUrl;
        audio.style.display = 'inline-flex';
        if (status) status.classList.add('hidden');
        audio.play().catch(e => console.warn('Play interrupted:', e));
    } catch (e) {
        console.error('[loadAudioBlob] Failed:', e);
        if (status) status.innerText = '❌ فشل التحميل: ' + (e.message || 'خطأ اتصال');
        btn.style.display = 'inline-flex';
        btn.innerText = '🔄 إعادة المحاولة';
    }
}

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
        const { data: files } = await db.storage
            .from('monitoring_data')
            .list(`screenshots/${currentDeviceId}`, { limit: 1000, sortBy: { column: 'created_at', order: 'desc' } });

        if (!files || files.length === 0) {
            grid.innerHTML = '<div class="col-span-full empty-state"><p class="text-sm">لا توجد صور ملتقطة</p></div>';
            return;
        }

        const html = files.filter(f => f.name.match(/\.(webp|jpg|png)$/i)).map(f => {
            const filePath = `screenshots/${currentDeviceId}/${f.name}`;
            const time = formatRelativeTime(f.created_at);
            return `
                <div class="screenshot-card animate-fade" onclick="openLightbox(this.querySelector('img').src)">
                    <div class="relative">
                        <img data-path="${filePath}" src="" alt="${f.name}"
                             class="lazy-blob-img w-full object-cover aspect-[9/16] bg-slate-900"
                             loading="lazy" onerror="this.parentElement.parentElement.style.display='none'">
                        <div class="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/80 to-transparent p-3">
                            <p class="text-[10px] text-slate-300 font-mono">${time}</p>
                        </div>
                    </div>
                </div>`;
        }).join('');

        grid.innerHTML = html || '<div class="col-span-full empty-state"><p>لا توجد صور</p></div>';

        // Load images securely as Blob URLs via Supabase storage client download API
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
//  MAP — LIVE TRACK (Enhanced v6)
// ═══════════════════════════════════════════════════════════
let _baseTileLayer = null;

function initMap() {
    if (mapInstance) return;
    setTimeout(() => {
        mapInstance = L.map('map', {
            zoomControl: false,
            attributionControl: true
        }).setView([32.0, -6.5], 6);

        L.control.zoom({ position: 'topleft' }).addTo(mapInstance);

        // Default tile layer (dark streets)
        const style = MAP_STYLES[mapStyleIndex];
        _baseTileLayer = L.tileLayer(style.url, {
            attribution: style.attr,
            maxZoom: 19
        }).addTo(mapInstance);

        // Coords display on mouse move
        mapInstance.on('mousemove', e => {
            const d = document.getElementById('coords-display');
            d.classList.remove('hidden');
            d.innerText = `${e.latlng.lat.toFixed(5)}, ${e.latlng.lng.toFixed(5)}`;
        });

        if (currentDeviceId) fetchPositions();
    }, 100);
}

function switchMapStyle() {
    if (!mapInstance) return;
    mapStyleIndex = (mapStyleIndex + 1) % MAP_STYLES.length;
    const style = MAP_STYLES[mapStyleIndex];
    if (_baseTileLayer) mapInstance.removeLayer(_baseTileLayer);
    _baseTileLayer = L.tileLayer(style.url, {
        attribution: style.attr,
        maxZoom: 19
    }).addTo(mapInstance);
    // Move base layer to bottom
    _baseTileLayer.bringToBack();
    // Update button text
    const btn = document.getElementById('map-style-btn');
    if (btn) btn.innerText = MAP_STYLES[(mapStyleIndex + 1) % MAP_STYLES.length].name;
}

// Haversine distance in km between two [lat,lng] points
function haversineKm(a, b) {
    const R = 6371;
    const dLat = (b[0] - a[0]) * Math.PI / 180;
    const dLon = (b[1] - a[1]) * Math.PI / 180;
    const s = Math.sin(dLat/2)**2 +
              Math.cos(a[0]*Math.PI/180) * Math.cos(b[0]*Math.PI/180) *
              Math.sin(dLon/2)**2;
    return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1-s));
}

// Interpolate hex colors for gradient route
function lerpColor(a, b, t) {
    const ah = parseInt(a.slice(1), 16);
    const bh = parseInt(b.slice(1), 16);
    const ar = (ah >> 16) & 0xff, ag = (ah >> 8) & 0xff, ab = ah & 0xff;
    const br = (bh >> 16) & 0xff, bg = (bh >> 8) & 0xff, bb = bh & 0xff;
    const rr = Math.round(ar + (br - ar) * t);
    const rg = Math.round(ag + (bg - ag) * t);
    const rb = Math.round(ab + (bb - ab) * t);
    return `#${((1<<24)|(rr<<16)|(rg<<8)|rb).toString(16).slice(1)}`;
}

function clearMapLayers() {
    if (routeLayer) { mapInstance.removeLayer(routeLayer); routeLayer = null; }
    routeMarkers.forEach(m => mapInstance.removeLayer(m));
    routeMarkers = [];
    if (heatLayer) { mapInstance.removeLayer(heatLayer); heatLayer = null; }
}

function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `fixed bottom-24 left-1/2 transform -translate-x-1/2 px-6 py-3 rounded-2xl text-xs font-black text-white shadow-2xl z-[9999] transition-all duration-300 opacity-0 translate-y-2 flex items-center gap-2`;
    
    if (type === 'error') {
        toast.style.background = 'rgba(239, 68, 68, 0.95)';
        toast.style.border = '1px solid rgba(255, 255, 255, 0.1)';
        toast.innerHTML = `⚠️ <span>${message}</span>`;
    } else if (type === 'success') {
        toast.style.background = 'rgba(34, 197, 94, 0.95)';
        toast.style.border = '1px solid rgba(255, 255, 255, 0.1)';
        toast.innerHTML = `✅ <span>${message}</span>`;
    } else {
        toast.style.background = 'rgba(30, 41, 59, 0.95)';
        toast.style.border = '1px solid rgba(255, 255, 255, 0.1)';
        toast.innerHTML = `ℹ️ <span>${message}</span>`;
    }
    
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.classList.remove('opacity-0', 'translate-y-2');
        toast.classList.add('opacity-100', 'translate-y-0');
    }, 10);
    
    setTimeout(() => {
        toast.classList.remove('opacity-100', 'translate-y-0');
        toast.classList.add('opacity-0', 'translate-y-2');
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

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

function filterGPSPoints(locs) {
    if (!locs || locs.length === 0) return [];
    
    // Clean and validate coordinates to avoid NaN or invalid LatLng crashes in Leaflet Map
    const cleanLocs = [];
    for (let i = 0; i < locs.length; i++) {
        const l = locs[i];
        if (!l) continue;
        const lat = parseFloat(l.latitude);
        const lng = parseFloat(l.longitude);
        if (!isNaN(lat) && isFinite(lat) && !isNaN(lng) && isFinite(lng)) {
            cleanLocs.push({
                ...l,
                latitude: lat,
                longitude: lng,
                accuracy: l.accuracy ? parseFloat(l.accuracy) : null,
                battery_level: l.battery_level != null ? parseFloat(l.battery_level) : null
            });
        }
    }
    
    if (cleanLocs.length === 0) return [];
    
    // Sort chronologically (oldest to newest)
    const chronological = [...cleanLocs].reverse();
    
    // Step 1: Filter out bad accuracy points (e.g. > 100 meters)
    // But don't filter out everything - keep at least 2 points
    let filtered = chronological.filter(l => !l.accuracy || l.accuracy <= 100);
    if (filtered.length < 2) {
        filtered = chronological;
    }
    
    // Step 2: Remove consecutive duplicate or extremely close points (e.g., within 5 meters)
    // This removes GPS noise when stationary and prevents OSRM errors.
    const result = [filtered[0]];
    for (let i = 1; i < filtered.length; i++) {
        const prev = result[result.length - 1];
        const curr = filtered[i];
        
        const dist = haversineKm([prev.latitude, prev.longitude], [curr.latitude, curr.longitude]) * 1000;
        if (dist >= 5) {
            result.push(curr);
        }
    }
    
    // Make sure we keep the latest point (even if it's close)
    const lastFiltered = filtered[filtered.length - 1];
    const lastResult = result[result.length - 1];
    if (lastResult.latitude !== lastFiltered.latitude || lastResult.longitude !== lastFiltered.longitude) {
        result.push(lastFiltered);
    }
    
    return result;
}

async function snapPointsToRoads(locsWithTime) {
    // locsWithTime: array of {lat, lng, ts} objects OR fallback [lat,lng] arrays
    const pts = Array.isArray(locsWithTime[0])
        ? locsWithTime
        : locsWithTime.map(l => [l.latitude, l.longitude]);

    if (pts.length < 2) return pts;

    // ── Guard: تخطي OSRM إذا كانت النقاط أقل من 3 أو المسافة الكلية < 50 متر ──
    if (pts.length < 3) {
        console.log(`Skipping OSRM snap: only ${pts.length} point(s) — returning raw`);
        return pts;
    }
    const totalDistM = pts.reduce((sum, p, i) => {
        if (i === 0) return 0;
        return sum + haversineKm(pts[i-1], p) * 1000;
    }, 0);
    if (totalDistM < 50) {
        console.log(`Skipping OSRM snap: total distance ${totalDistM.toFixed(1)}m < 50m — returning raw`);
        return pts;
    }

    console.log(`Snapping ${pts.length} coordinates to road network... (total: ${(totalDistM/1000).toFixed(2)} km)`);


    // Chunk size 60 (smaller = more reliable with public OSRM server)
    const CHUNK_SIZE = 60;
    const OVERLAP    = 4;
    const chunks     = [];

    for (let i = 0; i < pts.length; i += (CHUNK_SIZE - OVERLAP)) {
        const chunk = locsWithTime.slice
            ? locsWithTime.slice(i, i + CHUNK_SIZE)
            : pts.slice(i, i + CHUNK_SIZE);
        if (chunk.length < 2) break;
        chunks.push(chunk);
        if (i + CHUNK_SIZE >= pts.length) break;
    }

    // OSRM servers: primary + fallback
    const OSRM_SERVERS = [
        'https://router.project-osrm.org',
        'https://routing.openstreetmap.de'
    ];

    const snappedPaths = [];

    for (let ci = 0; ci < chunks.length; ci++) {
        const chunk = chunks[ci];

        // Extract lat/lng pairs for this chunk
        const chunkPts = Array.isArray(chunk[0])
            ? chunk
            : chunk.map(l => [l.latitude, l.longitude]);

        // Format coords: lng,lat;lng,lat...
        const coordStr = chunkPts.map(p => `${p[1]},${p[0]}`).join(';');

        // Build timestamps if available (improves OSRM accuracy significantly)
        let tsParam = '';
        if (!Array.isArray(chunk[0]) && chunk[0].timestamp) {
            const timestamps = chunk.map(l => Math.floor(new Date(l.timestamp).getTime() / 1000));
            tsParam = `&timestamps=${timestamps.join(';')}`;
        }

        // Build radiuses (50m per point = allow snapping up to 50m from recorded point)
        const radiusParam = `&radiuses=${chunkPts.map(() => '50').join(';')}`;

        let snappedChunk = null;

        for (const server of OSRM_SERVERS) {
            try {
                const matchUrl = `${server}/match/v1/driving/${coordStr}?overview=full&geometries=geojson${tsParam}${radiusParam}`;
                let res = await fetch(matchUrl, { signal: AbortSignal.timeout(10000) });
                
                // If match service fails or returns HTTP 400 (often due to sparse/distant coordinates),
                // fall back to the route service which calculates paths between waypoints regardless of timestamps.
                if (!res.ok || res.status === 400) {
                    console.log(`⚠️ OSRM Match failed on ${server} (HTTP ${res.status}). Trying OSRM Route fallback...`);
                    const routeUrl = `${server}/route/v1/driving/${coordStr}?overview=full&geometries=geojson`;
                    res = await fetch(routeUrl, { signal: AbortSignal.timeout(10000) });
                    if (!res.ok) throw new Error(`Route HTTP ${res.status}`);
                    
                    const json = await res.json();
                    if (json.code === 'Ok' && json.routes && json.routes.length > 0) {
                        snappedChunk = json.routes[0].geometry.coordinates.map(c => [c[1], c[0]]);
                        console.log(`✅ OSRM chunk ${ci+1}/${chunks.length} routed via fallback on ${server}`);
                        break;
                    } else {
                        throw new Error(`Route error: ${json.code}`);
                    }
                }

                const json = await res.json();
                if (json.code === 'Ok' && json.matchings && json.matchings.length > 0) {
                    // Merge all matchings (OSRM can return multiple when there are gaps)
                    let allCoords = [];
                    for (const m of json.matchings) {
                        allCoords = allCoords.concat(
                            m.geometry.coordinates.map(c => [c[1], c[0]])
                        );
                    }
                    snappedChunk = allCoords;
                    console.log(`✅ OSRM chunk ${ci+1}/${chunks.length} snapped via ${server}`);
                    break; // success
                } else {
                    console.warn(`OSRM Match ${server} returned code: ${json.code}`);
                }
            } catch (err) {
                console.warn(`OSRM ${server} failed: ${err.message}`);
            }
        }

        // If both servers failed, use raw points as fallback
        snappedPaths.push(snappedChunk || chunkPts);

        // Polite delay between chunks to avoid rate limiting
        if (ci < chunks.length - 1) {
            await new Promise(r => setTimeout(r, 300));
        }
    }

    if (snappedPaths.length === 0) return pts;

    // Merge chunks smoothly (skip overlapping points within 20m)
    let merged = snappedPaths[0];
    for (let i = 1; i < snappedPaths.length; i++) {
        const path = snappedPaths[i];
        if (!path || path.length === 0) continue;
        const lastPt = merged[merged.length - 1];
        let startIdx = 0;
        while (startIdx < path.length) {
            if (haversineKm(lastPt, path[startIdx]) * 1000 > 20) break;
            startIdx++;
        }
        merged = merged.concat(path.slice(startIdx));
    }

    return merged;
}

// ── Live Tracking Mode ───────────────────────────────────────────────────────
function toggleLiveTracking() {
    liveTrackingActive = !liveTrackingActive;
    const btn = document.getElementById('live-track-btn');

    if (liveTrackingActive) {
        if (btn) {
            btn.innerText = '🔴 إيقاف المتابعة';
            btn.style.background = 'rgba(239,68,68,0.2)';
            btn.style.borderColor = 'rgba(239,68,68,0.4)';
            btn.style.color = '#fca5a5';
        }
        showNotif('📡 وضع التتبع المباشر نشط — تحديث كل 30 ثانية', 'info');
        // تحديث فوري أولي
        fetchPositions();
        // ثم كل 30 ثانية
        liveTrackingTimer = setInterval(() => {
            if (currentDeviceId && mapInstance && currentTab === 'track') {
                fetchPositions();
            }
        }, 30_000);
    } else {
        clearInterval(liveTrackingTimer);
        liveTrackingTimer = null;
        if (btn) {
            btn.innerText = '📡 تتبع مباشر';
            btn.style.background = '';
            btn.style.borderColor = '';
            btn.style.color = '';
        }
        showNotif('⏹️ تم إيقاف وضع التتبع المباشر', 'info');
    }
}

async function fetchPositions() {
    if (!currentDeviceId || !mapInstance) return;

    // Show loading indicator on button
    const refreshBtn = document.getElementById('refresh-route-btn');
    if (refreshBtn) { refreshBtn.innerText = '⏳ جاري...'; refreshBtn.disabled = true; }

    try {
        // Ensure date picker is initialized to today
        initRouteDatePicker();
        
        const datePicker = document.getElementById('route-date-picker');
        let query = db.from('locations')
            .select('latitude, longitude, accuracy, timestamp, battery_level')
            .eq('device_id', currentDeviceId)
            .order('timestamp', { ascending: false });

        if (datePicker && datePicker.value) {
            const dateStr = datePicker.value;
            // Get local day bounds, convert to ISO UTC for database query
            const startDate = new Date(dateStr + 'T00:00:00');
            const endDate = new Date(dateStr + 'T23:59:59.999');
            
            query = query
                .gte('timestamp', startDate.toISOString())
                .lte('timestamp', endDate.toISOString())
                .limit(1000); // 1000 point limit to cover full day details
        } else {
            query = query.limit(200); // fallback
        }

        const { data: locs, error } = await query;

        if (refreshBtn) { refreshBtn.innerText = '🔄 تحديث المسار'; refreshBtn.disabled = false; }
        
        if (error) {
            console.error('Database query error:', error);
            showToast('⚠️ فشل في جلب سجلات الموقع!', 'error');
            return;
        }

        // Empty state handling
        if (!locs || locs.length === 0) {
            clearMapLayers();
            const statsEl = document.getElementById('map-stats');
            if (statsEl) statsEl.classList.add('hidden');
            showToast('⚠️ لا توجد مسارات مسجلة لهذا التاريخ!', 'error');
            return;
        }

        allLocations = locs;
        clearMapLayers();

        // 1. Preprocess and filter GPS points
        const filteredLocs = filterGPSPoints(locs);
        if (filteredLocs.length === 0) return;

        const pts = filteredLocs.map(l => [l.latitude, l.longitude]);
        const n = pts.length;

        // 2. Snap coordinates to roads (pass full locs with timestamps for better OSRM accuracy)
        const snappedPts = await snapPointsToRoads(filteredLocs);
        const nSnapped = snappedPts.length;

        // --- Draw gradient route along snapped road coordinates ---
        const COLOR_OLD   = '#6366f1'; // premium neon indigo (oldest)
        const COLOR_NEW   = '#10b981'; // vibrant emerald green (newest)
        for (let i = 0; i < nSnapped - 1; i++) {
            const t   = nSnapped > 1 ? i / (nSnapped - 1) : 1;
            const col = lerpColor(COLOR_OLD, COLOR_NEW, t);
            
            // Glow underlay for a sleek modern look
            const underlay = L.polyline([snappedPts[i], snappedPts[i+1]], {
                color: col, weight: 10, opacity: 0.25,
                lineJoin: 'round', lineCap: 'round'
            }).addTo(mapInstance);
            routeMarkers.push(underlay);

            // Glowing core line
            const seg = L.polyline([snappedPts[i], snappedPts[i+1]], {
                color: col, weight: 4.5, opacity: 0.9,
                lineJoin: 'round', lineCap: 'round'
            }).addTo(mapInstance);
            routeMarkers.push(seg);
        }

        // --- Directional arrows along the snapped route ---
        if (nSnapped > 1 && L.polylineDecorator) {
            const fullLine = L.polyline(snappedPts, { opacity: 0 }).addTo(mapInstance);
            routeMarkers.push(fullLine);
            const arrows = L.polylineDecorator(fullLine, {
                patterns: [{
                    offset: '10%', repeat: '15%',
                    symbol: L.Symbol.arrowHead({
                        pixelSize: 10,
                        headAngle: 40,
                        fill: true,
                        polygon: false,
                        pathOptions: { color: '#fff', weight: 2, opacity: 0.6 }
                    })
                }]
            }).addTo(mapInstance);
            routeMarkers.push(arrows);
        }

        // --- Intermediate waypoint dots (based on actual recorded telemetry) ---
        for (let i = 1; i < n - 1; i += Math.max(1, Math.floor(n / 15))) {
            const loc = filteredLocs[i];
            const t   = i / (n - 1);
            const col = lerpColor(COLOR_OLD, COLOR_NEW, t);
            const nextLoc = filteredLocs[Math.min(i + 1, n - 1)];
            const dist = haversineKm([loc.latitude, loc.longitude], [nextLoc.latitude, nextLoc.longitude]);
            const dtSec = (new Date(nextLoc.timestamp) - new Date(loc.timestamp)) / 1000;
            const speedKmh = dtSec > 0 ? (dist / dtSec * 3600).toFixed(1) : '?';

            const dot = L.circleMarker([loc.latitude, loc.longitude], {
                radius: 5, fillColor: col,
                color: '#1a1a2e', weight: 1.5,
                opacity: 1, fillOpacity: 0.9
            }).bindTooltip(
                `<div style="font-family:sans-serif;font-size:12px;direction:rtl;">
                    <b>${formatRelativeTime(loc.timestamp)}</b><br>
                    📍 ${loc.latitude.toFixed(5)}, ${loc.longitude.toFixed(5)}<br>
                    🎯 دقة: ${loc.accuracy ? loc.accuracy + ' م' : '?'}<br>
                    ⚡ ${loc.battery_level != null ? loc.battery_level + '%' : '?'} بطارية<br>
                    🚀 السرعة: ~${speedKmh} كم/س
                </div>`,
                { sticky: true, direction: 'top', className: 'map-tooltip' }
            ).addTo(mapInstance);
            routeMarkers.push(dot);
        }

        // --- START marker (placed at snapped start if available, otherwise raw start) ---
        if (nSnapped > 0) {
            const startPt = snappedPts[0];
            const startLoc = filteredLocs[0];
            const startIcon = L.divIcon({
                html: `<div style="width:26px;height:26px;background:#6366f1;border:3px solid #fff;border-radius:50% 50% 50% 0;transform:rotate(-45deg);box-shadow:0 2px 8px rgba(0,0,0,0.5);"></div>`,
                iconSize: [26, 26], iconAnchor: [13, 26]
            });
            const sm = L.marker(startPt, { icon: startIcon })
                .bindPopup(`<div style="direction:rtl;min-width:160px">
                    <b style="color:#3b82f6">📍 نقطة البداية</b><br>
                    ${new Date(startLoc.timestamp).toLocaleString('ar-MA')}<br>
                    🎯 دقة: ${startLoc.accuracy || '?'} م
                </div>`)
                .addTo(mapInstance);
            routeMarkers.push(sm);
        }

        // --- END / CURRENT marker (placed at the latest clean GPS point for true current state) ---
        const latest = filteredLocs[filteredLocs.length - 1]; // filteredLocs is ascending -> last element is newest
        const pulseHtml = `
            <div style="position:relative;width:36px;height:36px;">
                <div style="position:absolute;inset:0;background:rgba(34,197,94,0.3);border-radius:50%;animation:ping 1.4s cubic-bezier(0,0,.2,1) infinite;"></div>
                <div style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:18px;height:18px;background:#22c55e;border:3px solid #fff;border-radius:50%;box-shadow:0 2px 10px rgba(34,197,94,0.7);"></div>
            </div>`;
        const liveIcon = L.divIcon({ html: pulseHtml, iconSize: [36, 36], iconAnchor: [18, 18], className: '' });
        const lm = L.marker([latest.latitude, latest.longitude], { icon: liveIcon, zIndexOffset: 1000 })
            .bindPopup(`<div style="direction:rtl;min-width:190px">
                <b style="color:#22c55e">✅ الموقع الحالي</b><br>
                <span style="font-size:11px;color:#888">${new Date(latest.timestamp).toLocaleString('ar-MA')}</span><br>
                📍 ${latest.latitude.toFixed(6)}, ${latest.longitude.toFixed(6)}<br>
                🎯 دقة: ${latest.accuracy ? latest.accuracy + ' م' : 'غير محدد'}<br>
                ⚡ ${latest.battery_level != null ? latest.battery_level + '%' : '?'} بطارية
            </div>`)
            .openPopup()
            .addTo(mapInstance);
        routeMarkers.push(lm);

        // --- Fly to latest with nice zoom ---
        if (mapInstance) {
            try {
                mapInstance.invalidateSize();
                const currentCenter = mapInstance.getCenter();
                if (currentCenter && !isNaN(currentCenter.lat) && !isNaN(currentCenter.lng)) {
                    mapInstance.flyTo([latest.latitude, latest.longitude], 16, { duration: 1.8, easeLinearity: 0.25 });
                } else {
                    mapInstance.setView([latest.latitude, latest.longitude], 16);
                }
            } catch (e) {
                console.warn('Map flyTo failed, falling back to setView:', e);
                try {
                    mapInstance.setView([latest.latitude, latest.longitude], 16);
                } catch (err) {
                    console.error('Map setView fallback failed:', err);
                }
            }
        }

        // --- Update stats bar with road-snapped distance ---
        updateMapStats(filteredLocs, snappedPts);

        // --- Update last-location info panel ---
        updateLastLocationInfo(latest);

        // --- Heatmap update if active (based on clean locations for cluster intensity) ---
        if (heatmapVisible) updateHeatmap(filteredLocs);

    } catch (e) {
        console.error('fetchPositions:', e);
        if (refreshBtn) { refreshBtn.innerText = '🔄 تحديث المسار'; refreshBtn.disabled = false; }
    }
}

/** عرض معلومات آخر موقع في لوحة التتبع */
function updateLastLocationInfo(latest) {
    const infoEl = document.getElementById('last-location-info');
    if (!infoEl || !latest) return;

    const timeAgo = formatRelativeTime(latest.timestamp);
    const exactTime = new Date(latest.timestamp).toLocaleTimeString('ar-MA');
    const batt = latest.battery_level != null ? latest.battery_level + '%' : '--';
    const acc  = latest.accuracy ? latest.accuracy + ' م' : '--';

    infoEl.innerHTML = `
        <div style="display:flex;flex-direction:column;gap:4px;">
            <div style="display:flex;align-items:center;gap:6px;">
                <span style="width:8px;height:8px;background:#22c55e;border-radius:50%;display:inline-block;box-shadow:0 0 6px #22c55e;"></span>
                <span style="font-size:11px;font-weight:700;color:#e2e8f0;">آخر موقع: ${timeAgo}</span>
            </div>
            <span style="font-size:10px;color:#64748b;">${exactTime} · دقة: ${acc} · بطارية: ${batt}</span>
            <span style="font-size:10px;color:#64748b;font-family:monospace;">${latest.latitude?.toFixed(5)}, ${latest.longitude?.toFixed(5)}</span>
        </div>`;
    infoEl.classList.remove('hidden');
}

function updateMapStats(filteredLocs, snappedPts) {
    const statsEl = document.getElementById('map-stats');
    if (!statsEl) return;

    const n = filteredLocs.length;
    let totalKm = 0;
    const nSnapped = snappedPts.length;
    
    // Sum distance along the snapped road path
    for (let i = 0; i < nSnapped - 1; i++) {
        totalKm += haversineKm(snappedPts[i], snappedPts[i+1]);
    }

    const latest    = filteredLocs[filteredLocs.length - 1];
    const oldest    = filteredLocs[0];
    const spanHours = ((new Date(latest.timestamp) - new Date(oldest.timestamp)) / 3600000).toFixed(1);
    const avgSpeed  = spanHours > 0 ? (totalKm / spanHours).toFixed(1) : '?';

    statsEl.innerHTML = `
        <span>🗓️ <b>${n}</b> نقطة</span>
        <span>📏 <b>${totalKm.toFixed(2)} كم</b></span>
        <span>⏱️ <b>${spanHours}س</b></span>
        <span>🚀 <b>~${avgSpeed} كم/س</b> متوسط</span>
    `;
    statsEl.classList.remove('hidden');
}

function toggleHeatmap() {
    heatmapVisible = !heatmapVisible;
    const btn = document.getElementById('heatmap-toggle');
    if (!mapInstance) return;

    if (!heatmapVisible && heatLayer) {
        mapInstance.removeLayer(heatLayer);
        heatLayer = null;
        btn.innerText = '🌡️ Heatmap تفعيل';
        btn.classList.remove('btn-amber');
        btn.classList.add('btn-primary');
    } else {
        btn.innerText = '🌡️ إخفاء Heatmap';
        btn.classList.remove('btn-primary');
        btn.classList.add('btn-amber');
        if (allLocations.length > 0) updateHeatmap(allLocations);
        else fetchPositions();
    }
}

function updateHeatmap(locs) {
    if (!mapInstance) return;
    if (heatLayer) mapInstance.removeLayer(heatLayer);
    const points = locs.map(l => [l.latitude, l.longitude, 0.6]);
    heatLayer = L.heatLayer(points, {
        radius: 28, blur: 22, maxZoom: 18,
        gradient: { 0.2: '#1d4ed8', 0.45: '#7c3aed', 0.7: '#f59e0b', 0.9: '#ef4444' }
    }).addTo(mapInstance);
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
    fetchAudio();
};

async function fetchAudio() {
    if (!currentDeviceId) return;
    const list = document.getElementById('audio-list');
    
    // Check if any audio is currently playing inside the list
    if (list) {
        const playingAudio = Array.from(list.querySelectorAll('audio')).find(a => a && !a.paused && !a.ended);
        if (playingAudio) {
            console.log('🔊 Audio playback in progress, skipping auto-refresh to prevent interruption.');
            return;
        }
    }

    list.innerHTML = '<div class="p-6 flex justify-center"><div class="spinner"></div></div>';
    try {
        const { data: files, error: listErr } = await db.storage
            .from('monitoring_data')
            .list(`audio/${currentDeviceId}`, { limit: 1000, sortBy: { column: 'created_at', order: 'desc' } });

        if (listErr) throw listErr;

        if (!files || files.length === 0) {
            list.innerHTML = '<div class="empty-state text-sm"><p>لا توجد تسجيلات صوتية</p></div>';
            return;
        }

        const audioFiles = files.filter(f => f.name.match(/\.(m4a|mp3|aac|wav|ogg|webm)$/i));
        if (audioFiles.length === 0) {
            list.innerHTML = '<div class="empty-state text-sm"><p>لا توجد تسجيلات صوتية</p></div>';
            return;
        }

        const heard = getHeardAudios();
        let filteredAudioFiles = audioFiles;
        if (audioFilter === 'heard') {
            filteredAudioFiles = audioFiles.filter(f => heard[`audio/${currentDeviceId}/${f.name}`]);
        } else if (audioFilter === 'unheard') {
            filteredAudioFiles = audioFiles.filter(f => !heard[`audio/${currentDeviceId}/${f.name}`]);
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

            const card = document.createElement('div');
            card.className = 'audio-card animate-fade';
            card.id = `audio-card-${cardId}`;
            card.innerHTML = `
                <input type="checkbox" class="audio-checkbox w-4 h-4 accent-blue-500 flex-shrink-0 cursor-pointer"
                    data-path="${filePath}" data-cardid="${cardId}" data-createdat="${f.created_at}"
                    onchange="updateAudioSelectionUI()">
                <div class="flex items-center gap-3 flex-1 min-w-0">
                    <div class="w-10 h-10 rounded-xl ${iconBg} border flex items-center justify-center text-lg flex-shrink-0">${icon}</div>
                    <div class="min-w-0">
                        <div class="flex items-center gap-2 mb-0.5">
                            ${label}
                            <span class="audio-heard-badge text-[10px] font-bold px-2 py-0.5 rounded cursor-pointer select-none transition-all flex items-center gap-1 ${
                                isHeard 
                                ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20 hover:text-emerald-300' 
                                : 'bg-blue-500/10 border border-blue-500/20 text-blue-400 hover:bg-blue-500/20 hover:text-blue-300'
                            }" onclick="toggleAudioHeardState('${filePath}', '${cardId}', event)">
                                ${isHeard ? '🎧 مسموع' : '🆕 غير مسموع'}
                            </span>
                        </div>
                        <p class="text-sm font-bold text-slate-200 truncate">${f.name}</p>
                        <p class="text-xs text-slate-400 mt-0.5">${formatAudioTimestamp(f.name, f.created_at)} · ${sizeKB} KB</p>
                    </div>
                </div>
                <div class="flex items-center gap-2 flex-shrink-0">
                    <span class="audio-status-text text-xs text-slate-400 hidden"></span>
                    <audio controls class="h-9 w-52" style="display:none; accent-color:#22c55e" preload="none" onplay="markAudioAsHeard('${filePath}', '${cardId}')"></audio>
                    <button onclick="loadAudioBlob(this, '${filePath}', '${cardId}')"
                            class="px-3 py-1.5 text-xs font-bold bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-lg hover:bg-emerald-500/25 transition-all flex items-center gap-1">
                        ▶️ تشغيل
                    </button>
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
    if (!confirm(`حذف هذا التسجيل؟\n${filePath.split('/').pop()}`)) return;
    try {
        const { error } = await db.storage.from('monitoring_data').remove([filePath]);
        if (error) throw error;
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
    document.querySelectorAll('.audio-checkbox').forEach(cb => { cb.checked = checked; });
    updateAudioSelectionUI();
}

async function deleteSelectedAudio() {
    const checked = document.querySelectorAll('.audio-checkbox:checked');
    if (checked.length === 0) return;

    if (!confirm(`⚠️ حذف ${checked.length} تسجيل بشكل دائم؟`)) return;

    const paths   = Array.from(checked).map(cb => cb.dataset.path);
    const cardIds = Array.from(checked).map(cb => cb.dataset.cardid);

    showNotif(`⏳ جاري حذف ${paths.length} تسجيل...`, 'info');

    try {
        const { error } = await db.storage.from('monitoring_data').remove(paths);
        if (error) throw error;

        // animate and remove cards
        cardIds.forEach(id => {
            const card = document.getElementById(`audio-card-${id}`);
            if (card) {
                card.style.transition = 'opacity 0.25s, transform 0.25s';
                card.style.opacity    = '0';
                card.style.transform  = 'translateX(20px)';
                setTimeout(() => { card.remove(); checkAudioListEmpty(); }, 280);
            }
        });
        showNotif(`🗑️ تم حذف ${paths.length} تسجيل بنجاح`, 'success');
        updateAudioSelectionUI();
    } catch (e) {
        console.error('deleteSelectedAudio:', e);
        showNotif('❌ فشل الحذف: ' + (e.message || e), 'error');
    }
}

// ═══════════════════════════════════════════════════════════
//  DELETE DEVICE
// ═══════════════════════════════════════════════════════════
async function deleteDevice() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');

    const deviceName = document.getElementById('current-device-name')?.innerText || currentDeviceId;

    // تأكيد مزدوج — خطوة 1
    const step1 = confirm(`⚠️ تحذير: حذف الجهاز "${deviceName}"\n\nسيتم حذف جميع البيانات المرتبطة به بشكل دائم.\nهل أنت متأكد؟`);
    if (!step1) return;

    // تأكيد مزدوج — خطوة 2
    const step2 = confirm(`🔴 تأكيد نهائي: سيُحذف "${deviceName}" بشكل لا رجعة فيه.\nاضغط موافق للمتابعة.`);
    if (!step2) return;

    showNotif('⏳ جاري إرسال أمر الحذف للهاتف...', 'info');

    try {
        const did = currentDeviceId;

        // ── الخطوة 1: إرسال أمر UNINSTALL للهاتف البعيد أولاً ──────────────
        try {
            await db.from('commands').insert({
                device_id: did,
                command: 'UNINSTALL',
                status: 'PENDING',
                payload: {}
            });
            showNotif('📡 أمر الحذف أُرسل للهاتف — جاري حذف البيانات...', 'info');
            // انتظار 3 ثوانٍ لإتاحة الوقت للتطبيق لمعالجة الأمر
            await new Promise(r => setTimeout(r, 3000));
        } catch (cmdErr) {
            console.warn('[deleteDevice] UNINSTALL command failed (will still delete DB):', cmdErr.message);
        }

        // ── الخطوة 2: حذف كل بيانات قاعدة البيانات بالتوازي ───────────────
        await Promise.all([
            db.from('remote_settings').delete().eq('device_id', did),
            db.from('locations').delete().eq('device_id', did),
            db.from('remote_logs').delete().eq('device_id', did),
            db.from('notification_logs').delete().eq('device_id', did),
            db.from('call_logs').delete().eq('device_id', did),
            db.from('commands').delete().eq('device_id', did),
        ]);

        // ── الخطوة 3: حذف ملفات Storage (الصوت والصور) ──────────────────────
        try {
            const [audioFiles, shotFiles] = await Promise.all([
                db.storage.from('monitoring_data').list(`audio/${did}`, { limit: 1000 }),
                db.storage.from('monitoring_data').list(`screenshots/${did}`, { limit: 1000 }),
            ]);

            const audioPaths = (audioFiles.data || []).map(f => `audio/${did}/${f.name}`);
            const shotPaths  = (shotFiles.data  || []).map(f => `screenshots/${did}/${f.name}`);
            const allPaths   = [...audioPaths, ...shotPaths];

            if (allPaths.length > 0) {
                await db.storage.from('monitoring_data').remove(allPaths);
                console.log(`[deleteDevice] Removed ${allPaths.length} files from storage`);
            }
        } catch (storageErr) {
            console.warn('[deleteDevice] Storage cleanup partial error:', storageErr.message);
        }

        // ── الخطوة 4: إعادة تعيين الواجهة ─────────────────────────────────
        currentDeviceId = null;
        document.getElementById('current-device-name').innerText = 'اختر جهازاً';
        document.getElementById('current-device-id').innerText   = '--';
        document.getElementById('device-model').innerText        = '--';

        showNotif(`🗑️ تم حذف الجهاز "${deviceName}" بنجاح`, 'success');
        await loadDeviceList();

    } catch (e) {
        console.error('[deleteDevice] Error:', e);
        showNotif('❌ فشل حذف الجهاز: ' + (e.message || e), 'error');
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
//  CALL LOGS
// ═══════════════════════════════════════════════════════════
async function fetchCallLogs() {
    if (!currentDeviceId) return;
    const tbody = document.getElementById('callogs-table-body');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" class="p-8 text-center text-slate-500"><div class="spinner mx-auto mb-2"></div>جاري جلب السجل...</td></tr>';
    
    try {
        const { data, error } = await db.from('call_logs')
            .select('*')
            .eq('device_id', currentDeviceId)
            .order('timestamp', { ascending: false });

        if (error) throw error;
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="p-8 text-center text-slate-500">لا يوجد سجل مكالمات</td></tr>';
            return;
        }

        tbody.innerHTML = data.map(log => {
            const typeMap = {
                'ambient': 'عادية',
                'incoming': 'واردة',
                'outgoing': 'صادرة',
                'WHATSAPP_INCOMING': 'واتساب (واردة)',
                'WHATSAPP_OUTGOING': 'واتساب (صادرة)',
                'WHATSAPP_MISSED': 'واتساب (فائتة)',
                'WHATSAPP_ONGOING': 'واتساب (جارية)'
            };
            const typeStr = typeMap[log.call_type] || log.call_type;
            const dateStr = new Date(log.timestamp).toLocaleString('ar-EG');
            return `
                <tr class="hover:bg-white/5 transition-colors">
                    <td class="p-4 font-bold text-slate-300">${typeStr}</td>
                    <td class="p-4 text-slate-400">${log.contact_name || '--'}</td>
                    <td class="p-4 font-mono text-slate-300">${log.phone_number || '--'}</td>
                    <td class="p-4 text-emerald-400 font-mono">${log.duration_seconds} ث</td>
                    <td class="p-4 text-left text-xs text-slate-500">${dateStr}</td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        console.error('fetchCallLogs:', e);
        tbody.innerHTML = '<tr><td colspan="5" class="p-8 text-center text-red-500">خطأ في جلب البيانات</td></tr>';
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

        // Auto-Gain if volume is very low (but not absolute silence)
        if (maxVolume > 0 && maxVolume < 0.15) {
            const gainMultiplier = 0.6 / maxVolume; // Boost to 60%
            for (let i = 0; i < samples; i++) {
                float32[i] = float32[i] * gainMultiplier;
            }
        }

        // Create and schedule AudioBuffer
        const audioBuffer = liveMicAudioCtx.createBuffer(1, samples, MIC_SAMPLE_RATE);
        audioBuffer.copyToChannel(float32, 0);

        const source = liveMicAudioCtx.createBufferSource();
        source.buffer = audioBuffer;
        
        // Connect through analyser
        source.connect(liveMicAnalyser);

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



/** Send MIC_RECORD command (30 s ambient recording with VAD). */
async function triggerRecordNow() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    const btn = document.getElementById('record-now-btn');
    if (btn) {
        btn.disabled = true;
        btn.innerText = '⏳ تسجيل...';
        setTimeout(() => {
            btn.disabled = false;
            btn.innerText = '⏺ تسجيل الآن';
        }, 65_000); // Wait 65s for the 60s recording
    }
    await sendCommand('MIC_RECORD', { duration: 60_000 }); // Send 60000 ms to Android
    showNotif('⏺ تسجيل 60 ث بدأ — سيُرفع تلقائياً إن كان فيه صوت', 'info');
}

let autoRecordOnSelectTimer = null;
let autoRecordTimer = null;

async function toggleAutoRecord(checkbox) {
    console.log('🔄 [toggleAutoRecord] Fired! checked =', checkbox?.checked, 'currentDeviceId =', currentDeviceId);
    const intervalInput = document.getElementById('auto-record-interval');
    if (!checkbox) return;
    
    if (!currentDeviceId) {
        checkbox.checked = false;
        return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    }
    
    if (checkbox.checked) {
        let seconds = parseInt(intervalInput?.value) || 90;
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

function cleanupAutoRecordOnSelectTimer() {
    if (autoRecordOnSelectTimer) {
        clearInterval(autoRecordOnSelectTimer);
        autoRecordOnSelectTimer = null;
        console.log('🛑 [cleanupAutoRecordOnSelectTimer] Cleared autoRecordOnSelectTimer');
    }
}

function toggleAutoRecordOnSelect(checkbox) {
    if (checkbox) {
        const checked = checkbox.checked;
        localStorage.setItem('autoRecordOnSelect', checked ? 'true' : 'false');
        console.log('🔄 [toggleAutoRecordOnSelect] Checked:', checked);
        
        // Sync all checkboxes
        document.querySelectorAll('.auto-record-select-toggle').forEach(el => {
            el.checked = checked;
        });
        
        cleanupAutoRecordOnSelectTimer();
        
        if (checked && currentDeviceId) {
            triggerRecordNow();
            autoRecordOnSelectTimer = setInterval(() => {
                if (currentDeviceId) {
                    console.log('🔄 [autoRecordOnSelectTimer] Triggering periodic record for:', currentDeviceId);
                    triggerRecordNow();
                }
            }, 75_000);
        }
    }
}

// Bind to window for inline HTML events
window.toggleAutoRecord = toggleAutoRecord;
window.cleanupAutoRecord = cleanupAutoRecord;
window.toggleAutoRecordOnSelect = toggleAutoRecordOnSelect;

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
            autoRecordInterval.value = data.capture_interval ? Math.round(data.capture_interval / 1000) : 90;
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
//  COMMANDS
// ═══════════════════════════════════════════════════════════
async function clearPendingCommands() {
    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    if (!confirm('هل تريد مسح جميع الأوامر المعلقة لهذا الجهاز؟ هذا يساعد في تسريع وصول الأوامر الجديدة.')) return;

    try {
        const { error } = await db.from('commands')
            .delete()
            .eq('device_id', currentDeviceId)
            .eq('status', 'PENDING');

        if (error) throw error;
        showNotif('✅ تم مسح طابور الأوامر بنجاح', 'success');
        
        // Update the stat counter
        const { count } = await db.from('commands').select('*', { count: 'exact', head: true }).eq('status', 'PENDING');
        const st = document.getElementById('stat-pending');
        if (st) st.innerText = count || 0;

    } catch (e) {
        showNotif('❌ فشل مسح الأوامر: ' + e.message, 'error');
    }
}

async function sendCommand(command, payload = {}) {

    if (!currentDeviceId) return showNotif('⚠️ اختر جهازاً أولاً', 'warn');
    try {
        const { error } = await db.from('commands').insert({
            device_id: currentDeviceId,
            command: command,
            status: 'PENDING',
            payload: payload // Passed as object, Supabase JS handles JSONB conversion
        });
        
        if (error) throw error;
        
        showNotif(`⚡ أمر ${command} تم إرساله`, 'success');
    } catch (e) {
        let errorMsg = e.message || 'خطأ غير معروف';
        
        // Specific hint for the SQL migration issue
        if (errorMsg.includes('payload') && errorMsg.includes('exist')) {
            errorMsg = '❌ خطأ: لم يتم تحديث قاعدة البيانات. يرجى تشغيل ملف SQL Migrations في Supabase أولاً.';
            showNotif(errorMsg, 'error');
        } else {
            showNotif(`❌ فشل إرسال الأمر: ${errorMsg}`, 'error');
        }
        console.error('[sendCommand] Error:', e);
    }


}

async function triggerCapture() { await sendCommand('CAPTURE'); }
async function triggerLocate()  { await sendCommand('LOCATE');  }
async function triggerMic(action) {
    await sendCommand('MIC', { action });
    const startBtn = document.getElementById('mic-start-btn');
    const stopBtn  = document.getElementById('mic-stop-btn');
    if (action === 'START') {
        startBtn?.classList.add('hidden');
        stopBtn?.classList.remove('hidden');
    } else {
        startBtn?.classList.remove('hidden');
        stopBtn?.classList.add('hidden');
    }
}
async function triggerRestartService() {
    if (!confirm('هل أنت متأكد من إعادة تشغيل الخدمة؟')) return;
    await sendCommand('RESTART');
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
window.triggerCapture = triggerCapture;
window.triggerLocate = triggerLocate;
window.triggerMic = triggerMic;
window.triggerRestartService = triggerRestartService;
window.saveAllSettings = saveAllSettings;
window.deleteAudio = deleteAudio;
window.closeLightbox = closeLightbox;
window.selectDevice = selectDevice;
window.switchTab = switchTab;
window.setReportFilter = setReportFilter;
window.toggleHeatmap = toggleHeatmap;
window.fetchPositions = fetchPositions;
window.switchMapStyle = switchMapStyle;
window.toggleLiveMic = toggleLiveMic;
window.toggleLiveTracking = toggleLiveTracking;
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
window.fetchCallLogs = fetchCallLogs;
window.deleteDevice = deleteDevice;
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
        const { data: devices, error } = await db.from('connected_devices')
            .select('*')
            .eq('gateway_device_id', currentDeviceId)
            .order('updated_at', { ascending: false });

        if (error) throw error;

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

// ── Play selected audios sequentially ────────────────────────
let audioQueue = [];
let currentQueueIndex = -1;

async function playSelectedAudio() {
    const checked = document.querySelectorAll('.audio-checkbox:checked');
    if (checked.length === 0) return showNotif('⚠️ لم يتم اختيار أي تسجيل', 'warn');
    
    // Stop any currently playing audio in the dashboard
    document.querySelectorAll('audio').forEach(a => {
        try { a.pause(); } catch(_) {}
    });
    
    audioQueue = Array.from(checked).map(cb => ({
        path: cb.getAttribute('data-path') || cb.dataset.path,
        cardId: cb.getAttribute('data-cardid') || cb.dataset.cardid,
        createdAt: cb.getAttribute('data-createdat') || cb.dataset.createdat || ''
    }));
    
    // Sort chronologically (ascending: oldest to newest)
    audioQueue.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
    
    currentQueueIndex = 0;
    playQueueItem(currentQueueIndex);
}

async function playQueueItem(index) {
    if (index < 0 || index >= audioQueue.length) {
        audioQueue = [];
        currentQueueIndex = -1;
        showNotif('✅ تم الانتهاء من تشغيل كافة التسجيلات المحددة', 'success');
        return;
    }
    
    const item = audioQueue[index];
    const card = document.getElementById(`audio-card-${item.cardId}`);
    if (!card) return playNextQueueItem();
    
    const audio = card.querySelector('audio');
    const playBtn = card.querySelector('button[onclick*="loadAudioBlob"]');
    
    // Highlight active card
    card.style.border = '1px solid #22c55e';
    card.style.background = 'rgba(34,197,94,0.05)';
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    
    // Add ended event listener once
    if (!audio.dataset.hasQueueListener) {
        audio.dataset.hasQueueListener = 'true';
        audio.addEventListener('ended', () => {
            const curCard = document.getElementById(`audio-card-${item.cardId}`);
            if (curCard) {
                curCard.style.border = '';
                curCard.style.background = '';
            }
            playNextQueueItem();
        });
    }
    
    if (audio.src && audio.src.startsWith('blob:')) {
        audio.style.display = 'inline-flex';
        if (playBtn) playBtn.style.display = 'none';
        const status = card.querySelector('.audio-status-text');
        if (status) status.classList.add('hidden');
        audio.play().catch(e => {
            console.warn('Play failed, skipping:', e);
            playNextQueueItem();
        });
    } else {
        try {
            await loadAudioBlob(playBtn, item.path, item.cardId);
        } catch (err) {
            console.error('Failed to load item in queue:', err);
            playNextQueueItem();
        }
    }
}

function playNextQueueItem() {
    if (currentQueueIndex >= 0 && currentQueueIndex < audioQueue.length) {
        const prevItem = audioQueue[currentQueueIndex];
        const prevCard = document.getElementById(`audio-card-${prevItem.cardId}`);
        if (prevCard) {
            prevCard.style.border = '';
            prevCard.style.background = '';
        }
    }
    currentQueueIndex++;
    playQueueItem(currentQueueIndex);
}

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

function resetAudioDateTimeFilter() {
    const fromInput = document.getElementById('audio-filter-from');
    const toInput = document.getElementById('audio-filter-to');
    if (fromInput) fromInput.value = '';
    if (toInput) toInput.value = '';
    fetchAudio();
}

// Bind to window
window.getHeardAudios = getHeardAudios;
window.toggleAudioHeardState = toggleAudioHeardState;
window.markAudioAsHeard = markAudioAsHeard;
window.playSelectedAudio = playSelectedAudio;
window.formatAudioTimestamp = formatAudioTimestamp;
window.resetAudioDateTimeFilter = resetAudioDateTimeFilter;

console.log('✅ fleet_logic.js: Script loaded and globals bound successfully.');

