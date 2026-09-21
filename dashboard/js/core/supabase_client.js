/**
 * supabase_client.js — Central Supabase Client & Configuration
 * ─────────────────────────────────────────────────────────────
 * جزء النواة المشتركة (core/): تهيئة العميل الموحد ومفاتيح الاتصال
 */

'use strict';

const SUPABASE_URL = 'https://ahiqnmheiqigrdbishwe.supabase.co';
const SUPABASE_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFoaXFubWhlaXFpZ3JkYmlzaHdlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU0NTMyNDgsImV4cCI6MjEwMTAyOTI0OH0.qnX22vNr-qMqCOOaUp6yQ9qguvv8Aen6KjALjFSTlPg';

const db = (typeof supabase !== 'undefined' && supabase.createClient)
    ? supabase.createClient(SUPABASE_URL, SUPABASE_KEY, {
        auth: {
            persistSession: true,
            autoRefreshToken: true,
            detectSessionInUrl: false
        }
    })
    : null;

const ONLINE_THRESHOLD_MINUTES = 30; // عتبة اتصال الأجهزة لامتصاص توفير الطاقة في أندرويد

// تصدير للكائنات العامة في المتصفح
window.SUPABASE_URL = SUPABASE_URL;
window.SUPABASE_KEY = SUPABASE_KEY;
window.db = db;
window.ONLINE_THRESHOLD_MINUTES = ONLINE_THRESHOLD_MINUTES;

console.log('✅ [Core] Supabase Client Initialized');
