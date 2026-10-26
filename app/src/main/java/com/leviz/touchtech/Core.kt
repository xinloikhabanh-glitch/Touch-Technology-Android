package com.leviz.touchtech

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.sqrt

// =====================================================================
// Cấu hình (SỬA Ở ĐÂY)
// =====================================================================

object Config {
    /** Địa chỉ server Vercel của bạn, KHÔNG có dấu "/" ở cuối. */
    const val API_BASE = "https://api-rust-alpha-49.vercel.app"
    const val RECHECK_MS = 60_000L
    const val ZALO = "0335849687"
    const val TIKTOK = "thaitunhahotran"
}

// =====================================================================
// Lưu trữ
// =====================================================================

object Prefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("tt", Context.MODE_PRIVATE)

    fun str(c: Context, k: String, d: String = ""): String = sp(c).getString(k, d) ?: d
    fun putStr(c: Context, k: String, v: String) {
        sp(c).edit().putString(k, v).apply()
    }

    fun bool(c: Context, k: String, d: Boolean = false): Boolean = sp(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) {
        sp(c).edit().putBoolean(k, v).apply()
    }

    fun remove(c: Context, k: String) {
        sp(c).edit().remove(k).apply()
    }
}

@SuppressLint("HardwareIds")
fun deviceHwid(c: Context): String {
    val saved = Prefs.str(c, "hwid")
    if (saved.length >= 6) return saved
    val a = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID)
    val id = "and-" + (if (a.isNullOrBlank()) UUID.randomUUID().toString() else a)
    Prefs.putStr(c, "hwid", id)
    return id
}

// =====================================================================
// Giấy phép (gọi /api/verify)
// =====================================================================

data class LicenseInfo(
    val key: String,
    val tier: String,
    val lifetime: Boolean,
    val expiresAt: Long?,
    val deviceLock: Boolean,
    val maxDevices: Int,
    val devicesUsed: Int,
    val serverTime: Long
) {
    fun toJson(): String = JSONObject()
        .put("key", key)
        .put("tier", tier)
        .put("lifetime", lifetime)
        .put("expiresAt", expiresAt ?: JSONObject.NULL)
        .put("deviceLock", deviceLock)
        .put("maxDevices", maxDevices)
        .put("devicesUsed", devicesUsed)
        .put("serverTime", serverTime)
        .toString()

    companion object {
        fun parse(j: JSONObject): LicenseInfo = LicenseInfo(
            key = j.getString("key"),
            tier = j.optString("tier", "?"),
            lifetime = j.optBoolean("lifetime", false),
            expiresAt = if (j.isNull("expiresAt")) null else j.getLong("expiresAt"),
            deviceLock = j.optBoolean("deviceLock", true),
            maxDevices = j.optInt("maxDevices", 1),
            devicesUsed = j.optInt("devicesUsed", 0),
            serverTime = j.optLong("serverTime", System.currentTimeMillis())
        )
    }
}

sealed class VerifyResult {
    data class Ok(val info: LicenseInfo) : VerifyResult()
    data class Rejected(val msg: String) : VerifyResult()
    data class Unavailable(val msg: String) : VerifyResult()
}

object LicenseApi {
    /** Chạy trên luồng nền (Dispatchers.IO). */
    fun verify(ctx: Context, key: String): VerifyResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(Config.API_BASE + "/api/verify").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject()
                .put("key", key)
                .put("hwid", deviceHwid(ctx))
                .put("deviceName", Build.MODEL)
                .toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val j = try { JSONObject(text) } catch (e: Exception) { null }

            if (code == 200 && j != null && j.optBoolean("success", false)) {
                VerifyResult.Ok(LicenseInfo.parse(j))
            } else {
                val msg = j?.optString("message", "") ?: ""
                val shown = if (msg.isNotEmpty()) msg else "Lỗi máy chủ ($code)"
                val noCode = j == null || j.optString("error", "").isEmpty()
                if (code >= 500 || code == 0 || noCode) VerifyResult.Unavailable(shown)
                else VerifyResult.Rejected(shown)
            }
        } catch (e: Exception) {
            VerifyResult.Unavailable("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.")
        } finally {
            try { conn?.disconnect() } catch (e: Exception) { }
        }
    }
}

