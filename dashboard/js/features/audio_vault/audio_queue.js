/**
 * audio_queue.js — Sequential Audio Queue & State Management
 * ─────────────────────────────────────────────────────────────
 * ميزة خزينة الصوت (audio_vault): إدارة التشغيل المتتالي للمحدد تصاعدياً،
 * فك قيود التشغيل التلقائي (Autoplay unlock)، إدارة الحالات في localStorage، وفلاتر الوقت.
 */

'use strict';

window.audioQueue = window.audioQueue || [];
window.currentQueueIndex = window.currentQueueIndex || -1;
window.cachedAudioStates = window.cachedAudioStates || { heard: {}, favorites: {}, names: {} };

/**
 * ── إدارة حالات التسجيلات المسموعة (Heard Recordings) ──
 * محفوظة في localStorage['heard_recordings'] (بند أساسي في قواعد المشروع)
 */
function getHeardAudios() {
    try {
        return JSON.parse(localStorage.getItem('heard_recordings') || '{}');
    } catch (e) {
        return {};
    }
}

function markAudioAsHeard(filePath, cardId) {
    if (!filePath) return;
    try {
        const heard = getHeardAudios();
        if (!heard[filePath]) {
            heard[filePath] = true;
            localStorage.setItem('heard_recordings', JSON.stringify(heard));

            if (window.cachedAudioStates) {
                window.cachedAudioStates.heard = heard;
            }

            // تحديث واجهة الشارة في الكرت فوراً دون إعادة تحميل القائمة
            if (cardId) {
                const card = document.getElementById(`audio-card-${cardId}`);
                if (card) {
                    const badge = card.querySelector('.audio-heard-badge');
                    if (badge) {
                        badge.textContent = 'مسموع';
                        badge.className = 'audio-heard-badge px-2 py-0.5 rounded text-[10px] font-medium bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
                    }
                }
            }
            if (typeof saveAudioStatesToStorage === 'function') {
                saveAudioStatesToStorage();
            }
        }
    } catch (e) {
        console.warn('[markAudioAsHeard] Error:', e);
    }
}

/**
 * ── المفضلات والأسماء المخصصة ──
 */
function getFavoriteAudios() {
    try {
        return JSON.parse(localStorage.getItem('favorite_recordings') || '{}');
    } catch (e) {
        return {};
    }
}

function isAudioFavorite(filePath) {
    return !!getFavoriteAudios()[filePath];
}

function toggleAudioFavorite(filePath, cardId) {
    const favs = getFavoriteAudios();
    if (favs[filePath]) {
        delete favs[filePath];
        if (typeof showNotif === 'function') showNotif('⭐ تمت الإزالة من قائمة المفضل', 'info');
    } else {
        favs[filePath] = true;
        if (typeof showNotif === 'function') showNotif('⭐ تمت الإضافة إلى قائمة المفضل', 'success');
    }
    localStorage.setItem('favorite_recordings', JSON.stringify(favs));
    
    if (typeof saveAudioStatesToStorage === 'function') saveAudioStatesToStorage();
    if (typeof fetchAudio === 'function') fetchAudio();
}

function getCustomAudioNames() {
    try {
        return JSON.parse(localStorage.getItem('custom_audio_names') || '{}');
    } catch (e) {
        return {};
    }
}

function getCustomAudioName(filePath) {
    return getCustomAudioNames()[filePath] || null;
}

function renameAudio(filePath, cardId) {
    const currentName = getCustomAudioName(filePath) || filePath.split('/').pop();
    const newName = prompt('أدخل الاسم الجديد للتسجيل:', currentName);
    if (newName === null) return;
    const trimmed = newName.trim();
    if (!trimmed) return;
    const names = getCustomAudioNames();
    names[filePath] = trimmed;
    localStorage.setItem('custom_audio_names', JSON.stringify(names));
    if (typeof showNotif === 'function') showNotif('✏️ تم تغيير اسم التسجيل بنجاح', 'success');
    
    if (typeof saveAudioStatesToStorage === 'function') saveAudioStatesToStorage();
    if (typeof fetchAudio === 'function') fetchAudio();
}

/**
 * إعادة تعيين حقول تصفية التاريخ والوقت
 */
function resetAudioDateTimeFilter() {
    const fromInput = document.getElementById('audio-filter-from');
    const toInput = document.getElementById('audio-filter-to');
    if (fromInput) fromInput.value = '';
    if (toInput) toInput.value = '';
    if (typeof fetchAudio === 'function') fetchAudio();
}

