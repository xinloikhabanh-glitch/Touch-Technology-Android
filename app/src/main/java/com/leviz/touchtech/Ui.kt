package com.leviz.touchtech

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import kotlin.math.sqrt

// =====================================================================
// Màu sắc & thành phần dùng chung
// =====================================================================

object T {
    val bg = Color(0xFF080D17)
    val cyan = Color(0xFF29C7FF)
    val card = Color(0x12FFFFFF)
    val line = Color(0x1FFFFFFF)
    val good = Color(0xFF30D158)
    val warn = Color(0xFFFFC733)
    val bad = Color(0xFFFF6161)
    val dim = Color(0x99FFFFFF)
}

@Composable
fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(T.card)
            .border(1.dp, T.line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title.uppercase(),
            color = T.dim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        content()
    }
}

@Composable
fun Small(text: String, color: Color = T.dim) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 17.sp)
}

@Composable
fun Brand(subtitle: String) {
    Column {
        Text("TOUCH TECHNOLOGY", color = T.cyan, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = T.dim, fontSize = 14.sp)
    }
}

@Composable
fun KeyValue(k: String, v: String, vColor: Color = Color.White) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = T.dim, fontSize = 14.sp)
        Text(v, color = vColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RowScopeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0FFFFFFF))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = T.dim, fontSize = 11.sp)
    }
}

// =====================================================================
// Màn hình gốc
// =====================================================================

@Composable
fun TouchApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = T.cyan,
            onPrimary = Color.Black,
            background = T.bg,
            surface = T.bg
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = T.bg, contentColor = Color.White) {
            if (Model.info != null) MainTabs() else ActivationScreen()
        }
    }
}

@Composable
fun ActivationScreen() {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Prefs.str(ctx, "key")) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(T.bg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(T.card)
                .border(1.dp, T.line, RoundedCornerShape(26.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TOUCH TECHNOLOGY", color = T.cyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Nhập key để mở khoá ứng dụng", color = T.dim, fontSize = 14.sp)

            OutlinedTextField(
                value = key,
                onValueChange = { key = it.uppercase() },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("PREM-XXXX-XXXX-XXXX-XXXX") },
                textStyle = TextStyle(textAlign = TextAlign.Center, color = Color.White, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { Model.submit(ctx, key) })
            )

            Button(
                onClick = { Model.submit(ctx, key) },
                enabled = !Model.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (Model.busy) "Đang kiểm tra…" else "Mở khoá", fontWeight = FontWeight.SemiBold)
            }

            if (Model.message.isNotEmpty()) {
                Text(Model.message, color = T.bad, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun MainTabs() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Tự bật dịch vụ chạy nền khi đã mở khoá (người dùng có thể tắt trong mục Quyền)
    LaunchedEffect(Unit) {
        if (Prefs.bool(ctx, "bg", true)) KeepAliveService.start(ctx)
    }

    Scaffold(
        containerColor = T.bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D1422)) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("⚡") }, label = { Text("Chức năng") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Text("🎯") }, label = { Text("Độ nhạy FF") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("👤") }, label = { Text("Admin") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> FeaturesScreen()
                1 -> SensScreen()
                else -> AdminScreen()
            }
        }
    }
}

// =====================================================================
// Mục 1: Chức năng
// =====================================================================