object Model {
    var info by mutableStateOf<LicenseInfo?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")
    var offline by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var skew = 0L
    private var loop: Job? = null
    private var started = false

    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val k = Prefs.str(app, "key")
        if (k.isNotEmpty()) scope.launch { activate(app, k) }
    }

    fun submit(ctx: Context, raw: String) {
        val app = ctx.applicationContext
        scope.launch { activate(app, raw) }
    }

    fun recheckNow(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { recheck(app) }
    }

    fun logout(ctx: Context) {
        lock(ctx.applicationContext, "")
    }

    fun expiryText(i: LicenseInfo): String {
        if (i.lifetime) return "Vĩnh viễn"
        val e = i.expiresAt ?: return "—"
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e))
    }

    private suspend fun activate(ctx: Context, raw: String) {
        val key = raw.trim().uppercase()
        if (key.isEmpty()) {
            message = "Hãy nhập key."
            return
        }
        if (busy) return
        busy = true
        message = ""
        try {
            when (val r = withContext(Dispatchers.IO) { LicenseApi.verify(ctx, key) }) {
                is VerifyResult.Ok -> accept(ctx, r.info)
                is VerifyResult.Rejected -> {
                    Prefs.remove(ctx, "key")
                    Prefs.remove(ctx, "license_cache")
                    info = null
                    message = r.msg
                }
                is VerifyResult.Unavailable -> {
                    val c = loadCache(ctx)
                    if (c != null && c.key == key && !isExpired(c)) {
                        info = c          // tạm dùng offline cho đến khi có mạng
                        offline = true
                        startLoop(ctx)
                    } else {
                        message = r.msg
                    }
                }
            }
        } finally {
            busy = false
        }
    }

    private suspend fun recheck(ctx: Context) {
        if (busy || info == null) return
        val key = Prefs.str(ctx, "key")
        if (key.isEmpty()) return
        when (val r = withContext(Dispatchers.IO) { LicenseApi.verify(ctx, key) }) {
            is VerifyResult.Ok -> accept(ctx, r.info)
            is VerifyResult.Rejected -> lock(ctx, r.msg)
            is VerifyResult.Unavailable -> {
                offline = true
                val i = info
                if (i != null && isExpired(i)) lock(ctx, "Key đã hết hạn")
            }
        }
    }

    private fun accept(ctx: Context, i: LicenseInfo) {
        Prefs.putStr(ctx, "key", i.key)
        Prefs.putStr(ctx, "license_cache", i.toJson())
        skew = i.serverTime - System.currentTimeMillis()
        offline = false
        message = ""
        info = i
        startLoop(ctx)
    }

    private fun lock(ctx: Context, msg: String) {
        loop?.cancel()
        loop = null
        Prefs.remove(ctx, "key")
        Prefs.remove(ctx, "license_cache")
        info = null
        offline = false
        message = msg
    }

    private fun isExpired(i: LicenseInfo): Boolean {
        if (i.lifetime) return false
        val e = i.expiresAt ?: return false
        return e <= System.currentTimeMillis() + skew
    }

    private fun loadCache(ctx: Context): LicenseInfo? {
        val s = Prefs.str(ctx, "license_cache")
        if (s.isEmpty()) return null
        return try { LicenseInfo.parse(JSONObject(s)) } catch (e: Exception) { null }
    }

    private fun startLoop(ctx: Context) {
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                delay(Config.RECHECK_MS)
                recheck(ctx)
            }
        }
    }
}

// =====================================================================
// Shizuku (chạy lệnh quyền shell)
// =====================================================================

data class ExecResult(val code: Int, val out: String)