/**
 * ── التشغيل المتتالي للمحدد (Sequential Playback) ──
 * يقوم بفرز التسجيلات المحددة زمنياً تصاعدياً (من الأقدم للأحدث) وتشغيلها تباعاً
 */
async function playSelectedAudio() {
    const checked = document.querySelectorAll('.audio-checkbox:checked, .audio-item-checkbox:checked');
    if (checked.length === 0) {
        if (typeof showNotif === 'function') showNotif('⚠️ لم يتم اختيار أي تسجيل', 'warn');
        return;
    }
    
    // إيقاف أي قائمة تشغيل سابقة
    window.audioQueue = [];
    window.currentQueueIndex = -1;
    window.isAudioPlayingGlobal = true;
    
    // 🚀 CRITICAL FOR MOBILE & AUTOPLAY: فك حظر Autoplay بنغمة صامتة فور حدوث إيماءة المستخدم
    const sharedPlayer = (typeof getSharedAudioPlayer === 'function')
        ? getSharedAudioPlayer()
        : document.getElementById('shared-audio-player');

    if (sharedPlayer) {
        try {
            sharedPlayer.src = 'data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=';
            sharedPlayer.muted = true;
            const primePromise = sharedPlayer.play();
            if (primePromise) {
                primePromise.then(() => {
                    sharedPlayer.pause();
                    sharedPlayer.muted = false;
                }).catch(() => {
                    sharedPlayer.muted = false;
                });
            }
        } catch (_) {}
    }

    const playSelectedBtn = document.getElementById('audio-play-selected-btn') || document.getElementById('btn-play-selected-audios');
    const origBtnText = playSelectedBtn ? playSelectedBtn.innerHTML : '🔊 استماع للمحدد';
    if (playSelectedBtn) playSelectedBtn.innerHTML = '⏳ جاري تجهيز الملفات...';
    
    const itemsToPlay = Array.from(checked).map(cb => ({
        path: cb.getAttribute('data-path') || cb.dataset.path || cb.dataset.filePath,
        cardId: cb.getAttribute('data-cardid') || cb.dataset.cardid || cb.dataset.cardId,
        createdAt: cb.getAttribute('data-createdat') || cb.dataset.createdat || ''
    })).filter(item => item.path && item.cardId);
    
    // فرز تصاعدي زمني صارم (من الأقدم للأحدث) وفق قواعد المنظومة
    itemsToPlay.sort((a, b) => {
        const tA = (typeof getAudioFileTimestamp === 'function')
            ? getAudioFileTimestamp({ name: a.path.split('/').pop(), created_at: a.createdAt })
            : new Date(a.createdAt).getTime();
        const tB = (typeof getAudioFileTimestamp === 'function')
            ? getAudioFileTimestamp({ name: b.path.split('/').pop(), created_at: b.createdAt })
            : new Date(b.createdAt).getTime();
        return tA - tB;
    });
    
    if (typeof showNotif === 'function') {
        showNotif(`⏳ جاري تجهيز وتحميل ${itemsToPlay.length} تسجيلات للاستماع...`, 'info');
    }
    
    const readyItems = [];
    const client = window.db || db;

    for (let i = 0; i < itemsToPlay.length; i++) {
        const item = itemsToPlay[i];
        if (playSelectedBtn) {
            playSelectedBtn.innerHTML = `⏳ تجهيز (${i + 1}/${itemsToPlay.length})...`;
        }

        const card = document.getElementById(`audio-card-${item.cardId}`);
        const playBtn = card ? card.querySelector('button[onclick*="loadAudioBlob"], .audio-play-btn') : null;
        const status = card ? card.querySelector('.audio-status-text') : null;

        if (status) {
            status.innerText = '⏳ تحميل...';
            status.classList.remove('hidden');
        }

        try {
            const { data, error } = await client.storage.from('monitoring_data').download(item.path);
            if (error) throw error;

            const arrayBuffer = await data.arrayBuffer();
            if (!arrayBuffer || arrayBuffer.byteLength < 50) {
                throw new Error('الملف الصوتي فارغ');
            }

            const audioBlob = new Blob([arrayBuffer], { type: 'audio/m4a' });
            const blobUrl = URL.createObjectURL(audioBlob);
            item.blobUrl = blobUrl;

            if (playBtn) playBtn.style.display = 'none';
            if (status) status.classList.add('hidden');

            readyItems.push(item);
        } catch (e) {
            console.error('[playSelectedAudio] Download failed for item:', item.path, e);
            if (status) status.innerText = '❌ فشل التحميل';
        }
    }

    if (playSelectedBtn) playSelectedBtn.innerHTML = origBtnText;

    if (readyItems.length === 0) {
        window.isAudioPlayingGlobal = false;
        if (typeof showNotif === 'function') showNotif('❌ فشل تحميل كافة التسجيلات المحددة', 'error');
        return;
    }

    window.audioQueue = readyItems;
    window.currentQueueIndex = 0;
    if (typeof showNotif === 'function') {
        showNotif(`▶️ بدء تشغيل ${window.audioQueue.length} تسجيلات بالترتيب...`, 'success');
    }
    playQueueItem(window.currentQueueIndex);
}