@Composable
fun FeaturesScreen() {
    val ctx = LocalContext.current
    var shz by remember { mutableStateOf(ShizukuHelper.state(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            shz = ShizukuHelper.state(ctx)
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Brand("Đo & tối ưu máy khi chơi game")
        if (Model.offline) Small("Đang offline — dùng key đã lưu", T.warn)
        PermissionsPanel(shz)
        TweaksPanel(shz)
        DisplayPanel(shz)
        HealthPanel()
        TouchPanel()
        PingPanel()
        MusicPanel()
        TipsPanel()
        Small(
            "Lưu ý: app không thể làm phần cứng cảm ứng nhanh hơn mức máy hỗ trợ. " +
                "Các nút ở trên đổi cài đặt hệ thống thật (qua Shizuku), còn các thẻ đo cho bạn thấy kết quả."
        )
    }
}

// ---------- Quyền ----------

@Composable
fun PermissionsPanel(shz: ShizukuHelper.State) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var results by remember { mutableIntStateOf(0) }
    var stage by remember { mutableIntStateOf(3) }
    var bgOn by remember { mutableStateOf(Prefs.bool(ctx, "bg", true)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            tick++
        }
    }
    val refresh = tick   // đọc để Compose vẽ lại định kỳ

    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val batOk = pm.isIgnoringBatteryOptimizations(ctx.packageName)

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { results++ }
    val batLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { results++ }

    fun batIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${ctx.packageName}"))

    // Xin lần lượt: thông báo -> chạy nền -> Shizuku
    fun advance() {
        while (stage <= 2) {
            val s = stage
            stage = s + 1
            if (s == 0 && !notifOk && Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            if (s == 1 && !batOk) {
                try {
                    batLauncher.launch(batIntent())
                    return
                } catch (e: Exception) { }
            }
            if (s == 2 && shz == ShizukuHelper.State.NO_PERMISSION) {
                ShizukuHelper.request()
                return
            }
        }
    }

    LaunchedEffect(results) { if (results > 0) advance() }
    LaunchedEffect(Unit) {
        if (!Prefs.bool(ctx, "asked_all")) {
            Prefs.putBool(ctx, "asked_all", true)
            stage = 0
            advance()
        }
    }

    Panel("Quyền cần thiết") {
        PermRow("Thông báo", notifOk, "Cấp quyền") {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PermRow("Chạy nền (không bị hệ thống tắt)", batOk, "Cho phép") {
            try {
                ctx.startActivity(batIntent())
            } catch (e: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        when (shz) {
            ShizukuHelper.State.READY -> PermRow("Shizuku", true, "") { }
            ShizukuHelper.State.NO_PERMISSION -> PermRow("Shizuku (đang chạy, chưa cấp quyền)", false, "Cấp quyền") {
                ShizukuHelper.request()
            }
            ShizukuHelper.State.NOT_RUNNING -> {
                PermRow("Shizuku (đã cài, chưa bật)", false, "Mở Shizuku") {
                    val i = ctx.packageManager.getLaunchIntentForPackage(ShizukuHelper.PKG)
                    if (i != null) ctx.startActivity(i)
                }
                Small("Mở Shizuku và bấm Start (Gỡ lỗi không dây trên Android 11+, hoặc chạy lệnh adb), rồi quay lại đây.")
            }
            ShizukuHelper.State.NOT_INSTALLED -> {
                PermRow("Shizuku (chưa cài)", false, "Tải Shizuku") {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
                    )
                }
                Small("Shizuku cho app quyền đổi độ phân giải, DPI và các cài đặt cảm ứng.")
            }
        }

        HorizontalDivider(color = T.line)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Dịch vụ chạy nền", fontWeight = FontWeight.SemiBold)
                Small("Giữ app hoạt động khi thoát ra chơi game (hiện 1 thông báo nhỏ).")
            }
            Switch(
                checked = bgOn,
                onCheckedChange = {
                    bgOn = it
                    Prefs.putBool(ctx, "bg", it)
                    if (it) KeepAliveService.start(ctx) else KeepAliveService.stop(ctx)
                }
            )
        }
        OutlinedButton(onClick = { stage = 0; advance() }, modifier = Modifier.fillMaxWidth()) {
            Text("Cấp tất cả quyền")
        }
        if (refresh < 0) Small("")
    }
}

@Composable
fun PermRow(title: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ok) "✅ " else "⚠️ ")
            Text(title, fontSize = 14.sp)
        }
        if (!ok && action.isNotEmpty()) {
            Button(onClick = onClick) { Text(action, fontSize = 13.sp) }
        }
    }
}

// ---------- Tinh chỉnh cảm ứng ----------