object ShizukuHelper {
    const val REQUEST_CODE = 4242
    const val PKG = "moe.shizuku.privileged.api"

    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    fun state(ctx: Context): State {
        val running = try { Shizuku.pingBinder() } catch (e: Throwable) { false }
        if (running) {
            val ok = try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
            return if (ok) State.READY else State.NO_PERMISSION
        }
        val installed = try {
            ctx.packageManager.getPackageInfo(PKG, 0)
            true
        } catch (e: Exception) {
            false
        }
        return if (installed) State.NOT_RUNNING else State.NOT_INSTALLED
    }

    fun request() {
        try { Shizuku.requestPermission(REQUEST_CODE) } catch (e: Throwable) { }
    }

    /** Chạy 1 lệnh shell với quyền shell (qua Shizuku). Gọi trên luồng nền. */
    fun exec(cmd: String): ExecResult {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = p.inputStream.bufferedReader().use { it.readText() }
            val err = p.errorStream.bufferedReader().use { it.readText() }
            val code = p.waitFor()
            ExecResult(code, (out + err).trim())
        } catch (e: Throwable) {
            ExecResult(-1, e.cause?.message ?: e.message ?: "Lỗi Shizuku")
        }
    }
}

// =====================================================================
// Các tinh chỉnh hệ thống (cần Shizuku)
// =====================================================================

object Tweaks {
    data class ToggleDef(val id: String, val title: String, val desc: String)

    val toggles = listOf(
        ToggleDef("touches", "Hiện điểm chạm", "Hiện chấm tại nơi ngón tay chạm, giúp thấy cảm ứng có bị trễ/mất không."),
        ToggleDef("pointer", "Hiện tọa độ & vệt chạm", "Thanh thông tin cảm ứng trên cùng màn hình (tọa độ, tốc độ)."),
        ToggleDef("anim", "Tắt hoạt ảnh hệ thống", "Mở/đóng app và chuyển cảnh tức thì, máy phản hồi nhanh hơn."),
        ToggleDef("longpress", "Nhấn giữ nhanh (250 ms)", "Rút ngắn thời gian nhận biết nhấn giữ (mặc định 400 ms)."),
        ToggleDef("samsung", "Tăng độ nhạy cảm ứng (Samsung)", "Chỉ có tác dụng trên máy Samsung (hữu ích khi dán cường lực)."),
        ToggleDef("hz", "Ép tần số quét cao nhất", "Giữ màn hình ở tần số quét tối đa của máy (nếu hãng cho phép).")
    )

    fun readToggles(): Map<String, Boolean> {
        val r = ShizukuHelper.exec(
            "settings get system show_touches; " +
                "settings get system pointer_location; " +
                "settings get global window_animation_scale; " +
                "settings get secure long_press_timeout; " +
                "settings get secure high_touch_sensitivity_enable; " +
                "settings get system min_refresh_rate"
        )
        val lines = r.out.lines().map { it.trim() }
        fun g(i: Int): String = if (i < lines.size) lines[i] else "null"
        return mapOf(
            "touches" to (g(0) == "1"),
            "pointer" to (g(1) == "1"),
            "anim" to ((g(2).toFloatOrNull() ?: 1f) == 0f),
            "longpress" to ((g(3).toIntOrNull() ?: 400) in 1..300),
            "samsung" to (g(4) == "1"),
            "hz" to ((g(5).toFloatOrNull() ?: 0f) >= 90f)
        )
    }

