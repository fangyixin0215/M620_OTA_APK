package com.huixiangtel.m620ota.ota

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/** 服务器上的固件信息。 */
data class FirmwareInfo(
    val version: String,
    val fileName: String,
    val notes: String = "",
    val url: String
)

sealed class DownloadResult {
    data class Success(val file: File, val info: FirmwareInfo) : DownloadResult()
    data class Failure(val reason: String) : DownloadResult()
}

/** 最新固件查询结果：区分"服务器可达但无更新"与"服务器不可达"。 */
data class FirmwareLookup(
    val serverReachable: Boolean,
    val latest: FirmwareInfo?
)

/**
 * 固件仓库：从 nginx 静态目录（http://wiki.huixiangtel.com/M620/）获取固件。
 *
 * 固件命名约定：`M620_V{版本号}.zip`，如 `M620_V0.8.zip`。
 * 版本号格式：`主.次[修订字母]`，如 0.6A、0.8、1.0B。
 *
 * 最新版本发现策略（按优先级）：
 * 1. version.json（后台管理端上线后优先使用）：
 *    {"version":"0.8","file":"M620_V0.8.zip","notes":"..."}
 * 2. 版本探测：基于设备当前版本生成候选版本号，
 *    用 HTTP Range 单字节请求探测存在的固件文件，取最高版本。
 *
 * 下载支持断点续传（HTTP Range）与最多 3 次自动重试。
 */
class FirmwareRepository(context: Context) {