async function playQueueItem(index) {
    const queue = window.audioQueue || [];
    if (index < 0 || index >= queue.length) {
        if (window.currentQueueIndex >= 0 && window.currentQueueIndex < queue.length) {
            const prevItem = queue[window.currentQueueIndex];
            if (prevItem) {
                const prevCard = document.getElementById(`audio-card-${prevItem.cardId}`);
                if (prevCard) {
                    prevCard.style.border = '';
                    prevCard.style.background = '';
                }
            }
        }
        window.audioQueue = [];
        window.currentQueueIndex = -1;
        window.isAudioPlayingGlobal = false;
        if (typeof showNotif === 'function') showNotif('✅ تم الانتهاء من تشغيل كافة التسجيلات المحددة', 'success');
        return;
    }
    
    window.isAudioPlayingGlobal = true;

    // مسح التأشير عن الكرت السابق
    if (window.currentQueueIndex >= 0 && window.currentQueueIndex < queue.length) {
        const prevItem = queue[window.currentQueueIndex];
        if (prevItem) {
            const prevCard = document.getElementById(`audio-card-${prevItem.cardId}`);
            if (prevCard) {
                prevCard.style.border = '';
                prevCard.style.background = '';
            }
        }
    }

    window.currentQueueIndex = index;
    const item = queue[index];
    const card = document.getElementById(`audio-card-${item.cardId}`);
    if (!card) return playNextQueueItem();

    const status = card.querySelector('.audio-status-text');
    if (status) status.classList.add('hidden');

    // تمييز الكرت النشط (إطار أخضر وخلفية خضراء خفيفة)
    card.style.border = '1px solid #22c55e';
    card.style.background = 'rgba(34,197,94,0.05)';
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    // نقل المشغل المشترك للكرت النشط
    const player = (typeof getSharedAudioPlayer === 'function')
        ? getSharedAudioPlayer(item.cardId)
        : document.getElementById('shared-audio-player');

    if (!player) return playNextQueueItem();

    player.dataset.filePath = item.path;
    player.dataset.cardId = item.cardId;

    player.onended = null;
    player.onerror = null;

    player.src = item.blobUrl;

    player.onended = () => {
        card.style.border = '';
        card.style.background = '';
        markAudioAsHeard(item.path, item.cardId);
        setTimeout(() => playNextQueueItem(), 200);
    };

    player.onerror = () => {
        card.style.border = '';
        card.style.background = '';
        console.warn('[playQueueItem] Audio error, skipping to next track...');
        setTimeout(() => playNextQueueItem(), 200);
    };

    try {
        await player.play();
    } catch (e) {
        console.warn('[playQueueItem] Play rejected, advancing to next item:', e);
        card.style.border = '';
        card.style.background = '';
        setTimeout(() => playNextQueueItem(), 200);
    }
}

function playNextQueueItem() {
    playQueueItem((window.currentQueueIndex || 0) + 1);
}

// تصدير للدوال العامة
window.getHeardAudios = getHeardAudios;
window.markAudioAsHeard = markAudioAsHeard;
window.getFavoriteAudios = getFavoriteAudios;
window.isAudioFavorite = isAudioFavorite;
window.toggleAudioFavorite = toggleAudioFavorite;
window.getCustomAudioNames = getCustomAudioNames;
window.getCustomAudioName = getCustomAudioName;
window.renameAudio = renameAudio;
window.resetAudioDateTimeFilter = resetAudioDateTimeFilter;
window.playSelectedAudio = playSelectedAudio;
window.playSelectedAudios = playSelectedAudio; // Alias
window.playQueueItem = playQueueItem;
window.playNextQueueItem = playNextQueueItem;

console.log('✅ [Feature/AudioVault] Queue & State Manager Initialized');