    fun set(id: String, on: Boolean, maxHz: Float): ExecResult {
        val v = if (on) 1 else 0
        return when (id) {
            "touches" -> ShizukuHelper.exec("settings put system show_touches $v")
            "pointer" -> ShizukuHelper.exec("settings put system pointer_location $v")
            "anim" -> {
                val s = if (on) "0" else "1"
                ShizukuHelper.exec(
                    "settings put global window_animation_scale $s; " +
                        "settings put global transition_animation_scale $s; " +
                        "settings put global animator_duration_scale $s"
                )
            }
            "longpress" -> ShizukuHelper.exec("settings put secure long_press_timeout ${if (on) 250 else 400}")
            "samsung" -> ShizukuHelper.exec("settings put secure high_touch_sensitivity_enable $v")
            "hz" -> {
                if (on) {
                    val h = maxHz.toInt().coerceAtLeast(60)
                    ShizukuHelper.exec(
                        "settings put system min_refresh_rate ${h}.0; " +
                            "settings put system peak_refresh_rate ${h}.0"
                    )
                } else {
                    ShizukuHelper.exec(
                        "settings delete system min_refresh_rate; " +
                            "settings delete system peak_refresh_rate"
                    )
                }
            }
            else -> ExecResult(-1, "Không rõ tinh chỉnh: $id")
        }
    }

    // ---------- Độ phân giải & DPI ----------

    data class DisplayInfo(
        val physW: Int, val physH: Int,
        val curW: Int, val curH: Int,
        val physDpi: Int, val curDpi: Int
    ) {
        val sizeOverridden: Boolean get() = curW != physW || curH != physH
        val dpiOverridden: Boolean get() = curDpi != physDpi
    }

    fun readDisplay(): DisplayInfo? {
        val r = ShizukuHelper.exec("wm size; wm density")
        val phys = Regex("""Physical size:\s*(\d+)x(\d+)""").find(r.out) ?: return null
        val pw = phys.groupValues[1].toInt()
        val ph = phys.groupValues[2].toInt()
        val ov = Regex("""Override size:\s*(\d+)x(\d+)""").find(r.out)
        val cw = ov?.groupValues?.get(1)?.toInt() ?: pw
        val ch = ov?.groupValues?.get(2)?.toInt() ?: ph
        val pd = Regex("""Physical density:\s*(\d+)""").find(r.out)?.groupValues?.get(1)?.toInt() ?: return null
        val cd = Regex("""Override density:\s*(\d+)""").find(r.out)?.groupValues?.get(1)?.toInt() ?: pd
        return DisplayInfo(pw, ph, cw, ch, pd, cd)
    }

    fun sizeCmd(w: Int, h: Int) = "wm size ${w}x${h}"
    fun densityCmd(d: Int) = "wm density $d"
}

// =====================================================================
// Thông tin máy (đo thật từ hệ thống)
// =====================================================================

data class Health(
    val tempC: Float,
    val level: Int,
    val charging: Boolean,
    val powerSave: Boolean,
    val thermalLevel: Int,      // 0 mát, 1 hơi ấm, 2 nóng, 3 rất nóng
    val thermalText: String,
    val ramFreeMb: Long,
    val ramTotalMb: Long,
    val freeGb: Double,
    val hz: Float,
    val score: Int,
    val warnings: List<String>
)

fun maxRefreshRate(ctx: Context): Float {
    return try {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val d = wm.defaultDisplay
        val modes = d.supportedModes
        var m = d.refreshRate
        for (x in modes) if (x.refreshRate > m) m = x.refreshRate
        m
    } catch (e: Exception) {
        60f
    }
}

fun currentRefreshRate(ctx: Context): Float {
    return try {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.refreshRate
    } catch (e: Exception) {
        60f
    }
}

