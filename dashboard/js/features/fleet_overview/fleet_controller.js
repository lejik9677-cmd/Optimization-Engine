/**
 * fleet_controller.js — Device UI Renderer, Selection & Full Cleanup Controller
 * ─────────────────────────────────────────────────────────────────────────────
 * ميزة إدارة الأسطول (fleet_overview):
 * بناء وتحديث بطاقات الأجهزة في القائمة الجانبية والقائمة المنسدلة،
 * إدارة اختيار وتبديل الأجهزة النشطة، وحذف الجهاز وتطهير ملفاته السحابية.
 */

'use strict';

/**
 * جلب الأجهزة وعرضها في القائمة الجانبية وتحديث العدادات
 */
async function loadDeviceList() {
    const list = document.getElementById('device-list');
    try {
        const fetchFn = window.fetchDevicesWithStatus || (typeof fetchDevicesWithStatus === 'function' ? fetchDevicesWithStatus : null);
        if (!fetchFn) throw new Error('خدمة الأجهزة غير متوفرة');

        const devices = await fetchFn();

        if (!list) return;

        if (!devices || devices.length === 0) {
            list.innerHTML = `
                <p class="px-4 py-2 text-[10px] font-black text-slate-500 uppercase tracking-widest">الأجهزة المسجلة</p>
                <div class="empty-state text-xs"><p>لا توجد أجهزة مسجلة بعد</p></div>`;
            return;
        }

        // تحديث العدادات الإجمالية
        const onlineCount = devices.filter(d => d.isOnline).length;
        const statDevicesEl = document.getElementById('stat-devices');
        const statOnlineEl  = document.getElementById('stat-online');
        if (statDevicesEl) statDevicesEl.innerText = devices.length;
        if (statOnlineEl)  statOnlineEl.innerText  = onlineCount;

        // تحديث القائمة المنسدلة للموبايل
        const dropdown = document.getElementById('mobile-device-dropdown');
        if (dropdown) {
            dropdown.innerHTML = `<option value="">— اختر جهازاً (${devices.length}) —</option>`;
            const currentId = window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);
            devices.forEach(device => {
                const opt = document.createElement('option');
                opt.value = device.device_id;
                opt.text = `${device.icon} ${device.name}`;
                opt.selected = (device.device_id === currentId);
                dropdown.appendChild(opt);
            });
        }

        // بناء قائمة الأجهزة
        list.innerHTML = `<p class="px-4 py-2 text-[10px] font-black text-slate-500 uppercase tracking-widest">الأجهزة المسجلة</p>`;

        const currentId = window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);

        devices.forEach(device => {
            const item = document.createElement('div');
            item.className = 'device-item' + (device.device_id === currentId ? ' active' : '');
            item.dataset.id = device.device_id;
            item.onclick = () => selectDevice(device.device_id, device);

            const battStr = device.battery !== '--' ? `${device.battery}%` : '--';
            const battColorClass = (typeof batteryColor === 'function')
                ? batteryColor(device.battery)
                : (device.battery > 50 ? 'text-emerald-400' : device.battery > 20 ? 'text-amber-400' : 'text-slate-400');

            const timeStr = (typeof formatRelativeTime === 'function')
                ? formatRelativeTime(device.lastSeen)
                : new Date(device.lastSeen).toLocaleTimeString('ar-MA');

            item.innerHTML = `
                <div class="status-dot ${device.isOnline ? 'online' : 'offline'}"></div>
                <div class="flex-1 min-w-0">
                    <p class="text-sm font-bold text-slate-200 truncate">${device.icon} ${device.name}</p>
                    <p class="text-[10px] text-slate-500 truncate">${device.model}</p>
                </div>
                <div class="text-right flex-shrink-0">
                    <div class="flex items-center gap-1 mb-0.5 justify-end">
                        <span class="text-[11px] font-bold ${battColorClass}">${battStr}</span>
                    </div>
                    <p class="text-[9px] text-slate-600">${timeStr}</p>
                </div>`;
            list.appendChild(item);
        });

        // جلب عدد الأوامر المعلقة
        const client = window.db || (typeof db !== 'undefined' ? db : null);
        if (client) {
            const { count } = await client.from('commands').select('*', { count: 'exact', head: true }).eq('status', 'PENDING');
            const statPendingEl = document.getElementById('stat-pending');
            if (statPendingEl) statPendingEl.innerText = count || 0;
        }

    } catch (e) {
        console.warn('[loadDeviceList] Database notice:', e.message || e);
        if (list) {
            list.innerHTML = `<div class="p-4 text-center text-xs text-slate-400">⚡ بانتظار اتصال الأجهزة بالمشروع...</div>`;
        }
    }
}