@Composable
fun TweaksPanel(shz: ShizukuHelper.State) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var states by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var status by remember { mutableStateOf("") }
    val ready = shz == ShizukuHelper.State.READY
    val maxHz = remember { maxRefreshRate(ctx) }

    LaunchedEffect(shz) {
        if (ready) states = withContext(Dispatchers.IO) { Tweaks.readToggles() }
    }

    Panel("Tinh chỉnh cảm ứng & hiệu năng") {
        if (!ready) Small("Cần bật Shizuku và cấp quyền ở mục trên để dùng các nút này.", T.warn)

        Tweaks.toggles.forEach { t ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Small(t.desc)
                }
                Switch(
                    checked = states[t.id] == true,
                    enabled = ready,
                    onCheckedChange = { on ->
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Tweaks.set(t.id, on, maxHz) }
                            status = if (r.code == 0) {
                                "Đã ${if (on) "bật" else "tắt"}: ${t.title}"
                            } else {
                                "Lỗi: ${r.out}"
                            }
                            states = withContext(Dispatchers.IO) { Tweaks.readToggles() }
                        }
                    }
                )
            }
        }
        if (status.isNotEmpty()) Small(status, if (status.startsWith("Lỗi")) T.bad else T.good)
    }
}

// ---------- Độ phân giải & DPI ----------

@Composable
fun DisplayPanel(shz: ShizukuHelper.State) {
    val scope = rememberCoroutineScope()
    val ready = shz == ShizukuHelper.State.READY
    var info by remember { mutableStateOf<Tweaks.DisplayInfo?>(null) }
    var dpi by remember { mutableFloatStateOf(0f) }
    var pendingRevert by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(15) }
    var msg by remember { mutableStateOf("") }

    suspend fun reload() {
        val d = withContext(Dispatchers.IO) { Tweaks.readDisplay() }
        info = d
        if (d != null) dpi = d.curDpi.toFloat()
    }

    LaunchedEffect(shz) { if (ready) reload() }

    // Tự hoàn lại sau 15 giây nếu người dùng không bấm "Giữ" (phòng khi màn hình khó nhìn)
    LaunchedEffect(pendingRevert) {
        if (pendingRevert != null) {
            countdown = 15
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            val cmd = pendingRevert
            if (cmd != null) {
                withContext(Dispatchers.IO) { ShizukuHelper.exec(cmd) }
                pendingRevert = null
                reload()
            }
        }
    }

    fun applyWithRevert(apply: String, revert: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { ShizukuHelper.exec(apply) }
            if (r.code == 0) {
                pendingRevert = revert
                msg = ""
            } else {
                msg = "Lỗi: ${r.out}"
            }
        }
    }

    Panel("Độ phân giải & DPI màn hình") {
        val d = info
        if (!ready) {
            Small("Cần bật Shizuku và cấp quyền để đổi độ phân giải / DPI.", T.warn)
        } else if (d == null) {
            Small("Đang đọc thông tin màn hình…")
        } else {
            KeyValue("Gốc", "${d.physW}×${d.physH} · ${d.physDpi} dpi")
            KeyValue(
                "Hiện tại",
                "${d.curW}×${d.curH} · ${d.curDpi} dpi",
                if (d.sizeOverridden || d.dpiOverridden) T.warn else Color.White
            )

            Text("Giảm độ phân giải (tăng FPS, giảm nóng)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            listOf(listOf(100, 90, 80), listOf(70, 60, 50)).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p ->
                        val w = ((d.physW * p / 100) / 2) * 2
                        val h = ((d.physH * p / 100) / 2) * 2
                        OutlinedButton(
                            onClick = {
                                val revert = if (d.sizeOverridden) Tweaks.sizeCmd(d.curW, d.curH) else "wm size reset"
                                val apply = if (p == 100) "wm size reset" else Tweaks.sizeCmd(w, h)
                                applyWithRevert(apply, revert)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$p%", fontWeight = FontWeight.Bold)
                                Text("${w}×${h}", fontSize = 10.sp, color = T.dim)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = T.line)
            Text("Chỉnh DPI: ${dpi.roundToInt()}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Slider(
                value = dpi.coerceIn(d.physDpi * 0.5f, d.physDpi * 1.5f),
                onValueChange = { dpi = it },
                valueRange = (d.physDpi * 0.5f)..(d.physDpi * 1.5f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dpi = (dpi - 10f).coerceAtLeast(120f) }, modifier = Modifier.weight(1f)) { Text("−10") }
                OutlinedButton(onClick = { dpi += 10f }, modifier = Modifier.weight(1f)) { Text("+10") }
                Button(
                    onClick = {
                        val target = dpi.roundToInt().coerceIn(120, 900)
                        val revert = if (d.dpiOverridden) Tweaks.densityCmd(d.curDpi) else "wm density reset"
                        applyWithRevert(Tweaks.densityCmd(target), revert)
                    },
                    modifier = Modifier.weight(1.4f)
                ) { Text("Áp dụng DPI") }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { ShizukuHelper.exec("wm size reset; wm density reset") }
                        msg = "Đã trả độ phân giải và DPI về mặc định."
                        reload()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Trả về mặc định") }

            if (msg.isNotEmpty()) Small(msg, if (msg.startsWith("Lỗi")) T.bad else T.good)
            Small("Sau khi đổi sẽ hiện hộp thoại 15 giây: bấm Giữ để giữ lại, nếu không máy tự hoàn lại để tránh bị kẹt màn hình.")
        }
    }

    if (pendingRevert != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Giữ cài đặt hiển thị?") },
            text = { Text("Tự hoàn lại sau $countdown giây nếu bạn không bấm Giữ.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRevert = null
                    scope.launch { reload() }
                }) { Text("Giữ") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val c = pendingRevert
                    pendingRevert = null
                    scope.launch {
                        if (c != null) withContext(Dispatchers.IO) { ShizukuHelper.exec(c) }
                        reload()
                    }
                }) { Text("Hoàn lại") }
            }
        )
    }
}

