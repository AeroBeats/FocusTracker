package com.focustracker.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focustracker.data.Category
import com.focustracker.data.FocusSession
import com.focustracker.detector.FocusMode
import com.focustracker.detector.FocusState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: FocusViewModel) {
    val context = LocalContext.current

    val categories by vm.categories.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val elapsed by vm.elapsedMs.collectAsState()
    val focusState by vm.focusState.collectAsState()
    val score by vm.score.collectAsState()
    val mode by vm.mode.collectAsState()
    val selectedId by vm.selectedCategoryId.collectAsState()

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var usageGranted by remember { mutableStateOf(hasUsageStatsPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                usageGranted = hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("专注记录", style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.height(16.dp))
            StatusCard(elapsedMs = elapsed, focusState = focusState, score = score, mode = mode)

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isRunning) {
                        vm.stop()
                    } else {
                        if (!cameraGranted) {
                            cameraLauncher.launch(Manifest.permission.CAMERA)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.start()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isRunning) "结束专注" else "开始专注")
            }

            Spacer(Modifier.height(24.dp))
            PermissionSection(
                cameraGranted = cameraGranted,
                notifGranted = notifGranted,
                usageGranted = usageGranted,
                onRequestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                onRequestNotification = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onOpenUsageSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    )
                },
            )

            Spacer(Modifier.height(24.dp))
            CategorySection(
                categories = categories,
                selectedId = selectedId,
                onSelect = vm::selectCategory,
                onAdd = vm::addCategory,
            )

            Spacer(Modifier.height(24.dp))
            SessionSection(sessions)
        }
    }
}

@Composable
private fun StatusCard(
    elapsedMs: Long,
    focusState: FocusState,
    score: Float,
    mode: FocusMode,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("计时", style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(elapsedMs), style = MaterialTheme.typography.displayMedium)

            Spacer(Modifier.height(12.dp))
            Row {
                InfoLabel("状态", focusState.name)
                Spacer(Modifier.width(16.dp))
                InfoLabel("模式", mode.name)
                Spacer(Modifier.width(16.dp))
                InfoLabel("评分", String.format("%.2f", score))
            }
        }
    }
}

@Composable
private fun InfoLabel(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PermissionSection(
    cameraGranted: Boolean,
    notifGranted: Boolean,
    usageGranted: Boolean,
    onRequestCamera: () -> Unit,
    onRequestNotification: () -> Unit,
    onOpenUsageSettings: () -> Unit,
) {
    Text("权限", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    PermissionRow("摄像头", cameraGranted, if (cameraGranted) null else onRequestCamera)
    PermissionRow("通知", notifGranted, if (notifGranted) null else onRequestNotification)
    PermissionRow("使用情况访问", usageGranted, if (usageGranted) null else onOpenUsageSettings)
}

@Composable
private fun PermissionRow(name: String, granted: Boolean, onAction: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f))
        Text(if (granted) "已授权" else "未授权", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onAction != null) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onAction) { Text("授权") }
        }
    }
}

@Composable
private fun CategorySection(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onAdd: (String) -> Unit,
) {
    Text("分类", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    var input by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("新分类") },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onAdd(input); input = "" }) { Text("添加") }
    }

    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text("未分类") },
        )
        categories.forEach { c ->
            FilterChip(
                selected = selectedId == c.id,
                onClick = { onSelect(c.id) },
                label = { Text(c.name) },
            )
        }
    }
}

@Composable
private fun SessionSection(sessions: List<FocusSession>) {
    Text("历史记录", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (sessions.isEmpty()) {
        Text("暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        sessions.take(20).forEach { s ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        formatSessionTime(s.startTime),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "专注 ${formatDuration(s.focusedDurationMs)} · 分心 ${s.distractedCount} 次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private fun formatSessionTime(ms: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
