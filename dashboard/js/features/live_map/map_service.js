/**
 * map_service.js — Leaflet Map Engine, Layers, Styling & Coordinate Filtering
 * ─────────────────────────────────────────────────────────────────────────────
 * ميزة الخريطة والتتبع المباشر (live_map):
 * إدارة كائن الخريطة، الطبقات، تصفية إحداثيات GPS، مطابقة الطرق،
 * ورسم المسارات المتوهجة والعلامات مع التحصين الصارم ضد إحداثيات NaN.
 */

'use strict';

// ── إعدادات أنماط الخرائط (Tile Layers) ──────────────────────────────────────
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

let mapInstance    = null;
let _baseTileLayer = null;
let heatLayer      = null;
let routeLayer     = null;
let routeMarkers   = [];
let heatmapVisible = false;
let mapStyleIndex  = 0;
let allLocations   = [];

/**
 * تهيئة خريطة Leaflet وضبط التحكم والأحداث
 */
function initMap() {
    if (mapInstance) {
        setTimeout(() => {
            try { mapInstance.invalidateSize(); } catch (e) {}
        }, 100);
        return;
    }

    setTimeout(() => {
        const mapEl = document.getElementById('map');
        if (!mapEl || typeof L === 'undefined') return;

        mapInstance = L.map('map', {
            zoomControl: false,
            attributionControl: true
        }).setView([32.0, -6.5], 6);

        window.mapInstance = mapInstance;

        L.control.zoom({ position: 'topleft' }).addTo(mapInstance);

        // إضافة الطبقة الافتراضية
        const style = MAP_STYLES[mapStyleIndex];
        _baseTileLayer = L.tileLayer(style.url, {
            attribution: style.attr,
            maxZoom: 19
        }).addTo(mapInstance);

        // إحداثيات الماوس على الخريطة
        mapInstance.on('mousemove', e => {
            const d = document.getElementById('coords-display');
            if (d && e.latlng && !isNaN(e.latlng.lat) && !isNaN(e.latlng.lng)) {
                d.classList.remove('hidden');
                d.innerText = `${e.latlng.lat.toFixed(5)}, ${e.latlng.lng.toFixed(5)}`;
            }
        });

        const activeId = window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);
        if (activeId && typeof window.fetchPositions === 'function') {
            window.fetchPositions(activeId);
        }
    }, 100);
}

/**
 * تبديل نمط الخريطة (شوارع داكنة / أقمار صناعية / هجين)
 */
function switchMapStyle() {
    if (!mapInstance) return;
    mapStyleIndex = (mapStyleIndex + 1) % MAP_STYLES.length;
    const style = MAP_STYLES[mapStyleIndex];
    if (_baseTileLayer) mapInstance.removeLayer(_baseTileLayer);
    _baseTileLayer = L.tileLayer(style.url, {
        attribution: style.attr,
        maxZoom: 19
    }).addTo(mapInstance);
    _baseTileLayer.bringToBack();

    const btn = document.getElementById('map-style-btn');
    if (btn) btn.innerText = MAP_STYLES[(mapStyleIndex + 1) % MAP_STYLES.length].name;
}

/**
 * حساب المسافة بين نقطتين بالكيلومتر عبر معادلة Haversine
 */
function haversineKm(a, b) {
    if (!a || !b) return 0;
    const R = 6371;
    const dLat = (b[0] - a[0]) * Math.PI / 180;
    const dLon = (b[1] - a[1]) * Math.PI / 180;
    const s = Math.sin(dLat / 2) ** 2 +
              Math.cos(a[0] * Math.PI / 180) * Math.cos(b[0] * Math.PI / 180) *
              Math.sin(dLon / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(s), Math.sqrt(1 - s));
}

/**
 * التدرج اللوني بين لونين Hex لرسم مسار نيون سلس
 */
function lerpColor(a, b, t) {
    const ah = parseInt(a.slice(1), 16);
    const bh = parseInt(b.slice(1), 16);
    const ar = (ah >> 16) & 0xff, ag = (ah >> 8) & 0xff, ab = ah & 0xff;
    const br = (bh >> 16) & 0xff, bg = (bh >> 8) & 0xff, bb = bh & 0xff;
    const rr = Math.round(ar + (br - ar) * t);
    const rg = Math.round(ag + (bg - ag) * t);
    const rb = Math.round(ab + (bb - ab) * t);
    return `#${((1 << 24) | (rr << 16) | (rg << 8) | rb).toString(16).slice(1)}`;
}

/**
 * مسح كافة الطبقات والعلامات السابقة من الخريطة
 */