// ---------- Tình trạng máy ----------

@Composable
fun HealthPanel() {
    val ctx = LocalContext.current
    var h by remember { mutableStateOf(readHealth(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            h = readHealth(ctx)
        }
    }
    val scoreColor = if (h.score >= 80) T.good else if (h.score >= 50) T.warn else T.bad
    val thermalColor = when (h.thermalLevel) { 0 -> T.good; 1 -> T.warn; else -> T.bad }

    Panel("Sẵn sàng chơi game") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${h.score}", color = scoreColor, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Small("điểm / 100")
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyValue("Nhiệt độ", "${h.thermalText} · ${"%.1f".format(h.tempC)}°C", thermalColor)
                KeyValue("Tiết kiệm pin", if (h.powerSave) "BẬT" else "Tắt", if (h.powerSave) T.bad else T.good)
                KeyValue("Pin", if (h.level < 0) "—" else "${h.level}%${if (h.charging) " ⚡" else ""}")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowScopeStat("Tần số quét", "${h.hz.roundToInt()} Hz", Modifier.weight(1f))
            RowScopeStat("RAM trống", "${h.ramFreeMb} MB", Modifier.weight(1f))
            RowScopeStat("Bộ nhớ trống", "${"%.0f".format(h.freeGb)} GB", Modifier.weight(1f))
        }
        h.warnings.forEach { Small("⚠️ $it", Color(0xFFFFE08A)) }
    }
}

// ---------- Đo tốc độ cảm ứng ----------

data class TouchStats(val hz: Double, val avgMs: Double, val jitterMs: Double, val samples: Int)

class TouchPadView(ctx: Context) : View(ctx) {
    var onStats: ((TouchStats) -> Unit)? = null