    companion object {
        const val BASE_URL = "http://wiki.huixiangtel.com/M620/"
        private const val MAX_RETRY = 3
        private const val PROBE_MINOR_RANGE = 8
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val firmwareDir = File(context.filesDir, "firmware").apply { mkdirs() }

    /**
     * 查询服务器上的最新固件。
     * - serverReachable = true 且 latest 不比设备新 → 设备已是最新
     * - serverReachable = false → 服务器不可达（网络问题）
     */
    suspend fun lookupLatestFirmware(deviceVersion: String?): FirmwareLookup =
        withContext(Dispatchers.IO) {
            fetchFromVersionJson()?.let { return@withContext FirmwareLookup(true, it) }
            val found = probeExistingFirmware(deviceVersion)
            FirmwareLookup(
                serverReachable = found.isNotEmpty(),
                latest = found.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
            )
        }

    // ---------- 策略 1：version.json ----------

    private fun fetchFromVersionJson(): FirmwareInfo? = runCatching {
        val request = Request.Builder().url(BASE_URL + "version.json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val body = response.body?.string() ?: return@runCatching null
            val json = JSONObject(body)
            val fileName = json.getString("file")
            FirmwareInfo(
                version = json.getString("version"),
                fileName = fileName,
                notes = json.optString("notes"),
                url = BASE_URL + fileName
            )
        }
    }.getOrNull()

    // ---------- 策略 2：版本号探测 ----------

    /** 探测服务器上存在的固件文件列表。 */
    private suspend fun probeExistingFirmware(deviceVersion: String?): List<FirmwareInfo> {
        val candidates = buildProbeCandidates(deviceVersion)
        val found = mutableListOf<FirmwareInfo>()
        // 分批并发探测，避免一次性发起过多请求
        candidates.chunked(10).forEach { batch ->
            coroutineScope {
                batch.map { version ->
                    async(Dispatchers.IO) {
                        val fileName = "M620_V$version.zip"
                        if (probeExists(BASE_URL + fileName)) {
                            FirmwareInfo(version, fileName, "", BASE_URL + fileName)
                        } else null
                    }
                }.awaitAll().filterNotNullTo(found)
            }
        }
        return found
    }

    /** 单字节 Range 请求探测文件是否存在（nginx 支持 206）。 */
    private fun probeExists(url: String): Boolean = runCatching {
        val request = Request.Builder().url(url).addHeader("Range", "bytes=0-0").build()
        client.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    /**
     * 生成候选版本号：以设备当前版本为中心，覆盖当前、更低和更高版本。
     * 例如当前 0.8A → 0.8A、0.8、0.8B、0.6~0.16 各后缀、1.0~1.2 各后缀。
     * 覆盖当前/更低版本是为了区分"服务器可达但设备已是最新"与"服务器不可达"。
     */
    private fun buildProbeCandidates(deviceVersion: String?): List<String> {
        val (major, minor, suffix) = parseVersion(deviceVersion) ?: Triple(0, 0, "")
        val candidates = mutableListOf<String>()

        // 当前版本本身
        candidates += "$major.$minor$suffix"
        // 同数字版本的其他修订字母（0.8A → 0.8、0.8B）
        if (suffix.isNotEmpty()) candidates += "$major.$minor"
        if (suffix.isEmpty() || suffix[0] > 'A') candidates += "$major.${minor}A"
        if (suffix.isEmpty() || suffix[0] != 'B') {
            if (suffix.isEmpty() || suffix[0] < 'B') candidates += "$major.${minor}B"
        }
        // 更高的修订字母（0.8B → 0.8C）
        if (suffix.isNotEmpty() && suffix[0] < 'Z') {
            candidates += "$major.$minor${suffix[0] + 1}"
        }
        // 相邻次版本号（向下 3 个用于可达性判断，向上 8 个用于发现新版本）
        val from = (minor - 3).coerceAtLeast(0)
        for (m in from..(minor + PROBE_MINOR_RANGE)) {
            candidates += "$major.$m"
            candidates += "$major.${m}A"
            candidates += "$major.${m}B"
        }
        // 下一个主版本号
        for (m in 0..2) {
            candidates += "${major + 1}.$m"
            candidates += "${major + 1}.${m}A"
        }
        return candidates.distinct()
    }

    // ---------- 下载 ----------

    /** 下载固件（断点续传 + 自动重试），progress 回调 0..100。 */
    suspend fun download(
        info: FirmwareInfo,
        onProgress: (Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val target = File(firmwareDir, info.fileName)
        repeat(MAX_RETRY) {
            try {
                resumeDownload(info.url, target, onProgress)
                if (isValidZip(target)) return@withContext DownloadResult.Success(target, info)
            } catch (_: Exception) {
                // 继续重试
            }
        }
        target.delete()
        DownloadResult.Failure("固件下载失败，请检查网络后重试")
    }

    private fun resumeDownload(url: String, target: File, onProgress: (Int) -> Unit) {
        val existing = if (target.exists()) target.length() else 0L
        val request = Request.Builder().url(url).apply {
            if (existing > 0) addHeader("Range", "bytes=$existing-")
        }.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            val body = response.body ?: throw java.io.IOException("Empty body")

            // 服务器支持 Range(206) 时续传，否则从头下载
            val append = response.code == 206 && existing > 0
            val downloaded = if (append) existing else 0L
            val total = downloaded + body.contentLength()

            if (!append && target.exists()) target.delete()

            body.byteStream().use { input ->
                val output = if (append) {
                    val raf = RandomAccessFile(target, "rw")
                    raf.seek(raf.length())
                    object : java.io.OutputStream() {
                        override fun write(b: Int) = raf.write(b)
                        override fun write(b: ByteArray, off: Int, len: Int) =
                            raf.write(b, off, len)
                        override fun close() = raf.close()
                    }
                } else FileOutputStream(target)

                output.use { out ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    var current = downloaded
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        current += read
                        if (total > 0)
                            onProgress((current * 100 / total).toInt().coerceIn(0, 100))
                    }
                    out.flush()
                }
            }
        }
    }

    /** 简单校验 zip 文件头（PK 魔数）。 */
    private fun isValidZip(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return RandomAccessFile(file, "r").use {
            it.readByte() == 0x50.toByte() && it.readByte() == 0x4B.toByte()
        }
    }

    // ---------- 版本号解析与比较 ----------

    /**
     * 解析版本号 "0.6A" → Triple(主=0, 次=6, 修订="A")。
     * 支持 "1.2.3"、"0.8"、"V1.0B" 等格式。
     */
    fun parseVersion(version: String?): Triple<Int, Int, String>? {
        if (version.isNullOrBlank()) return null
        val v = version.trim().trimStart('v', 'V')
        val match = Regex("^([0-9]+)\\.([0-9]+)(?:\\.[0-9]+)?([A-Za-z]?)$").matchEntire(v)
            ?: return null
        return Triple(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].uppercase()
        )
    }

    /**
     * 比较两个版本号：a > b 返回正数。
     * 0.8 > 0.6A；0.8A > 0.8；1.0 > 0.9B。
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        if (pa == null || pb == null) return a.compareTo(b, ignoreCase = true)
        if (pa.first != pb.first) return pa.first - pb.first
        if (pa.second != pb.second) return pa.second - pb.second
        return pa.third.compareTo(pb.third)
    }

    /** server 版本比 device 版本新（需要升级）返回 true。无法解析时按不等处理。 */
    fun isNewer(serverVersion: String, deviceVersion: String?): Boolean {
        if (deviceVersion.isNullOrBlank()) return true
        val sa = parseVersion(serverVersion)
        val da = parseVersion(deviceVersion)
        if (sa == null || da == null) {
            return !serverVersion.equals(deviceVersion, ignoreCase = true)
        }
        return compareVersions(serverVersion, deviceVersion) > 0
    }
}
