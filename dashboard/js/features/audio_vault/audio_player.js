/**
 * audio_player.js — Shared Audio Player & Progress Slider Controls
 * ─────────────────────────────────────────────────────────────
 * ميزة خزينة الصوت (audio_vault): إدارة المشغل المشترك الوحيد لجميع البطاقات،
 * أشرطة التمرير والتقدم، تحميل ملفات الصوت كـ Blob، وتحديث مظهر البطاقات.
 */

'use strict';

/**
 * تحديث واجهة بطاقة الصوت (الزر، المدى، الوقت الحالي، والمدة الكلية)
 */
function updateCardAudioUI(cardId, isPlaying, currentTime, duration) {
    if (!cardId) return;
    const card = document.getElementById(`audio-card-${cardId}`);
    if (!card) return;

    const playBtn = card.querySelector('.audio-play-btn');
    const rangeInput = card.querySelector('.audio-progress-bar');
    const timeCurr = card.querySelector('.audio-time-current');
    const timeDur = card.querySelector('.audio-time-duration');

    if (playBtn) {
        playBtn.innerHTML = isPlaying ? '⏸️ إيقاف' : '▶️ تشغيل';
        if (isPlaying) {
            playBtn.classList.remove('bg-emerald-500/10', 'text-emerald-400', 'border-emerald-500/20');
            playBtn.classList.add('bg-amber-500/20', 'text-amber-300', 'border-amber-500/40');
        } else {
            playBtn.classList.remove('bg-amber-500/20', 'text-amber-300', 'border-amber-500/40');
            playBtn.classList.add('bg-emerald-500/10', 'text-emerald-400', 'border-emerald-500/20');
        }
    }

    if (duration && duration > 0) {
        if (rangeInput && !rangeInput.dataset.isSeeking) {
            const pct = (currentTime / duration) * 100;
            rangeInput.value = pct;
        }
        if (timeCurr) timeCurr.innerText = (typeof formatAudioSeconds === 'function') ? formatAudioSeconds(currentTime) : '00:00';
        if (timeDur) timeDur.innerText = (typeof formatAudioSeconds === 'function') ? formatAudioSeconds(duration) : '00:00';
    } else if (!isPlaying) {
        if (rangeInput && !rangeInput.dataset.isSeeking) rangeInput.value = 0;
        if (timeCurr) timeCurr.innerText = '00:00';
    }
}

/**
 * تمرير وسحب نقطة التشغيل الصوتي من شريط المدى
 */
function seekAudioFromCard(rangeInput, cardId) {
    const player = getSharedAudioPlayer();
    if (player && player.dataset.cardId === cardId && player.duration && player.duration > 0) {
        const targetTime = (parseFloat(rangeInput.value) / 100) * player.duration;
        player.currentTime = targetTime;
        const card = document.getElementById(`audio-card-${cardId}`);
        if (card) {
            const timeCurr = card.querySelector('.audio-time-current');
            if (timeCurr) timeCurr.innerText = (typeof formatAudioSeconds === 'function') ? formatAudioSeconds(targetTime) : '00:00';
        }
    }
}

/**
 * مشغل الصوت المشترك (Singleton Audio Player)
 * يضمن تشغيل مقطع واحد فقط في كل لحظة وعدم تداخل الأصوات
 */
function getSharedAudioPlayer(cardId) {
    let player = document.getElementById('shared-audio-player');
    if (!player) {
        player = document.createElement('audio');
        player.id = 'shared-audio-player';
        player.style.display = 'none';
        player.preload = 'auto';
        document.body.appendChild(player);

        player.addEventListener('timeupdate', () => {
            if (player.dataset.cardId) {
                updateCardAudioUI(player.dataset.cardId, !player.paused && !player.ended, player.currentTime, player.duration);
            }
        });

        player.addEventListener('loadedmetadata', () => {
            if (player.dataset.cardId) {
                updateCardAudioUI(player.dataset.cardId, !player.paused && !player.ended, player.currentTime, player.duration);
            }
        });

        player.addEventListener('play', () => {
            window.isAudioPlayingGlobal = true;
            if (player.dataset.filePath && player.dataset.cardId) {
                if (typeof markAudioAsHeard === 'function') {
                    markAudioAsHeard(player.dataset.filePath, player.dataset.cardId);
                }
                updateCardAudioUI(player.dataset.cardId, true, player.currentTime, player.duration);
            }
        });

        player.addEventListener('pause', () => {
            const queue = window.audioQueue || [];
            if (queue.length === 0 && !player.src) {
                window.isAudioPlayingGlobal = false;
            }
            if (player.dataset.cardId) {
                updateCardAudioUI(player.dataset.cardId, false, player.currentTime, player.duration);
            }
        });

        player.addEventListener('ended', () => {
            const queue = window.audioQueue || [];
            if (queue.length === 0) window.isAudioPlayingGlobal = false;
            if (player.dataset.cardId) {
                updateCardAudioUI(player.dataset.cardId, false, 0, player.duration);
            }
        });
    }

    if (cardId && player.dataset.cardId !== cardId) {
        if (player.dataset.cardId) {
            updateCardAudioUI(player.dataset.cardId, false, 0, 0);
        }
        player.dataset.cardId = cardId;
    }

    if (typeof applyAudioAmplifier === 'function') {
        applyAudioAmplifier(player);
    } else if (typeof window.applyAudioAmplifier === 'function') {
        window.applyAudioAmplifier(player);
    }

    return player;
}