    private val stamps = ArrayList<Long>()
    private val path = Path()
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF29C7FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }
    private var touched = false

    init {
        setBackgroundColor(0x47000000)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!touched) canvas.drawText("Kéo ngón tay ở đây", width / 2f, height / 2f, hint)
        canvas.drawPath(path, trail)
    }

    private fun record(e: MotionEvent) {
        for (i in 0 until e.historySize) {
            stamps.add(e.getHistoricalEventTime(i))
            path.lineTo(e.getHistoricalX(i), e.getHistoricalY(i))
        }
        stamps.add(e.eventTime)
        path.lineTo(e.x, e.y)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)          // lấy mẫu thô, không gộp theo khung hình
                parent?.requestDisallowInterceptTouchEvent(true)
                stamps.clear()
                path.reset()
                path.moveTo(e.x, e.y)
                touched = true
                record(e)
            }
            MotionEvent.ACTION_MOVE -> {
                record(e)
                if (stamps.size % 10 == 0) publish()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                record(e)
                publish()
            }
        }
        invalidate()
        return true
    }

    private fun publish() {
        if (stamps.size < 5) return
        val gaps = ArrayList<Double>()
        for (i in 1 until stamps.size) {
            val d = (stamps[i] - stamps[i - 1]).toDouble()
            if (d in 0.0..100.0) gaps.add(d)       // bỏ các lúc ngón tay đứng yên
        }
        if (gaps.size < 4) return
        val mean = gaps.sum() / gaps.size
        if (mean <= 0.0) return
        var v = 0.0
        for (g in gaps) v += (g - mean) * (g - mean)
        v /= gaps.size
        onStats?.invoke(TouchStats(1000.0 / mean, mean, sqrt(v), stamps.size))
    }
}

@Composable
fun TouchPanel() {
    var st by remember { mutableStateOf<TouchStats?>(null) }

    Panel("Đo tốc độ cảm ứng") {
        AndroidView(
            factory = { c -> TouchPadView(c).also { v -> v.onStats = { s -> st = s } } },
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        val s = st
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowScopeStat("Hz lấy mẫu", if (s == null) "—" else "%.0f".format(s.hz), Modifier.weight(1f))
            RowScopeStat("ms / lần", if (s == null) "—" else "%.1f".format(s.avgMs), Modifier.weight(1f))
            RowScopeStat("ms dao động", if (s == null) "—" else "%.1f".format(s.jitterMs), Modifier.weight(1f))
        }
        val verdict: Pair<String, Color> = when {
            s == null -> "Chạm và kéo 1 ngón trong khung" to T.dim
            s.hz >= 200 -> "Cảm ứng rất nhanh" to T.good
            s.hz >= 100 -> "Cảm ứng nhanh" to T.good
            s.hz >= 55 -> "Cảm ứng tiêu chuẩn" to T.warn
            else -> "Cảm ứng đang bị chậm — kiểm tra nhiệt độ / tiết kiệm pin" to T.bad
        }
        Text(verdict.first, color = verdict.second, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Small("Số càng cao, ms và dao động càng thấp thì cảm ứng càng mượt. Đo lúc máy nóng / bật tiết kiệm pin để thấy khác biệt.")
    }
}

// ---------- Đo ping ----------

data class PingResult(
    val name: String, val avg: Double?, val best: Double?,
    val worst: Double?, val jitter: Double?, val lost: Int, val samples: Int
)

suspend fun runPing(name: String, url: String, samples: Int = 6): PingResult = withContext(Dispatchers.IO) {
    val times = ArrayList<Double>()
    var lost = 0
    for (i in 0..samples) {                         // lần 0 = khởi động kết nối, bỏ qua
        val t0 = System.nanoTime()
        var ok = false
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "HEAD"
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.useCaches = false
            c.instanceFollowRedirects = false
            val code = c.responseCode
            ok = code > 0
        } catch (e: Exception) { }
        if (i > 0) {
            if (ok) times.add((System.nanoTime() - t0) / 1_000_000.0) else lost++
        }
        delay(120)
    }
    if (times.isEmpty()) {
        PingResult(name, null, null, null, null, lost, samples)
    } else {
        var j = 0.0
        for (k in 1 until times.size) j += Math.abs(times[k] - times[k - 1])
        val jitter = if (times.size > 1) j / (times.size - 1) else 0.0
        PingResult(name, times.sum() / times.size, times.minOrNull(), times.maxOrNull(), jitter, lost, samples)
    }
}