fun readHealth(ctx: Context): Health {
    val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val rawLevel = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val level = if (rawLevel >= 0 && scale > 0) rawLevel * 100 / scale else -1
    val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL

    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val powerSave = pm.isPowerSaveMode

    var tl = 0
    if (Build.VERSION.SDK_INT >= 29) {
        tl = when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> 0
            PowerManager.THERMAL_STATUS_LIGHT -> 1
            PowerManager.THERMAL_STATUS_MODERATE -> 2
            else -> 3
        }
    }
    if (temp >= 45f) tl = maxOf(tl, 3) else if (temp >= 41f) tl = maxOf(tl, 2) else if (temp >= 38f) tl = maxOf(tl, 1)
    val tt = when (tl) {
        0 -> "Mát"
        1 -> "Hơi ấm"
        2 -> "Nóng"
        else -> "Rất nóng"
    }

    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)

    val freeGb = try {
        StatFs(Environment.getDataDirectory().path).availableBytes / 1_000_000_000.0
    } catch (e: Exception) {
        0.0
    }
    val hz = currentRefreshRate(ctx)

    val w = ArrayList<String>()
    var score = 100
    if (tl == 1) { score -= 10; w.add("Máy hơi ấm, nên nghỉ 1–2 phút nếu thấy tụt FPS.") }
    if (tl == 2) { score -= 30; w.add("Máy đang NÓNG: hệ thống sẽ giảm hiệu năng, game dễ giật. Tháo ốp, cho máy nguội.") }
    if (tl >= 3) { score -= 50; w.add("Máy RẤT NÓNG: hãy dừng chơi để máy nguội.") }
    if (powerSave) { score -= 25; w.add("Tiết kiệm pin đang BẬT: giới hạn CPU và tần số quét. Hãy tắt đi.") }
    if (level in 0..19 && !charging) { score -= 10; w.add("Pin dưới 20%, máy dễ bị hạn chế hiệu năng.") }
    if (freeGb in 0.01..3.0) { score -= 10; w.add("Bộ nhớ trống dưới 3 GB, game có thể tải/lưu chậm.") }
    val ramFree = mi.availMem / 1_048_576
    val ramTotal = mi.totalMem / 1_048_576
    if (ramTotal > 0 && ramFree * 100 / ramTotal < 15) { score -= 10; w.add("RAM trống còn ít: hãy đóng bớt app chạy nền.") }

    return Health(temp, level, charging, powerSave, tl, tt, ramFree, ramTotal, freeGb, hz, maxOf(0, score), w)
}

// =====================================================================
// Dịch vụ chạy nền + khởi động cùng máy
// =====================================================================

class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Touch Technology", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Touch Technology đang chạy")
            .setContentText("Giữ app hoạt động nền, kiểm tra key định kỳ")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        Model.start(applicationContext)
        return START_STICKY
    }

    companion object {
        const val CHANNEL = "tt_keepalive"
        const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
            } catch (e: Exception) { }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, KeepAliveService::class.java)) } catch (e: Exception) { }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.bool(c, "bg", true) &&
            Prefs.str(c, "key").isNotEmpty()
        ) {
            KeepAliveService.start(c)
        }
    }
}

// =====================================================================
// Nhạc (đặt file .mp3 trong assets, tự nhận)
// =====================================================================

data class Track(val file: String, val title: String)

object Music {
    val tracks = mutableStateListOf<Track>()
    var index by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var pos by mutableIntStateOf(0)
    var dur by mutableIntStateOf(0)
    var loopOne by mutableStateOf(false)
    var shuffle by mutableStateOf(false)

    private var mp: MediaPlayer? = null
    private var app: Context? = null