function clearMapLayers() {
    if (!mapInstance) return;
    if (routeLayer) {
        try { mapInstance.removeLayer(routeLayer); } catch (e) {}
        routeLayer = null;
    }
    routeMarkers.forEach(m => {
        try { mapInstance.removeLayer(m); } catch (e) {}
    });
    routeMarkers = [];
    if (heatLayer) {
        try { mapInstance.removeLayer(heatLayer); } catch (e) {}
        heatLayer = null;
    }
}

/**
 * إشعار Toast خاص بأحداث الخريطة
 */
function showMapToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = 'fixed bottom-24 left-1/2 transform -translate-x-1/2 px-6 py-3 rounded-2xl text-xs font-black text-white shadow-2xl z-[9999] transition-all duration-300 opacity-0 translate-y-2 flex items-center gap-2';

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

/**
 * تنقية وفحص إحداثيات GPS وحمايتها من أخطاء Leaflet (منع قيم NaN و Null)
 */
function filterGPSPoints(locs) {
    if (!locs || locs.length === 0) return [];

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

    // الترتيب زمنياً تصاعدياً (من الأقدم للأحدث)
    const chronological = [...cleanLocs].reverse();

    // 1. استبعاد النقاط ذات الدقة المنخفضة جداً (> 100 متر) مع الاحتفاظ بنقطتين على الأقل
    let filtered = chronological.filter(l => !l.accuracy || l.accuracy <= 100);
    if (filtered.length < 2) {
        filtered = chronological;
    }

    // 2. إزالة النقاط المتكررة جداً (أقل من 5 أمتار) لمنع تشويش الـ GPS عند سكون الجهاز
    const result = [filtered[0]];
    for (let i = 1; i < filtered.length; i++) {
        const prev = result[result.length - 1];
        const curr = filtered[i];
        const dist = haversineKm([prev.latitude, prev.longitude], [curr.latitude, curr.longitude]) * 1000;
        if (dist >= 5) {
            result.push(curr);
        }
    }

    // التأكد من الاحتفاظ بالنقطة الأحدث دائماً
    const lastFiltered = filtered[filtered.length - 1];
    const lastResult = result[result.length - 1];
    if (lastResult.latitude !== lastFiltered.latitude || lastResult.longitude !== lastFiltered.longitude) {
        result.push(lastFiltered);
    }

    return result;
}

/**
 * مطابقة النقاط على شبكة الطرق عبر خوادم OSRM مع التراجع للمسار المباشر
 */