@Composable
fun PingPanel() {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<PingResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    val targets = remember {
        listOf(
            "Google" to "https://www.gstatic.com/generate_204",
            "Cloudflare" to "https://www.cloudflare.com/cdn-cgi/trace",
            "Garena" to "https://www.garena.com",
            "Server của bạn" to (Config.API_BASE + "/api/verify")
        )
    }

    Panel("Đo ping mạng") {
        if (results.isEmpty() && !running) {
            Small("Đo độ trễ từ máy bạn tới vài máy chủ. Ping cao hoặc dao động lớn thì game online sẽ giật/trễ.")
        }
        results.forEach { r ->
            val col = when {
                r.avg == null -> T.bad
                r.avg < 60 -> T.good
                r.avg < 130 -> T.warn
                else -> T.bad
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontWeight = FontWeight.SemiBold)
                    if (r.best != null && r.worst != null && r.jitter != null) {
                        Small("min %.0f · max %.0f · dao động %.0f ms · mất %d/%d".format(r.best, r.worst, r.jitter, r.lost, r.samples))
                    } else {
                        Small("Không kết nối được", T.bad)
                    }
                }
                Text(
                    if (r.avg == null) "—" else "%.0f ms".format(r.avg),
                    color = col, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Button(
            onClick = {
                running = true
                results = emptyList()
                scope.launch {
                    for ((n, u) in targets) {
                        val r = runPing(n, u)
                        results = results + r
                    }
                    running = false
                }
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (running) "Đang đo…" else if (results.isEmpty()) "Đo ngay" else "Đo lại") }
    }
}

// ---------- Nhạc ----------

@Composable
fun MusicPanel() {
    LaunchedEffect(Unit) {
        while (true) {
            Music.tick()
            delay(500)
        }
    }
    fun fmt(ms: Int): String {
        val s = maxOf(0, ms) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    Panel("Nhạc") {
        if (Music.tracks.isEmpty()) {
            Small("Chưa có bài nhạc nào. Thêm file .mp3 vào repo rồi build lại.")
        } else {
            Text(Music.tracks[Music.index].title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
            Slider(
                value = Music.pos.toFloat().coerceIn(0f, maxOf(Music.dur, 1).toFloat()),
                onValueChange = { Music.seek(it.toInt()) },
                valueRange = 0f..maxOf(Music.dur, 1).toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Small(fmt(Music.pos))
                Small(fmt(Music.dur))
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { Music.shuffle = !Music.shuffle }) {
                    Text("🔀", color = if (Music.shuffle) T.cyan else T.dim, fontSize = 20.sp)
                }
                TextButton(onClick = { Music.previous() }) { Text("⏮", fontSize = 22.sp) }
                Button(onClick = { Music.toggle() }) { Text(if (Music.playing) "⏸" else "▶", fontSize = 22.sp) }
                TextButton(onClick = { Music.next() }) { Text("⏭", fontSize = 22.sp) }
                TextButton(onClick = { Music.loopOne = !Music.loopOne }) {
                    Text("🔂", color = if (Music.loopOne) T.cyan else T.dim, fontSize = 20.sp)
                }
            }
            if (Music.tracks.size > 1) {
                Music.tracks.forEachIndexed { i, t ->
                    Text(
                        (if (i == Music.index) "🔊 " else "🎵 ") + t.title,
                        color = if (i == Music.index) T.cyan else Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { Music.play(i) }
                            .padding(vertical = 6.dp)
                    )
                }
            }
            Small("Nhạc tiếp tục phát khi bạn thoát ra chơi game, hoà cùng âm thanh game.")
        }
    }
}

// ---------- Danh sách tối ưu ----------

@Composable
fun TipsPanel() {
    val ctx = LocalContext.current
    val tips = remember {
        listOf(
            "Tắt Tiết kiệm pin" to "Chế độ này giảm CPU và có thể hạ tần số quét, làm game giật.",
            "Tắt Tối ưu hoá pin cho game" to "Cài đặt → Ứng dụng → game → Pin → Không hạn chế.",
            "Bật Không làm phiền / Chế độ chơi game" to "Chặn thông báo bật lên giữa trận gây giật hình.",
            "Đóng ứng dụng chạy nền" to "Vuốt đóng các app nặng (video, mạng xã hội) để giải phóng RAM.",
            "Dùng Wi-Fi 5 GHz, ngồi gần router, tắt VPN" to "Giảm ping và dao động ping. Dùng thẻ 'Đo ping mạng' để so sánh trước/sau.",
            "Lau sạch màn hình, bỏ miếng dán dày" to "Màn hình bẩn hoặc miếng dán dày làm cảm ứng kém nhạy. Kiểm tra bằng thẻ đo cảm ứng.",
            "Giữ máy mát: không vừa sạc vừa chơi" to "Máy nóng sẽ bị hệ thống hạ hiệu năng. Theo dõi điểm ở thẻ 'Sẵn sàng chơi game'.",
            "Thử giảm độ phân giải xuống 80–90%" to "Giảm tải GPU, tăng FPS ổn định, máy mát hơn (dùng thẻ Độ phân giải ở trên)."
        )
    }
    Panel("Danh sách tối ưu") {
        tips.forEachIndexed { i, (title, detail) ->
            var done by remember { mutableStateOf(Prefs.bool(ctx, "tip_$i")) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        done = !done
                        Prefs.putBool(ctx, "tip_$i", done)
                    },
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(if (done) "✅" else "⬜", fontSize = 18.sp)
                Column {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Small(detail)
                }
            }
        }
        Small("Chạm để đánh dấu những bước bạn đã làm.")
    }
}

// =====================================================================
// Mục 2: Độ nhạy Free Fire
// =====================================================================

@Composable
fun SensScreen() {
    val ctx = LocalContext.current
    val detected = remember { detectPhone(ctx) }
    var name by remember { mutableStateOf(detected.name) }
    var inches by remember { mutableFloatStateOf(detected.inches.toFloat()) }
    var hz by remember { mutableIntStateOf(detected.hz) }
    var style by remember { mutableStateOf(PlayStyle.BALANCED) }
    var query by remember { mutableStateOf("") }

    val q = query.trim().lowercase()
    val found = if (q.isEmpty()) emptyList() else PhoneDB.all.filter { it.name.lowercase().contains(q) }.take(8)
    val inchesRounded = (inches * 10).roundToInt() / 10.0
    val s = SensEngine.suggest(inchesRounded, hz, style)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Brand("Gợi ý độ nhạy Free Fire theo dòng máy")

        Panel("Dòng máy") {
            Text(name, color = T.cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Màn hình ${"%.1f".format(inchesRounded)}\" · $hz Hz", color = T.dim)
            if (name != detected.name) {
                TextButton(onClick = {
                    name = detected.name
                    inches = detected.inches.toFloat()
                    hz = detected.hz
                }) { Text("Về máy của tôi (${detected.name})") }
            } else {
                Small("Đã tự nhận diện máy của bạn", T.good)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tìm dòng máy (vd: S24, Redmi Note 13, Pixel)") }
            )
            found.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            name = p.name
                            inches = p.inches.toFloat()
                            hz = p.hz
                            query = ""
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(p.name)
                    Small("${p.inches}\" · ${p.hz}Hz")
                }
            }

            HorizontalDivider(color = T.line)
            Text("Chỉnh tay kích thước màn hình: ${"%.1f".format(inchesRounded)} inch", fontSize = 14.sp)
            Slider(value = inches, onValueChange = { inches = it }, valueRange = 4.5f..13f)
            Text("Tần số quét", fontSize = 14.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(60, 90, 120, 144, 165).forEach { f ->
                    if (hz == f) {
                        Button(onClick = { hz = f }, modifier = Modifier.weight(1f)) { Text("$f", fontSize = 13.sp) }
                    } else {
                        OutlinedButton(onClick = { hz = f }, modifier = Modifier.weight(1f)) { Text("$f", fontSize = 13.sp) }
                    }
                }
            }
        }

        Panel("Lối chơi") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PlayStyle.values().forEach { ps ->
                    if (style == ps) {
                        Button(onClick = { style = ps }, modifier = Modifier.weight(1f)) { Text(ps.label, fontSize = 13.sp) }
                    } else {
                        OutlinedButton(onClick = { style = ps }, modifier = Modifier.weight(1f)) { Text(ps.label, fontSize = 13.sp) }
                    }
                }
            }
            Small(style.detail)
        }

        Panel("Độ nhạy đề xuất") {
            SensBar("Tổng quan", s.general)
            SensBar("Red Dot", s.redDot)
            SensBar("Ống ngắm 2x", s.scope2x)
            SensBar("Ống ngắm 4x", s.scope4x)
            SensBar("Ống ngắm AWM", s.awm)
            SensBar("Nhìn xung quanh", s.freeLook)
        }

        Panel("Cách dùng") {
            Text(
                "1. Vào Free Fire → Cài đặt → Độ nhạy, nhập các số ở trên.\n" +
                    "2. Vào phòng tập, bắn thử vào bia ở nhiều khoảng cách.\n" +
                    "3. Tâm văng quá đà thì giảm 3–5; kéo không tới thì tăng 3–5, chỉnh từng mục một.",
                fontSize = 13.sp
            )
            Small(
                "Đây là gợi ý khởi điểm tính theo kích thước màn hình, tần số quét và lối chơi. " +
                    "Free Fire không có bảng độ nhạy chính thức theo từng máy, thông số máy trong danh sách là gần đúng; " +
                    "hãy tinh chỉnh theo cảm giác của bạn."
            )
        }
    }
}