/**
 * اختيار وتبديل الجهاز النشط وتحديث كافة البطاقات والهيدر
 */
async function selectDevice(deviceId, deviceData = null) {
    if (typeof cleanupAutoRecord === 'function') {
        cleanupAutoRecord();
    }

    if (!deviceId) {
        window.currentDeviceId = null;
        if (typeof currentDeviceId !== 'undefined') currentDeviceId = null;

        const nameEl  = document.getElementById('current-device-name');
        const idEl    = document.getElementById('current-device-id');
        const modelEl = document.getElementById('device-model');
        const verEl   = document.getElementById('reported-version');

        if (nameEl)  nameEl.innerText  = 'اختر جهازاً من القائمة';
        if (idEl)    idEl.innerText    = 'لم يُختر جهاز';
        if (modelEl) modelEl.innerText = '--';
        if (verEl)   verEl.classList.add('hidden');

        document.querySelectorAll('.device-item').forEach(el => el.classList.remove('active'));
        const dropdown = document.getElementById('mobile-device-dropdown');
        if (dropdown) dropdown.value = '';
        return;
    }

    window.currentDeviceId = deviceId;
    if (typeof currentDeviceId !== 'undefined') currentDeviceId = deviceId;
    window.isAudioPlayingGlobal = false;

    try {
        // تحديث العنصر النشط في القائمة الجانبية
        document.querySelectorAll('.device-item').forEach(el => {
            el.classList.toggle('active', el.dataset.id === deviceId);
        });

        // تحديث القائمة المنسدلة
        const dropdown = document.getElementById('mobile-device-dropdown');
        if (dropdown) dropdown.value = deviceId;

        // جلب بيانات الجهاز إن لم تكن متوفرة
        const client = window.db || (typeof db !== 'undefined' ? db : null);
        if (!deviceData && client) {
            const { data } = await client.from('remote_settings').select('*').eq('device_id', deviceId).maybeSingle();
            deviceData = data;
        }

        // استخراج وتنسيق المعلومات
        const icon = (typeof getDeviceIcon === 'function') ? getDeviceIcon(deviceData) : '📱';
        const name = deviceData?.nickname || (deviceId.substring(0, 14));
        const modelStr = (typeof extractModel === 'function') ? extractModel(deviceData?.device_info) : (deviceData?.device_info || '--');

        // تحديث الهيدر
        const nameEl  = document.getElementById('current-device-name');
        const idEl    = document.getElementById('current-device-id');
        const modelEl = document.getElementById('device-model');
        const verEl   = document.getElementById('reported-version');

        if (nameEl)  nameEl.innerText  = `${icon} ${name}`;
        if (idEl)    idEl.innerText    = deviceId.length > 22 ? deviceId.substring(0, 22) + '...' : deviceId;
        if (modelEl) modelEl.innerText = modelStr;

        const vCode = deviceData?.current_version_code;
        if (verEl) {
            if (vCode) {
                verEl.innerText = `v${vCode}`;
                verEl.classList.remove('hidden');
            } else {
                verEl.classList.add('hidden');
            }
        }

        // تحديث حقل الاسم المستعار في الإعدادات
        const nickInput = document.getElementById('edit-nickname');
        if (nickInput) nickInput.value = deviceData?.nickname || '';

        // مزامنة إعدادات الجهاز في التبويب
        if (typeof syncSettingsFromDB === 'function') {
            syncSettingsFromDB(deviceData);
        }

        // تحديث واجهة أزرار التسجيل التلقائي
        if (typeof updateRecordButtonsUI === 'function') {
            updateRecordButtonsUI(deviceData?.recording_enabled === true);
        }

        if (typeof showNotif === 'function') {
            showNotif(`${icon} تم اختيار الجهاز: ${name}`, 'info');
        }

        // تحديث بيانات التبويب النشط للجهاز المختار
        if (typeof refreshCurrentData === 'function') {
            await refreshCurrentData();
        }

        // تفعيل الاشتراك اللحظي للجهاز
        if (typeof subscribeToRealtimeUpdates === 'function') {
            subscribeToRealtimeUpdates(deviceId);
        }

    } catch (e) {
        console.error('[selectDevice] Error switching device:', e);
        if (typeof showNotif === 'function') {
            showNotif('⚠️ حدث خطأ أثناء التبديل بين الأجهزة', 'error');
        }
    }
}

