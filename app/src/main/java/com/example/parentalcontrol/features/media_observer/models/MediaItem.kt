package com.example.parentalcontrol.features.media_observer.models

import android.net.Uri

/**
 * MediaItem — نموذج يمثل بيانات الصورة الملتقطة أو المحفوظة على الهاتف
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val filePath: String,
    val fileName: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val bucketName: String? = null,
    val sizeBytes: Long = 0L
)
