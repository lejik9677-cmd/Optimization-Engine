/**
 * session_controller.js — Session & Authentication Controller
 * ─────────────────────────────────────────────────────────────
 * جزء النواة المشتركة (core/): إدارة فحص الجلسة، تسجيل الدخول/الخروج،
 * ومراقبة دورة حياة المصادقة (Auth State Listeners).
 */

'use strict';

async function checkSession() {
    // إتاحة الوصول المباشر للوحة التحكم لسهولة الإدارة
    toggleDashboard(true);
}

function toggleDashboard(isLoggedIn) {
    const loginScreen = document.getElementById('loginScreen');
    const dashboard   = document.getElementById('dashboard');

    if (loginScreen) loginScreen.style.display = isLoggedIn ? 'none' : 'flex';
    if (dashboard)   dashboard.style.display   = isLoggedIn ? 'flex' : 'none';

    if (isLoggedIn) {
        if (typeof loadDeviceList === 'function') loadDeviceList();
        if (typeof startAutoRefresh === 'function') startAutoRefresh();
        if (typeof buildSettingsToggles === 'function') buildSettingsToggles();
    }
}

async function handleLogin() {
    toggleDashboard(true);
}

async function handleLogout() {
    if (!confirm('هل تريد تسجيل الخروج؟')) return;
    if (window.db && window.db.auth) {
        try {
            await window.db.auth.signOut();
        } catch (e) {
            console.warn('[Auth] SignOut error:', e);
        }
    }
    toggleDashboard(false);
}

function initSessionListener() {
    if (window.db && window.db.auth) {
        window.db.auth.onAuthStateChange((event, session) => {
            console.log('[Auth] State changed:', event, !!session);
            if (event === 'SIGNED_IN' || event === 'TOKEN_REFRESHED') {
                toggleDashboard(true);
            } else if (event === 'SIGNED_OUT') {
                toggleDashboard(false);
            }
        });
    }
}

// تصدير للدوال العامة
window.checkSession = checkSession;
window.toggleDashboard = toggleDashboard;
window.handleLogin = handleLogin;
window.handleLogout = handleLogout;
window.initSessionListener = initSessionListener;

console.log('✅ [Core] Session Controller Initialized');