/**
 * تحميل وتشغيل مقطع صوتي محدد من Supabase Storage
 */
async function loadAudioBlob(btn, filePath, cardId) {
    const player = getSharedAudioPlayer(cardId);

    // تبديل التشغيل والإيقاف إذا كان نفس المقطع نشطاً
    if (player.dataset.cardId === cardId && player.src && player.src.length > 0) {
        if (!player.paused && !player.ended) {
            player.pause();
            return;
        } else if (player.paused && !player.ended && player.currentTime > 0) {
            player.play().catch(e => console.warn('Play interrupted:', e));
            return;
        }
    }

    window.audioQueue = [];
    window.currentQueueIndex = -1;

    const card = document.getElementById(`audio-card-${cardId}`);
    if (!card) return;
    const status = card.querySelector('.audio-status-text');

    window.isAudioPlayingGlobal = true;

    if (status) {
        status.innerText = '⏳ جاري التحميل من السحاب...';
        status.classList.remove('hidden');
    }

    try {
        const client = window.db || db;
        if (!client) throw new Error('عميل السحابة غير مهيأ');

        const { data, error } = await client.storage.from('monitoring_data').download(filePath);
        if (error) throw error;

        const arrayBuffer = await data.arrayBuffer();
        if (!arrayBuffer || arrayBuffer.byteLength < 50) {
            throw new Error('الملف الصوتي فارغ أو لم يكتمل رفعه بعد');
        }
        const audioBlob = new Blob([arrayBuffer], { type: 'audio/m4a' });
        const blobUrl = URL.createObjectURL(audioBlob);

        player.dataset.filePath = filePath;
        player.dataset.cardId = cardId;
        player.src = blobUrl;
        if (status) status.classList.add('hidden');

        player.onended = () => {
            const queue = window.audioQueue || [];
            if (queue.length === 0) window.isAudioPlayingGlobal = false;
            if (typeof markAudioAsHeard === 'function') {
                markAudioAsHeard(filePath, cardId);
            }
            updateCardAudioUI(cardId, false, 0, player.duration);
        };
        player.onerror = () => {
            const queue = window.audioQueue || [];
            if (queue.length === 0) window.isAudioPlayingGlobal = false;
            updateCardAudioUI(cardId, false, 0, 0);
        };

        player.play().catch(e => console.warn('Play interrupted:', e));
    } catch (e) {
        console.error('[loadAudioBlob] Failed:', e);
        window.isAudioPlayingGlobal = false;
        if (status) status.innerText = '❌ فشل التحميل: ' + (e.message || 'خطأ اتصال');
        updateCardAudioUI(cardId, false, 0, 0);
    }
}

/**
 * إيقاف المشغل المشترك فوراً
 */
function stopSharedAudio() {
    const player = document.getElementById('shared-audio-player');
    if (player) {
        player.pause();
        if (player.dataset.cardId) {
            updateCardAudioUI(player.dataset.cardId, false, 0, 0);
        }
    }
    window.isAudioPlayingGlobal = false;
}

// تصدير للدوال العامة
window.updateCardAudioUI = updateCardAudioUI;
window.seekAudioFromCard = seekAudioFromCard;
window.getSharedAudioPlayer = getSharedAudioPlayer;
window.loadAudioBlob = loadAudioBlob;
window.stopSharedAudio = stopSharedAudio;

console.log('✅ [Feature/AudioVault] Audio Player Initialized');