async function snapPointsToRoads(locsWithTime) {
    const pts = Array.isArray(locsWithTime[0])
        ? locsWithTime
        : locsWithTime.map(l => [l.latitude, l.longitude]);

    if (pts.length < 2) return pts;

    // تخطي OSRM إذا كانت النقاط أقل من 3 أو المسافة الكلية < 50 متر
    if (pts.length < 3) {
        return pts;
    }

    const totalDistM = pts.reduce((sum, p, i) => {
        if (i === 0) return 0;
        return sum + haversineKm(pts[i - 1], p) * 1000;
    }, 0);

    if (totalDistM < 50) {
        return pts;
    }

    const CHUNK_SIZE = 25;
    const OVERLAP    = 3;
    const chunks     = [];

    for (let i = 0; i < pts.length; i += (CHUNK_SIZE - OVERLAP)) {
        const chunk = locsWithTime.slice
            ? locsWithTime.slice(i, i + CHUNK_SIZE)
            : pts.slice(i, i + CHUNK_SIZE);
        if (chunk.length < 2) break;
        chunks.push(chunk);
        if (i + CHUNK_SIZE >= pts.length) break;
    }

    const OSRM_SERVERS = [
        'https://router.project-osrm.org',
        'https://routing.openstreetmap.de'
    ];

    const snappedPaths = [];

    for (let ci = 0; ci < chunks.length; ci++) {
        const chunk = chunks[ci];
        const chunkPts = Array.isArray(chunk[0])
            ? chunk
            : chunk.map(l => [l.latitude, l.longitude]);

        const coordStr = chunkPts.map(p => `${p[1]},${p[0]}`).join(';');
        let snappedChunk = null;

        for (const server of OSRM_SERVERS) {
            try {
                const routeUrl = `${server}/route/v1/driving/${coordStr}?overview=full&geometries=geojson`;
                const res = await fetch(routeUrl, { signal: AbortSignal.timeout(8000) });
                if (res.ok) {
                    const json = await res.json();
                    if (json.code === 'Ok' && json.routes && json.routes.length > 0) {
                        snappedChunk = json.routes[0].geometry.coordinates.map(c => [c[1], c[0]]);
                        break;
                    }
                }
            } catch (err) {
                // Fallback صامت عند انتهاء المهلة أو خطأ الشبكة
            }
        }

        snappedPaths.push(snappedChunk || chunkPts);

        if (ci < chunks.length - 1) {
            await new Promise(r => setTimeout(r, 250));
        }
    }

    if (snappedPaths.length === 0) return pts;

    // دمج المقاطع بسلاسة
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

/**
 * رسم المسار والعلامات والأسهم التوجيهية على خريطة Leaflet
 */
function renderRouteOnMap(filteredLocs, snappedPts) {
    if (!mapInstance || !filteredLocs || filteredLocs.length === 0) return;

    clearMapLayers();

    const n = filteredLocs.length;
    const nSnapped = snappedPts.length;

    const COLOR_OLD = '#6366f1'; // نيون بنفسجي (الأقدم)
    const COLOR_NEW = '#10b981'; // زمردي مشرق (الأحدث)

    // 1. رسم خطوط المسار مع طبقة توهج عصرية
    for (let i = 0; i < nSnapped - 1; i++) {
        const t = nSnapped > 1 ? i / (nSnapped - 1) : 1;
        const col = lerpColor(COLOR_OLD, COLOR_NEW, t);

        const underlay = L.polyline([snappedPts[i], snappedPts[i + 1]], {
            color: col, weight: 10, opacity: 0.25,
            lineJoin: 'round', lineCap: 'round'
        }).addTo(mapInstance);
        routeMarkers.push(underlay);

        const seg = L.polyline([snappedPts[i], snappedPts[i + 1]], {
            color: col, weight: 4.5, opacity: 0.9,
            lineJoin: 'round', lineCap: 'round'
        }).addTo(mapInstance);
        routeMarkers.push(seg);
    }

    // 2. أسهم الاتجاهات إن وُجدت PolylineDecorator
    if (nSnapped > 1 && L.polylineDecorator) {
        try {
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
        } catch (e) {
            console.warn('[renderRouteOnMap] polylineDecorator error:', e);
        }
    }

    // 3. علامات النقاط البينية مع التلميحات
    const step = Math.max(1, Math.floor(n / 15));
    for (let i = 1; i < n - 1; i += step) {
        const loc = filteredLocs[i];
        const t = i / (n - 1);
        const col = lerpColor(COLOR_OLD, COLOR_NEW, t);
        const nextLoc = filteredLocs[Math.min(i + 1, n - 1)];
        const dist = haversineKm([loc.latitude, loc.longitude], [nextLoc.latitude, nextLoc.longitude]);
        const dtSec = (new Date(nextLoc.timestamp) - new Date(loc.timestamp)) / 1000;
        const speedKmh = dtSec > 0 ? (dist / dtSec * 3600).toFixed(1) : '?';
        const timeAgo = (typeof formatRelativeTime === 'function')
            ? formatRelativeTime(loc.timestamp)
            : new Date(loc.timestamp).toLocaleTimeString('ar-MA');

        const dot = L.circleMarker([loc.latitude, loc.longitude], {
            radius: 5, fillColor: col,
            color: '#1a1a2e', weight: 1.5,
            opacity: 1, fillOpacity: 0.9
        }).bindTooltip(
            `<div style="font-family:sans-serif;font-size:12px;direction:rtl;">
                <b>${timeAgo}</b><br>
                📍 ${loc.latitude.toFixed(5)}, ${loc.longitude.toFixed(5)}<br>
                🎯 دقة: ${loc.accuracy ? loc.accuracy + ' م' : '?'}<br>
                ⚡ ${loc.battery_level != null ? loc.battery_level + '%' : '?'} بطارية<br>
                🚀 السرعة: ~${speedKmh} كم/س
            </div>`,
            { sticky: true, direction: 'top', className: 'map-tooltip' }
        ).addTo(mapInstance);
        routeMarkers.push(dot);
    }

    // 4. علامة نقطة البداية (START Marker)
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

    // 5. علامة الموقع الحالي والنبض الحي (CURRENT Marker)
    const latest = filteredLocs[filteredLocs.length - 1];
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

    // 6. تحصين الكاميرا (Map Camera Hardening — Anti-Crash Guard)
    safeFlyTo(latest.latitude, latest.longitude, 16);

    // 7. تحديث الإحصائيات ومعلومات الموقع
    updateMapStats(filteredLocs, snappedPts);
    updateLastLocationInfo(latest);

    if (heatmapVisible) updateHeatmap(filteredLocs);
}

/**
 * تحريك الكاميرا الآمن لمنع تعطل Leaflet عند قيم NaN
 */
function safeFlyTo(lat, lng, zoom = 16) {
    if (!mapInstance) return;
    const targetLat = parseFloat(lat);
    const targetLng = parseFloat(lng);

    if (isNaN(targetLat) || !isFinite(targetLat) || isNaN(targetLng) || !isFinite(targetLng)) {
        console.warn('[safeFlyTo] Invalid coordinates received:', { lat, lng });
        return;
    }

    try {
        mapInstance.invalidateSize();
        const currentCenter = mapInstance.getCenter();
        if (currentCenter && !isNaN(currentCenter.lat) && !isNaN(currentCenter.lng)) {
            mapInstance.flyTo([targetLat, targetLng], zoom, { duration: 1.8, easeLinearity: 0.25 });
        } else {
            mapInstance.setView([targetLat, targetLng], zoom);
        }
    } catch (e) {
        console.warn('[safeFlyTo] flyTo failed, falling back to setView:', e);
        try {
            mapInstance.setView([targetLat, targetLng], zoom);
        } catch (err) {
            console.error('[safeFlyTo] setView fallback failed:', err);
        }
    }
}

/**
 * عرض معلومات آخر موقع في لوحة التتبع
 */
function updateLastLocationInfo(latest) {
    const infoEl = document.getElementById('last-location-info');
    if (!infoEl || !latest) return;

    const timeAgo = (typeof formatRelativeTime === 'function')
        ? formatRelativeTime(latest.timestamp)
        : new Date(latest.timestamp).toLocaleTimeString('ar-MA');
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

/**
 * تحديث شريط إحصائيات المسار (النقاط، المسافة، الزمن، والسرعة)
 */
function updateMapStats(filteredLocs, snappedPts) {
    const statsEl = document.getElementById('map-stats');
    if (!statsEl || !filteredLocs || filteredLocs.length === 0) return;

    const n = filteredLocs.length;
    let totalKm = 0;
    const nSnapped = (snappedPts || []).length;

    for (let i = 0; i < nSnapped - 1; i++) {
        totalKm += haversineKm(snappedPts[i], snappedPts[i + 1]);
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

/**
 * تبديل حالة خريطة الكثافة الحرارية Heatmap
 */
function toggleHeatmap() {
    heatmapVisible = !heatmapVisible;
    const btn = document.getElementById('heatmap-toggle');
    if (!mapInstance) return;

    if (!heatmapVisible && heatLayer) {
        mapInstance.removeLayer(heatLayer);
        heatLayer = null;
        if (btn) {
            btn.innerText = '🌡️ Heatmap تفعيل';
            btn.classList.remove('btn-amber');
            btn.classList.add('btn-primary');
        }
    } else {
        if (btn) {
            btn.innerText = '🌡️ إخفاء Heatmap';
            btn.classList.remove('btn-primary');
            btn.classList.add('btn-amber');
        }
        if (allLocations.length > 0) {
            updateHeatmap(allLocations);
        } else if (typeof window.fetchPositions === 'function') {
            window.fetchPositions();
        }
    }
}

/**
 * تحديث طبقة Heatmap لنقاط المواقع
 */
function updateHeatmap(locs) {
    if (!mapInstance || typeof L === 'undefined' || typeof L.heatLayer !== 'function') return;
    if (heatLayer) mapInstance.removeLayer(heatLayer);

    const points = (locs || []).map(l => [l.latitude, l.longitude, 0.6]);
    heatLayer = L.heatLayer(points, {
        radius: 28, blur: 22, maxZoom: 18,
        gradient: { 0.2: '#1d4ed8', 0.45: '#7c3aed', 0.7: '#f59e0b', 0.9: '#ef4444' }
    }).addTo(mapInstance);
}

// تصدير الدوال والحالات العامة
window.MAP_STYLES = MAP_STYLES;
window.initMap = initMap;
window.switchMapStyle = switchMapStyle;
window.haversineKm = haversineKm;
window.lerpColor = lerpColor;
window.clearMapLayers = clearMapLayers;
window.showMapToast = showMapToast;
window.filterGPSPoints = filterGPSPoints;
window.snapPointsToRoads = snapPointsToRoads;
window.renderRouteOnMap = renderRouteOnMap;
window.safeFlyTo = safeFlyTo;
window.updateLastLocationInfo = updateLastLocationInfo;
window.updateMapStats = updateMapStats;
window.toggleHeatmap = toggleHeatmap;
window.updateHeatmap = updateHeatmap;

console.log('✅ [Feature/LiveMap] Service Initialized');