@Composable
fun SensBar(name: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontSize = 14.sp)
            Text("$value", color = T.cyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x1FFFFFFF))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(T.cyan)
            )
        }
    }
}

// =====================================================================
// Mục 3: Admin
// =====================================================================

@Composable
fun AdminScreen() {
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
    }

    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("contact", text))
        copied = true
    }

    fun openUrl(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Brand("Thông tin admin")

        Panel("Liên hệ") {
            ContactRow("Zalo", Config.ZALO, "Nhắn Zalo", { openUrl("https://zalo.me/" + Config.ZALO) }, { copy(Config.ZALO) })
            HorizontalDivider(color = T.line)
            ContactRow(
                "TikTok", "@" + Config.TIKTOK, "Mở TikTok",
                { openUrl("https://www.tiktok.com/@" + Config.TIKTOK) }, { copy("@" + Config.TIKTOK) }
            )
            if (copied) Small("Đã sao chép", T.good)
        }

        Panel("Giấy phép của bạn") {
            val i = Model.info
            if (i != null) {
                KeyValue("Gói", i.tier.uppercase())
                KeyValue("Hạn dùng", Model.expiryText(i))
                KeyValue("Thiết bị", if (i.deviceLock) "${i.devicesUsed}/${i.maxDevices}" else "Không giới hạn")
                KeyValue("Key", if (i.key.length > 10) i.key.take(4) + "••••" + i.key.takeLast(4) else i.key)
            }
            if (Model.offline) Small("Đang offline — dùng key đã lưu", T.warn)
            OutlinedButton(
                onClick = {
                    KeepAliveService.stop(ctx)
                    Model.logout(ctx)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Đăng xuất key", color = T.bad) }
        }

        Small("Touch Technology · v$version")
    }
}

@Composable
fun ContactRow(label: String, value: String, action: String, onOpen: () -> Unit, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Small(label)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onCopy) { Text("Sao chép") }
        Button(onClick = onOpen) { Text(action, fontSize = 13.sp) }
    }
}