    fun load(ctx: Context) {
        if (app != null) return
        val c = ctx.applicationContext
        app = c
        val names = try { c.assets.list("")?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        names
            .filter { n ->
                val l = n.lowercase()
                l.endsWith(".mp3") || l.endsWith(".m4a") || l.endsWith(".wav")
            }
            .sorted()
            .forEach { tracks.add(Track(it, titleOf(it))) }
        if (tracks.isNotEmpty()) prepare(0, false)
    }

    /** Bỏ phần sau " | " hoặc " _ " (tên tải từ YouTube hay có đuôi dài). */
    private fun titleOf(file: String): String {
        var s = file.substringBeforeLast('.')
        for (sep in listOf(" | ", " _ ", "｜")) {
            val k = s.indexOf(sep)
            if (k > 0) s = s.substring(0, k)
        }
        val t = s.replace('_', ' ').trim()
        return if (t.isEmpty()) file else t
    }

    private fun prepare(i: Int, autoplay: Boolean) {
        val c = app ?: return
        if (i !in tracks.indices) return
        index = i
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        try {
            val afd = c.assets.openFd(tracks[i].file)
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            p.setWakeMode(c, PowerManager.PARTIAL_WAKE_LOCK)
            p.setOnCompletionListener { player ->
                if (loopOne) {
                    player.seekTo(0)
                    player.start()
                } else {
                    next()
                }
            }
            p.prepare()
            mp = p
            dur = p.duration
            pos = 0
            if (autoplay) p.start()
            playing = autoplay
        } catch (e: Exception) {
            mp = null
            playing = false
            dur = 0
            pos = 0
        }
    }

    fun toggle() {
        val p = mp ?: return
        try {
            if (p.isPlaying) {
                p.pause()
                playing = false
            } else {
                p.start()
                playing = true
            }
        } catch (e: Exception) { }
    }

    fun play(i: Int) = prepare(i, true)

    fun next() {
        if (tracks.isEmpty()) return
        var n = (index + 1) % tracks.size
        if (shuffle && tracks.size > 1) {
            do { n = (0 until tracks.size).random() } while (n == index)
        }
        prepare(n, true)
    }

    fun previous() {
        if (tracks.isEmpty()) return
        if (pos > 3000) { seek(0); return }
        prepare((index - 1 + tracks.size) % tracks.size, true)
    }

    fun seek(ms: Int) {
        val p = mp ?: return
        try {
            p.seekTo(ms.coerceIn(0, maxOf(dur, 1)))
            pos = ms
        } catch (e: Exception) { }
    }

    fun tick() {
        val p = mp ?: return
        try {
            pos = p.currentPosition
            playing = p.isPlaying
        } catch (e: Exception) { }
    }
}

// =====================================================================
// Độ nhạy Free Fire (công thức theo màn hình / tần số quét / lối chơi)
// =====================================================================

data class PhoneSpec(val name: String, val inches: Double, val hz: Int)

object PhoneDB {
    val all = listOf(
        PhoneSpec("Samsung Galaxy S25 Ultra", 6.9, 120),
        PhoneSpec("Samsung Galaxy S25+", 6.7, 120),
        PhoneSpec("Samsung Galaxy S25", 6.2, 120),
        PhoneSpec("Samsung Galaxy S24 Ultra", 6.8, 120),
        PhoneSpec("Samsung Galaxy S24+", 6.7, 120),
        PhoneSpec("Samsung Galaxy S24", 6.2, 120),
        PhoneSpec("Samsung Galaxy S23 Ultra", 6.8, 120),
        PhoneSpec("Samsung Galaxy S23", 6.1, 120),
        PhoneSpec("Samsung Galaxy S22", 6.1, 120),
        PhoneSpec("Samsung Galaxy A55", 6.6, 120),
        PhoneSpec("Samsung Galaxy A54", 6.4, 120),
        PhoneSpec("Samsung Galaxy A35", 6.6, 120),
        PhoneSpec("Samsung Galaxy A34", 6.6, 120),
        PhoneSpec("Samsung Galaxy A25", 6.5, 120),
        PhoneSpec("Samsung Galaxy A15", 6.5, 90),
        PhoneSpec("Samsung Galaxy A14", 6.6, 90),
        PhoneSpec("Xiaomi 14", 6.4, 120),
        PhoneSpec("Xiaomi 13T Pro", 6.7, 144),
        PhoneSpec("Redmi Note 13 Pro", 6.7, 120),
        PhoneSpec("Redmi Note 13", 6.7, 120),
        PhoneSpec("Redmi Note 12", 6.7, 120),
        PhoneSpec("Redmi 13C", 6.7, 90),
        PhoneSpec("Redmi 12", 6.8, 90),
        PhoneSpec("Redmi 12C", 6.7, 60),
        PhoneSpec("POCO F6", 6.7, 120),
        PhoneSpec("POCO F5", 6.7, 120),
        PhoneSpec("POCO X6 Pro", 6.7, 120),
        PhoneSpec("POCO X5 Pro", 6.7, 120),
        PhoneSpec("OPPO Reno11", 6.7, 120),
        PhoneSpec("OPPO Reno8", 6.4, 90),
        PhoneSpec("OPPO A78", 6.4, 90),
        PhoneSpec("OPPO A58", 6.7, 90),
        PhoneSpec("Realme 11 Pro", 6.7, 120),
        PhoneSpec("Realme C55", 6.7, 90),
        PhoneSpec("Realme Narzo 60", 6.4, 90),
        PhoneSpec("vivo V30", 6.8, 120),
        PhoneSpec("vivo V29", 6.8, 120),
        PhoneSpec("vivo V27", 6.8, 120),
        PhoneSpec("vivo Y36", 6.6, 90),
        PhoneSpec("vivo Y27", 6.6, 90),
        PhoneSpec("vivo Y17s", 6.6, 60),
        PhoneSpec("Google Pixel 9 Pro XL", 6.8, 120),
        PhoneSpec("Google Pixel 9", 6.3, 120),
        PhoneSpec("Google Pixel 8 Pro", 6.7, 120),
        PhoneSpec("Google Pixel 8", 6.2, 120),
        PhoneSpec("Google Pixel 7a", 6.1, 90),
        PhoneSpec("Google Pixel 6a", 6.1, 60),
        PhoneSpec("OnePlus 12", 6.8, 120),
        PhoneSpec("OnePlus 11", 6.7, 120),
        PhoneSpec("ASUS ROG Phone 8", 6.8, 165),
        PhoneSpec("ASUS ROG Phone 7", 6.8, 165),
        PhoneSpec("RedMagic 9 Pro", 6.8, 120),
        PhoneSpec("Infinix Note 30", 6.8, 120),
        PhoneSpec("Infinix Hot 40", 6.8, 90),
        PhoneSpec("Tecno Spark 20", 6.6, 90)
    )
}

enum class PlayStyle(val label: String, val detail: String) {
    BALANCED("Cân bằng", "Phù hợp đa số người chơi, dễ làm quen."),
    DRAG("Kéo tâm", "Nhạy cao để kéo tâm nhanh lên đầu."),
    STEADY("Ổn định", "Thấp hơn để tâm chắc, hợp bắn xa / ngắm kỹ.")
}

data class Sens(
    val general: Int, val redDot: Int, val scope2x: Int,
    val scope4x: Int, val awm: Int, val freeLook: Int
)

object SensEngine {
    private fun clamp(v: Double): Int = Math.round(v).toInt().coerceIn(10, 100)