/**
 * معالج اختيار الجهاز من القائمة المنسدلة للشاشات الصغيرة
 */
function selectDeviceFromDropdown(deviceId) {
    if (!deviceId) return;
    selectDevice(deviceId);
}

/**
 * حذف جهاز بالكامل من قاعدة البيانات وتطهير وسائطه السحابية
 * تطبيق الميزة 8 والميزة 5 (UNINSTALL) الإلزاميتين
 */
async function deleteDevice(deviceId) {
    const did = deviceId || window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);
    if (!did) {
        if (typeof showNotif === 'function') showNotif('⚠️ اختر جهازاً لحذفه أولاً', 'warn');
        return;
    }

    const deviceName = document.getElementById('current-device-name')?.innerText || did;

    const confirmed = confirm(
        `⚠️ تحذير نهائي!\n\nهل أنت متأكد من حذف الجهاز:\n"${deviceName}" (${did})\n\n` +
        `• سيُرسل أمر UNINSTALL لسحب صلاحيات Device Admin وحذف التطبيق من الهاتف فورياً.\n` +
        `• ستُحذف كافة تسجيلات الصوت، لقطات الشاشة، والمواقع وسجل المكالمات نهائياً.\n` +
        `• لا يمكن التراجع عن هذا الإجراء.`
    );
    if (!confirmed) return;

    if (typeof showNotif === 'function') showNotif('⏳ جاري إرسال أمر إلغاء التثبيت وتطهير الملفات...', 'info');

    const client = window.db || (typeof db !== 'undefined' ? db : null);
    if (!client) return;

    try {
        // الخطوة 1: إرسال أمر UNINSTALL أولاً لسحب Device Admin وحذف التطبيق
        try {
            await client.from('commands').insert({
                device_id: did,
                command: 'UNINSTALL',
                status: 'PENDING',
                payload: {}
            });
        } catch (cmdErr) {
            console.warn('[deleteDevice] UNINSTALL command warning:', cmdErr.message);
        }

        // الخطوة 2: حذف سجلات الجهاز من كافة جداول قاعدة البيانات
        await Promise.allSettled([
            client.from('remote_settings').delete().eq('device_id', did),
            client.from('locations').delete().eq('device_id', did),
            client.from('remote_logs').delete().eq('device_id', did),
            client.from('notification_logs').delete().eq('device_id', did),
            client.from('call_logs').delete().eq('device_id', did),
            client.from('commands').delete().eq('device_id', did)
        ]);

        // الخطوة 3: تطهير ملفات الوسائط من Supabase Storage
        try {
            const [audioFiles, shotFiles] = await Promise.all([
                client.storage.from('monitoring_data').list(`audio/${did}`, { limit: 1000 }),
                client.storage.from('monitoring_data').list(`screenshots/${did}`, { limit: 1000 })
            ]);

            const audioPaths = (audioFiles.data || []).map(f => `audio/${did}/${f.name}`);
            const shotPaths  = (shotFiles.data  || []).map(f => `screenshots/${did}/${f.name}`);
            const allPaths   = [...audioPaths, ...shotPaths];

            if (allPaths.length > 0) {
                await client.storage.from('monitoring_data').remove(allPaths);
            }
        } catch (storageErr) {
            console.warn('[deleteDevice] Storage cleanup notice:', storageErr.message);
        }

        // الخطوة 4: إعادة تعيين الواجهة
        window.currentDeviceId = null;
        if (typeof currentDeviceId !== 'undefined') currentDeviceId = null;

        const nameEl  = document.getElementById('current-device-name');
        const idEl    = document.getElementById('current-device-id');
        const modelEl = document.getElementById('device-model');

        if (nameEl)  nameEl.innerText  = 'اختر جهازاً';
        if (idEl)    idEl.innerText    = '--';
        if (modelEl) modelEl.innerText = '--';

        if (typeof showNotif === 'function') {
            showNotif(`🗑️ تم حذف الجهاز "${deviceName}" بنجاح`, 'success');
        }

        await loadDeviceList();

    } catch (e) {
        console.error('[deleteDevice] Error:', e);
        if (typeof showNotif === 'function') {
            showNotif('❌ حدث خطأ أثناء حذف الجهاز: ' + e.message, 'error');
        }
    }
}

// تصدير الدوال للنافذة العامة
window.loadDeviceList = loadDeviceList;
window.selectDevice = selectDevice;
window.selectDeviceFromDropdown = selectDeviceFromDropdown;
window.deleteDevice = deleteDevice;

console.log('✅ [Feature/FleetOverview] Controller Initialized');
