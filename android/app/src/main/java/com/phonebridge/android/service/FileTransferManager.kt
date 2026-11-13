package com.phonebridge.android.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.phonebridge.android.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class FileTransferStatus {
    object Idle : FileTransferStatus()
    data class Uploading(
        val fileName: String,
        val progress: Float,
        val bytesSent: Long,
        val totalBytes: Long
    ) : FileTransferStatus()
    data class Success(val fileName: String, val message: String = "Dosya başarıyla PC'ye gönderildi") : FileTransferStatus()
    data class Error(val fileName: String?, val errorMessage: String) : FileTransferStatus()
}

data class TransferredFileItem(
    val fileName: String,
    val fileSizeFormatted: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean,
    val note: String = ""
)

class FileTransferManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FileTransferManager"

        @Volatile
        private var INSTANCE: FileTransferManager? = null

        fun getInstance(context: Context): FileTransferManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileTransferManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val prefs = PreferencesManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val _transferStatus = MutableStateFlow<FileTransferStatus>(FileTransferStatus.Idle)
    val transferStatus: StateFlow<FileTransferStatus> = _transferStatus.asStateFlow()

    private val _transferHistory = MutableStateFlow<List<TransferredFileItem>>(emptyList())
    val transferHistory: StateFlow<List<TransferredFileItem>> = _transferHistory.asStateFlow()

    fun resetStatus() {
        _transferStatus.value = FileTransferStatus.Idle
    }

    suspend fun uploadFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val targetIp = prefs.pcIp.trim()
        val targetPort = prefs.pcPort

        if (targetIp.isBlank()) {
            _transferStatus.value = FileTransferStatus.Error(null, "PC IP adresi yapılandırılmamış.")
            return@withContext false
        }

        val fileInfo = getFileInfo(uri)
        val fileName = fileInfo.first
        val fileSize = fileInfo.second
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        Log.i(TAG, "Starting upload for file: $fileName ($fileSize bytes, $mimeType)")
        _transferStatus.value = FileTransferStatus.Uploading(
            fileName = fileName,
            progress = 0f,
            bytesSent = 0,
            totalBytes = fileSize
        )

        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Dosya açılamadı: $uri")

            val fileBytes = inputStream.use { it.readBytes() }
            val rawBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())

            val countingBody = CountingRequestBody(rawBody) { bytesWritten, totalLength ->
                val lengthToUse = if (totalLength > 0) totalLength else fileSize
                val progress = if (lengthToUse > 0) {
                    (bytesWritten.toFloat() / lengthToUse.toFloat()).coerceIn(0f, 1f)
                } else 0f

                _transferStatus.value = FileTransferStatus.Uploading(
                    fileName = fileName,
                    progress = progress,
                    bytesSent = bytesWritten,
                    totalBytes = lengthToUse
                )
            }

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, countingBody)
                .build()

            val uploadUrl = "http://$targetIp:$targetPort/api/upload"
            val request = Request.Builder()
                .url(uploadUrl)
                .post(multipartBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                _transferStatus.value = FileTransferStatus.Success(fileName)
                addHistory(fileName, fileSize, true, "Tamamlandı")
                Log.i(TAG, "File $fileName successfully uploaded to $uploadUrl")
                return@withContext true
            } else {
                val errorMsg = "PC Yanıtı Hatalı: HTTP ${response.code}"
                _transferStatus.value = FileTransferStatus.Error(fileName, errorMsg)
                addHistory(fileName, fileSize, false, errorMsg)
                Log.e(TAG, "Upload failed with HTTP ${response.code}")
                return@withContext false
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Bağlantı hatası veya dosya gönderilemedi"
            Log.e(TAG, "Upload exception: $errorMsg", e)
            _transferStatus.value = FileTransferStatus.Error(fileName, errorMsg)
            addHistory(fileName, fileSize, false, errorMsg)
            return@withContext false
        }
    }

    private fun addHistory(fileName: String, fileSize: Long, isSuccess: Boolean, note: String) {
        val formattedSize = formatFileSize(fileSize)
        val newItem = TransferredFileItem(fileName, formattedSize, System.currentTimeMillis(), isSuccess, note)
        _transferHistory.value = listOf(newItem) + _transferHistory.value.take(19)
    }

    private fun getFileInfo(uri: Uri): Pair<String, Long> {
        var name = "dosya_${System.currentTimeMillis()}"
        var size: Long = 0

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { name = it }
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query file info: ${e.message}")
        }

        return Pair(name, size)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
    }

    private class CountingRequestBody(
        private val delegate: RequestBody,
        private val onProgress: (bytesWritten: Long, totalLength: Long) -> Unit
    ) : RequestBody() {

        override fun contentType() = delegate.contentType()

        override fun contentLength(): Long {
            return try {
                delegate.contentLength()
            } catch (e: IOException) {
                -1
            }
        }

        override fun writeTo(sink: BufferedSink) {
            val totalBytes = contentLength()
            var bytesWritten = 0L

            val countingSink = object : ForwardingSink(sink) {
                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    onProgress(bytesWritten, totalBytes)
                }
            }

            val bufferedSink = countingSink.buffer()
            delegate.writeTo(bufferedSink)
            bufferedSink.flush()
        }
    }
}