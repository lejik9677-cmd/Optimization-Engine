package com.example.parentalcontrol

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * محرك تسجيل الصوت المتقدم (AudioRecorderEngine)
 * يقوم بالتسجيل بجودة AAC وضغط الملفات لتقليل الاستهلاك
 * ميزة: كشف الصمت (Silence Detection) لحذف التسجيلات غير المهمة
 */
class AudioRecorderEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorderEngine"
        private const val BUCKET_NAME = "monitoring_data"
        private const val AUDIO_FOLDER = "audio_recordings"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isRecording = false
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * تسجيل مقطع صوتي لمدة محددة ثم رفعه تلقائياً
     */
    fun recordAndUpload(durationMs: Long) {
        engineScope.launch {
            startRecording()
            delay(durationMs)
            stopRecording(upload = true)
        }
    }

    /**
     * بدء التسجيل
     */
    fun startRecording() {
        if (isRecording) return

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val fileName = "audio_$timestamp.m4a"
            currentFile = File(context.cacheDir, fileName)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(currentFile?.absolutePath)
                
                prepare()
                start()
            }

            isRecording = true
            Log.i(TAG, "Recording started: ${currentFile?.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Start recording failed: ${e.message}")
            stopRecording(upload = false)
        }
    }

    /**
     * إيقاف التسجيل مع خيار الرفع أو الحذف
     */
    fun stopRecording(upload: Boolean = true) {
        if (!isRecording) return

        engineScope.launch {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stop recorder error: ${e.message}")
            } finally {
                mediaRecorder = null
                isRecording = false
            }

            if (upload && currentFile != null && currentFile!!.exists()) {
                if (isSilence(currentFile!!)) {
                    Log.i(TAG, "Silence detected! Discarding recording.")
                    currentFile!!.delete()
                } else {
                    uploadRecording(currentFile!!)
                }
            }
        }
    }

    /**
     * خوارزمية كشف الصمت (Silence Detection)
     * ملاحظة: هذه نسخة مبسطة تعتمد على حجم الملف لعدم وجود تحليل موجي فوري
     * محترفاً: يجب استخدام AudioRecord وتحليل RMS للتردد
     */
    private fun isSilence(file: File): Boolean {
        // إذا كان الملف صغيراً جداً (مثلاً أقل من 5 كيلوبايت لـ 30 ثانية) فهو كالصمت
        // AAC بترميز 32kbps يولد حوالي 4KB في الثانية
        // سنعتمد على الحجم الفعلي للملف كبداية
        return file.length() < 1024 * 5 // أقل من 5 كيلوبايت
    }

    /**
     * رفع الملف إلى Supabase
     */
    private suspend fun uploadRecording(file: File) {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            val result = SupabaseManager.getInstance().uploadFile(
                file = file,
                bucket = BUCKET_NAME,
                folder = "audio/$deviceId",
                customFileName = file.name
            )

            if (result is UploadResult.Success) {
                Log.i(TAG, "Audio uploaded successfully: ${result.publicUrl}")
                file.delete() // حذف محلي بعد الرفع
            } else {
                Log.e(TAG, "Audio upload failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error: ${e.message}")
        }
    }
}