    fun suggest(inches: Double, hz: Int, style: PlayStyle): Sens {
        var g = 92.0
        if (inches <= 5.5) g += 3          // màn nhỏ: ít chỗ vuốt => cần nhạy hơn
        if (inches >= 8.0) g -= 8          // máy tính bảng: quãng vuốt dài => hạ xuống
        if (hz >= 120) g -= 2              // quét cao: tâm mượt hơn nên không cần quá nhạy
        when (style) {
            PlayStyle.BALANCED -> Unit
            PlayStyle.DRAG -> g += 4
            PlayStyle.STEADY -> g -= 6
        }
        return Sens(
            general = clamp(g),
            redDot = clamp(g - 4),
            scope2x = clamp(g - 9),
            scope4x = clamp(g - 16),
            awm = clamp(g - 30),
            freeLook = clamp(g - 36)
        )
    }
}

/** Ước lượng thông số máy đang dùng: kích thước màn hình (inch) và tần số quét. */
fun detectPhone(ctx: Context): PhoneSpec {
    var inches = 6.5
    try {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        val w = dm.widthPixels / dm.xdpi.toDouble()
        val h = dm.heightPixels / dm.ydpi.toDouble()
        val d = sqrt(w * w + h * h)
        if (d in 3.5..14.0) inches = Math.round(d * 10) / 10.0
    } catch (e: Exception) { }
    val hz = maxRefreshRate(ctx).toInt().coerceAtLeast(60)
    return PhoneSpec("${Build.MANUFACTURER} ${Build.MODEL}", inches, hz)
}
