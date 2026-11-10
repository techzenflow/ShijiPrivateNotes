package com.shiji.notes

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.shiji.notes.data.*
import com.shiji.notes.security.Security
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.SecureRandom

private val Bg = Color(0xFF080D18)
private val Panel = Color(0xFF121C2D)
private val Cyan = Color(0xFF42D9FF)
private val Muted = Color(0xFF91A4BD)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8,13,24)
        window.navigationBarColor = android.graphics.Color.rgb(8,13,24)
        setContent { ShijiApp() }
    }
}

@Composable
fun ShijiApp() {
    val context = LocalContext.current
    val dao = remember { ShijiDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var masterKey by remember { mutableStateOf<ByteArray?>(null) }
    var masterInput by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(Security.isConfigured(context)) }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("notes") }
    var selected by remember { mutableStateOf<NoteEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showVaultEditor by remember { mutableStateOf(false) }
    var showPasswordGenerator by remember { mutableStateOf(false) }
    var generatedPassword by remember { mutableStateOf("") }
    var editingVaultId by remember { mutableStateOf<Long?>(null) }
    var vaultQuery by remember { mutableStateOf("") }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf("") }
    var pendingDeleteVaultId by remember { mutableStateOf<Long?>(null) }
    var selectedVersion by remember { mutableStateOf<NoteVersionEntity?>(null) }
    val rawNotes by dao.observeNotes().collectAsState(initial = emptyList())
    val notes = remember(rawNotes, masterKey) { masterKey?.let { key -> rawNotes.map { it.decryptFields(key) } } ?: emptyList() }
    var noteQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    val visibleNotes = remember(notes, noteQuery, favoritesOnly) { notes.filter { it.matchesQuery(noteQuery) && (!favoritesOnly || it.isFavorite) } }
    val rawVault by dao.observeVault().collectAsState(initial = emptyList())
    val vault = remember(rawVault, masterKey) {
        masterKey?.let { key -> rawVault.map { item ->
            item.copy(
                label = Security.decryptNoteField(key, item.label),
                site = Security.decryptNoteField(key, item.site)
            )
        } } ?: emptyList()
    }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val password = pendingExportPassword
            pendingExportPassword = ""
            scope.launch(Dispatchers.IO) {
                try {
                    val key = masterKey ?: throw IllegalStateException("主密钥不可用")
                    val backupBytes = BackupCodec.create(dao.getAllNotes(), dao.getAllVersions(), dao.observeVault().first(), key, password.toCharArray())
                    context.contentResolver.openOutputStream(uri)?.use { it.write(backupBytes) }
                        ?: throw IllegalStateException("无法写入备份文件")
                    withContext(Dispatchers.Main) { backupStatus = "加密备份已保存。请妥善保管备份密码。" }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { backupStatus = "备份失败：" + (e.message ?: "未知错误") }
                }
            }
        } else pendingExportPassword = ""
    }
    val openBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { readBackupBytes(it, 100 * 1024 * 1024) }
                    ?: throw IllegalStateException("无法读取备份文件")
                withContext(Dispatchers.Main) {
                    pendingRestoreBytes = bytes
                    restorePassword = ""
                    showRestorePasswordDialog = true
                    backupStatus = "文件已读取，请输入创建备份时设置的密码。"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { backupStatus = "读取失败：" + (e.message ?: "文件无效") }
            }
        }
    }
    val visibleVault = remember(vault, vaultQuery) {
        vault.filter { it.label.contains(vaultQuery, ignoreCase = true) || it.site.contains(vaultQuery, ignoreCase = true) }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Bg, surface = Panel, primary = Cyan, secondary = Color(0xFF8A7CFF),
        onBackground = Color(0xFFEAF3FF), onSurface = Color(0xFFEAF3FF)
    )) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            if (!unlocked) {
                LockScreen(configured, masterInput, { masterInput = it }, error, {
                    try {
                        if (!configured) {
                            Security.setupMaster(context, masterInput.toCharArray())
                            configured = true
                            masterKey = Security.unlock(context, masterInput.toCharArray())
                            unlocked = masterKey != null
                            if (masterKey != null) scope.launch { migrateLegacyNotes(dao, masterKey!!) }
                        } else {
                            masterKey = Security.unlock(context, masterInput.toCharArray())
                            unlocked = masterKey != null
                            if (!unlocked) error = "主密码不正确" else masterKey?.let { key -> scope.launch { migrateLegacyNotes(dao, key) } }
                        }
                        masterInput = ""
                    } catch (e: Exception) { error = e.message ?: "设置失败" }
                }, {
                    try {
                        if (!configured || !Security.biometricConfigured(context)) {
                            error = "请先使用主密码解锁，再在首页启用指纹解锁"
                        } else {
                            val can = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            if (can != BiometricManager.BIOMETRIC_SUCCESS) error = "请先在系统设置中录入指纹"
                            else {
                                val cipher = Security.biometricDecryptCipher(context)
                                    ?: throw IllegalStateException("指纹密钥不可用，请使用主密码登录后重新启用")
                                val prompt = BiometricPrompt(context as FragmentActivity, ContextCompat.getMainExecutor(context),
                                    object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            try {
                                                val c = result.cryptoObject?.cipher ?: throw IllegalStateException("认证结果无效")
                                                masterKey = Security.finishBiometricUnlock(context, c)
                                                unlocked = true
                                                scope.launch { migrateLegacyNotes(dao, masterKey!!) }
                                                error = ""
                                            } catch (e: Exception) {
                                                error = "指纹密钥失效，请使用主密码登录后重新启用"
                                                runCatching { Security.disableBiometric(context) }
                                            }
                                        }
                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { error = errString.toString() }
                                    })
                                prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("时迹身份验证")
                                    .setSubtitle("使用已绑定指纹解锁").setNegativeButtonText("取消")
                                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
                                    BiometricPrompt.CryptoObject(cipher))
                            }
                        }
                    } catch (e: Exception) { error = e.message ?: "指纹解锁暂不可用" }
                })
            } else {
                Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111E30)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x223FCBEF))
                    ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("时迹", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Cyan)
                            Text("私人笔记 · 本地保险库", color = Muted, style = MaterialTheme.typography.labelMedium)
                        }
                        Row {
                            TextButton(onClick = {
                                val key = masterKey
                                if (key == null) error = "主密钥不可用"
                                else if (Security.biometricConfigured(context)) {
                                    runCatching { Security.disableBiometric(context) }
                                    error = "已关闭指纹解锁"
                                } else {
                                    try {
                                        val can = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                                        if (can != BiometricManager.BIOMETRIC_SUCCESS) error = "请先在系统设置中录入指纹"
                                        else {
                                            val cipher = Security.createBiometricCipher()
                                            val prompt = BiometricPrompt(context as FragmentActivity, ContextCompat.getMainExecutor(context),
                                                object : BiometricPrompt.AuthenticationCallback() {
                                                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                        try {
                                                            val c = result.cryptoObject?.cipher ?: throw IllegalStateException("认证结果无效")
                                                            Security.enableBiometric(context, key, c)
                                                            error = "指纹解锁已启用"
                                                        } catch (e: Exception) { error = e.message ?: "启用指纹失败" }
                                                    }
                                                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { error = errString.toString() }
                                                })
                                            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("启用指纹解锁")
                                                .setSubtitle("验证指纹以绑定主密钥").setNegativeButtonText("取消")
                                                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
                                                BiometricPrompt.CryptoObject(cipher))
                                        }
                                    } catch (e: Exception) { error = e.message ?: "无法启用指纹" }
                                }
                            }) { Text(if (Security.biometricConfigured(context)) "关闭指纹" else "启用指纹") }
                            TextButton(onClick = { unlocked = false; masterKey?.fill(0); masterKey = null }) { Text("锁定") }
                        }
                    }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).background(Color(0xFF0E1726), RoundedCornerShape(18.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NavChip("笔记", page == "notes") { page = "notes" }
                        NavChip("时间线", page == "timeline") { page = "timeline" }
                        NavChip("密码库", page == "vault") { page = "vault" }
                        NavChip("回收站", page == "trash") { page = "trash" }
                        NavChip("备份", page == "backup") { page = "backup" }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (error.isNotBlank()) Text(error, color=Cyan)
                    when (page) {
                        "notes" -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("我的笔记", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = { selected = null; showEditor = true }) { Icon(Icons.Default.Add, null); Text("新建") }
                            }
                            OutlinedTextField(
                                value = noteQuery,
                                onValueChange = { noteQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("搜索标题或正文（解锁后本地搜索）") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false }, label = { Text("全部 ${notes.size}") })
                                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true }, label = { Text("收藏 ${notes.count { it.isFavorite }}") })
                                Spacer(Modifier.weight(1f))
                                Text("置顶优先", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(visibleNotes) { note ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { selected = note; showEditor = true }) {
                                        Column(Modifier.padding(16.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(note.title.ifBlank { "无标题" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                if (note.isPinned) Text("已置顶", color = Cyan, style = MaterialTheme.typography.labelSmall)
                                            }
                                            if (note.category.isNotBlank() || note.tags.isNotBlank()) {
                                                Text(listOf(note.category, note.tags.takeIf { it.isNotBlank() }?.let { "#$it" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "), color = Cyan, style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text(note.body, color = Muted, maxLines = 2)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("更新于 ${date(note.updatedAt)}", color = Cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                                                Row {
                                                    TextButton(onClick = { scope.launch { rawNotes.firstOrNull { it.id == note.id }?.let { dao.updateNote(it.copy(isPinned = !it.isPinned)) } } }) { Text(if (note.isPinned) "取消置顶" else "置顶") }
                                                    TextButton(onClick = { scope.launch { rawNotes.firstOrNull { it.id == note.id }?.let { dao.updateNote(it.copy(isFavorite = !it.isFavorite)) } } }) { Text(if (note.isFavorite) "已收藏" else "收藏", color = if (note.isFavorite) Cyan else Muted) }
                                                    TextButton(onClick = { scope.launch { rawNotes.firstOrNull { it.id == note.id }?.let { dao.updateNote(it.copy(deletedAt = System.currentTimeMillis())) } } }) {
                                                        Icon(Icons.Default.Delete, contentDescription = null); Text("删除")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "timeline" -> {
                            Text("选择笔记查看历史版本", style = MaterialTheme.typography.titleLarge)
                            LazyColumn {
                                items(notes) { note ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selected = note; page = "history" }, colors = CardDefaults.cardColors(containerColor = Panel)) {
                                        Text(note.title, Modifier.padding(16.dp))
                                    }
                                }
                            }
                        }
                        "history" -> selected?.let { note -> masterKey?.let { key -> HistoryScreen(dao, note, key, onCompare = { selectedVersion = it }, onRestore = { v ->
                            scope.launch {
                                val key = masterKey ?: return@launch
                                val current = dao.getNote(note.id) ?: return@launch
                                val now = System.currentTimeMillis()
                                val next = dao.maxVersion(note.id) + 1
                                dao.insertVersion(NoteVersionEntity(noteId=note.id, versionNo=next, title=v.title, body=v.body, savedAt=now, reason="从 V${v.versionNo} 恢复").encryptFields(key))
                                dao.updateNote(current.decryptFields(key).copy(title=v.title, body=v.body, updatedAt=now).encryptFields(key))
                            }
                        }) } }
                        "trash" -> {
                            val trash by dao.observeTrash().collectAsState(initial = emptyList())
                            val key = masterKey
                            Text("回收站", style = MaterialTheme.typography.titleLarge)
                            Text("删除的笔记会保留在本机，可恢复或永久删除", color = Muted, style = MaterialTheme.typography.bodySmall)
                            LazyColumn {
                                items(trash) { raw ->
                                    val note = key?.let { runCatching { raw.decryptFields(it) }.getOrNull() }
                                    if (note != null) Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(note.title.ifBlank { "无标题" }, fontWeight = FontWeight.Bold)
                                            Text(note.body, color = Muted, maxLines = 2)
                                            Row {
                                                TextButton(onClick = { scope.launch { dao.updateNote(raw.copy(deletedAt = null)) } }) { Text("恢复") }
                                                TextButton(onClick = { scope.launch { dao.deleteVersions(raw.id); dao.deleteNote(raw) } }) { Text("永久删除", color = Color(0xFFFF8792)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "backup" -> {
                            Text("加密备份与恢复", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(6.dp))
                            Text("备份文件由独立备份密码保护。恢复时会将笔记、历史版本、回收站记录和密码库重新加密到当前主密钥下。", color = Muted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(14.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("创建加密备份", fontWeight = FontWeight.Bold)
                                    Text("请设置至少 10 位的备份密码。它独立于应用主密码，忘记后无法恢复备份。", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = { showBackupPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("导出加密备份")
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("从备份恢复", fontWeight = FontWeight.Bold)
                                    Text("恢复会替换当前笔记、历史版本、回收站和密码库。请先确保已有可用备份。", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { openBackupLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("选择备份文件")
                                    }
                                }
                            }
                            if (backupStatus.isNotBlank()) Text(backupStatus, color = Cyan, style = MaterialTheme.typography.bodySmall)
                            Text("当前版本暂不包含附件文件；附件加密与备份会在附件模块完成后接入。", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        "vault" -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("私密账号", style = MaterialTheme.typography.titleLarge)
                                Row {
                                    TextButton(onClick = { showPasswordGenerator = true }) { Text("密码生成器") }
                                    TextButton(onClick = { editingVaultId = null; showVaultEditor = true }) { Text("＋ 添加") }
                                }
                            }
                            OutlinedTextField(
                                value = vaultQuery, onValueChange = { vaultQuery = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("搜索名称或网站") },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            val key = masterKey
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(visibleVault) { item ->
                                    Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=Panel)) {
                                        Column(Modifier.padding(14.dp)) {
                                            var reveal by remember(item.id) { mutableStateOf(false) }
                                            Text(item.label, fontWeight=FontWeight.Bold)
                                            Text(item.site, color=Muted)
                                            val credentials = key?.let { k -> runCatching {
                                                Security.decrypt(k, item.usernameCipher) to Security.decrypt(k, item.passwordCipher)
                                            }.getOrNull() }
                                            Text(
                                                if (reveal && credentials != null) "账号：${credentials.first}\n密码：${credentials.second}" else "••••••••••••",
                                                color=Cyan, modifier=Modifier.clickable { reveal = !reveal }
                                            )
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(onClick = {
                                                    val value = credentials?.first.orEmpty()
                                                    if (value.isNotEmpty()) copySecret(context, scope, value, "账号")
                                                }, enabled = credentials?.first?.isNotEmpty() == true) { Text("复制账号") }
                                                TextButton(onClick = {
                                                    val value = credentials?.second.orEmpty()
                                                    if (value.isNotEmpty()) copySecret(context, scope, value, "密码")
                                                }, enabled = credentials?.second?.isNotEmpty() == true) { Text("复制密码") }
                                                Spacer(Modifier.weight(1f))
                                                TextButton(onClick = { editingVaultId = item.id; showVaultEditor = true }) { Text("编辑") }
                                                TextButton(onClick = { pendingDeleteVaultId = item.id }) { Text("删除", color=Color(0xFFFF8792)) }
                                            }
                                        }
                                    }
                                }
                                if (visibleVault.isEmpty()) item { Text("没有匹配的账号记录", color=Muted, modifier=Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
            }
            if (showEditor) NoteEditor(selected, onClose = { showEditor = false }, onSave = { title, body, category, tags ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    if (selected == null) {
                        val key = masterKey ?: return@launch
                        val id = dao.insertNote(NoteEntity(title=title, body=body, category=category, tags=tags, createdAt=now, updatedAt=now).encryptFields(key))
                        dao.insertVersion(NoteVersionEntity(noteId=id, versionNo=1, title=title, body=body, savedAt=now, reason="创建笔记").encryptFields(key))
                    } else {
                        val key = masterKey ?: return@launch
                        val old = dao.getNote(selected!!.id)!!.decryptFields(key)
                        if (old.title != title || old.body != body || old.category != category || old.tags != tags) {
                            val v = dao.maxVersion(old.id) + 1
                            dao.insertVersion(NoteVersionEntity(noteId=old.id, versionNo=v, title=old.title, body=old.body, savedAt=now, reason="编辑前自动快照").encryptFields(key))
                            dao.updateNote(old.copy(title=title, body=body, category=category, tags=tags, updatedAt=now).encryptFields(key))
                            val nv = dao.maxVersion(old.id) + 1
                            dao.insertVersion(NoteVersionEntity(noteId=old.id, versionNo=nv, title=title, body=body, savedAt=now, reason="保存修改").encryptFields(key))
                        }
                    }
                    showEditor = false
                }
            })
            if (showVaultEditor) {
                val key = masterKey
                val editingItem = editingVaultId?.let { id -> vault.firstOrNull { it.id == id } }
                if (key != null) VaultEditor(item=editingItem, key=key, initialPassword=generatedPassword, onClose={ showVaultEditor=false; editingVaultId=null }, onSave={label,site,user,pass,notesText ->
                    scope.launch {
                        val now=System.currentTimeMillis()
                        val existing = editingVaultId?.let { id -> rawVault.firstOrNull { it.id == id } }
                        if (existing == null) {
                            dao.insertVault(VaultItemEntity(label=Security.encryptNoteField(key,label),site=Security.encryptNoteField(key,site),usernameCipher=Security.encrypt(key,user),passwordCipher=Security.encrypt(key,pass),notesCipher=Security.encrypt(key,notesText),createdAt=now,updatedAt=now))
                        } else {
                            dao.updateVault(existing.copy(label=Security.encryptNoteField(key,label),site=Security.encryptNoteField(key,site),usernameCipher=Security.encrypt(key,user),passwordCipher=Security.encrypt(key,pass),notesCipher=Security.encrypt(key,notesText),updatedAt=now))
                        }
                        showVaultEditor=false
                        editingVaultId=null
                        generatedPassword=""
                    }
                })
            }
            if (showBackupPasswordDialog) AlertDialog(
                onDismissRequest = { showBackupPasswordDialog = false },
                title = { Text("设置备份密码") },
                text = {
                    Column {
                        OutlinedTextField(value=exportPassword,onValueChange={exportPassword=it},label={Text("备份密码（至少 10 位）")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Text("此密码不会上传或保存在应用中。请单独妥善保管。", color=Muted, style=MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(enabled=exportPassword.length >= 10,onClick={
                        pendingExportPassword=exportPassword
                        exportPassword=""
                        showBackupPasswordDialog=false
                        createBackupLauncher.launch("ShijiPrivateNotes-backup.sjbackup")
                    }) { Text("继续") }
                },
                dismissButton = { TextButton(onClick={showBackupPasswordDialog=false}) { Text("取消") } }
            )
            if (showRestorePasswordDialog) AlertDialog(
                onDismissRequest = { showRestorePasswordDialog=false; pendingRestoreBytes=null },
                title = { Text("输入备份密码") },
                text = { OutlinedTextField(value=restorePassword,onValueChange={restorePassword=it},label={Text("备份密码")},singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation()) },
                confirmButton = {
                    TextButton(enabled=restorePassword.isNotEmpty(),onClick={showRestorePasswordDialog=false;showRestoreConfirm=true}) { Text("下一步") }
                },
                dismissButton = { TextButton(onClick={showRestorePasswordDialog=false;pendingRestoreBytes=null}) { Text("取消") } }
            )
            if (showRestoreConfirm) AlertDialog(
                onDismissRequest = { showRestoreConfirm=false },
                title = { Text("确认替换当前数据？") },
                text = { Text("恢复成功后，当前笔记、历史版本、回收站和密码库将被备份内容替换。导入前会先验证备份密码、文件完整性和记录关联。") },
                confirmButton = {
                    TextButton(onClick={
                        val bytes=pendingRestoreBytes
                        val key=masterKey
                        showRestoreConfirm=false
                        if(bytes!=null && key!=null) scope.launch(Dispatchers.IO) {
                            try {
                                val data=BackupCodec.restore(bytes,restorePassword.toCharArray(),key)
                                dao.replaceAllData(data.notes,data.versions,data.vault)
                                withContext(Dispatchers.Main) {
                                    backupStatus="恢复完成：笔记 ${data.notes.size} 篇，历史版本 ${data.versions.size} 条，密码库 ${data.vault.size} 条。"
                                    pendingRestoreBytes=null
                                    restorePassword=""
                                }
                            } catch(e:Exception) {
                                withContext(Dispatchers.Main) { backupStatus="恢复失败：" + (e.message ?: "备份无效"); pendingRestoreBytes=null; restorePassword="" }
                            }
                        }
                    }) { Text("确认恢复", color=Color(0xFFFF8792)) }
                },
                dismissButton = { TextButton(onClick={showRestoreConfirm=false}) { Text("取消") } }
            )
            if (showPasswordGenerator) PasswordGeneratorDialog(
                onClose = { showPasswordGenerator = false },
                onUse = { password ->
                    generatedPassword = password
                    editingVaultId = null
                    showPasswordGenerator = false
                    showVaultEditor = true
                }
            )
            pendingDeleteVaultId?.let { id ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteVaultId = null },
                    title = { Text("删除这条账号记录？") },
                    text = { Text("删除后将无法恢复。") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch { rawVault.firstOrNull { it.id == id }?.let { dao.deleteVault(it) } }
                            pendingDeleteVaultId = null
                        }) { Text("永久删除", color = Color(0xFFFF8792)) }
                    },
                    dismissButton = { TextButton(onClick = { pendingDeleteVaultId = null }) { Text("取消") } }
                )
            }
            selectedVersion?.let { v -> masterKey?.let { key -> VersionCompare(v, dao, key, onClose={selectedVersion=null}) } }
        }
    }
}

@Composable private fun NavChip(label: String, active: Boolean, click: () -> Unit) {
    Surface(
        color = if (active) Color(0xFF16445A) else Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = click)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            color = if (active) Color(0xFF73E5FF) else Muted,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
@Composable private fun LockScreen(configured:Boolean, input:String, onInput:(String)->Unit, error:String, submit:()->Unit, biometric:()->Unit) {
    val context = LocalContext.current
    val biometricReady = configured && Security.biometricConfigured(context)
    Box(
        modifier = Modifier.fillMaxSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(Color(0xFF0B1424), Bg, Color(0xFF070B13))
            )
        ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(76.dp).background(Color(0xFF123247), RoundedCornerShape(24.dp)),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Cyan, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("时迹", style = MaterialTheme.typography.displaySmall, color = Color(0xFFEAF6FF), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("让每一次记录，都有迹可循", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111E30)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3342D9FF))
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    if (biometricReady) {
                        Text("欢迎回来", style = MaterialTheme.typography.titleLarge, color = Color(0xFFEAF3FF), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("使用指纹快速、安全地解锁私人空间", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(22.dp))
                        Button(
                            onClick = biometric,
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12617A))
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(29.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("指纹优先解锁", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = Color(0xFF29394D))
                            Text("  或使用主密码  ", color = Muted, style = MaterialTheme.typography.labelSmall)
                            HorizontalDivider(Modifier.weight(1f), color = Color(0xFF29394D))
                        }
                        Spacer(Modifier.height(14.dp))
                    } else {
                        Text(if (configured) "主密码解锁" else "创建私人保险库", style = MaterialTheme.typography.titleLarge, color = Color(0xFFEAF3FF), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (configured) "输入主密码进入你的本地空间" else "设置主密码后，即可启用指纹快捷解锁",
                            color = Muted, style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(18.dp))
                    }

                    OutlinedTextField(
                        value = input,
                        onValueChange = onInput,
                        label = { Text(if (configured) "主密码" else "设置主密码（至少 8 位）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Muted) }
                    )
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(error, color = Color(0xFFFF8792), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = submit,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                    ) {
                        Text(if (configured) "使用主密码解锁" else "创建保险库", color = Color(0xFF06121D), fontWeight = FontWeight.Bold)
                    }
                    if (!biometricReady && configured) {
                        Spacer(Modifier.height(12.dp))
                        Text("指纹尚未绑定。进入后可在首页启用指纹解锁。", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF5F7894), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("数据仅保存在本机 · 私密内容受主密码保护", color = Color(0xFF71859E), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable private fun NoteEditor(note: NoteEntity?, onClose: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var category by remember(note?.id) { mutableStateOf(note?.category ?: "") }
    var tags by remember(note?.id) { mutableStateOf(note?.tags ?: "") }
    var body by remember(note?.id) { mutableStateOf(TextFieldValue(note?.body ?: "")) }
    var preview by remember { mutableStateOf(false) }
    val words = remember(body.text) { body.text.trim().split(Regex("\\s+")).count { it.isNotBlank() } }
    val chars = body.text.length

    fun insertMarkdown(prefix: String, suffix: String = "", linePrefix: Boolean = false) {
        body = MarkdownEditing.apply(body, prefix, suffix, linePrefix)
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, shape = RoundedCornerShape(0.dp)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (note == null) "新建笔记" else "编辑笔记", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("本地加密保存 · Markdown 编辑器", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onClose) { Text("取消") }
                    Button(onClick = { onSave(title.trim(), body.text, category.trim(), tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(",")) }, enabled = title.isNotBlank() || body.text.isNotBlank()) { Text("保存") }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("给这篇笔记起个标题") },
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value=category,onValueChange={category=it},modifier=Modifier.weight(1f),label={Text("分类")},singleLine=true)
                    OutlinedTextField(value=tags,onValueChange={tags=it},modifier=Modifier.weight(1f),label={Text("标签（逗号分隔）")},singleLine=true)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(5.dp)
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    EditorTool("H1") { insertMarkdown("# ", linePrefix = true) }
                    EditorTool("H2") { insertMarkdown("## ", linePrefix = true) }
                    EditorTool("粗体") { insertMarkdown("**", "**") }
                    EditorTool("斜体") { insertMarkdown("*", "*") }
                    EditorTool("删除线") { insertMarkdown("~~", "~~") }
                    EditorTool("列表") { insertMarkdown("- ", linePrefix = true) }
                    EditorTool("任务") { insertMarkdown("- [ ] ", linePrefix = true) }
                    EditorTool("引用") { insertMarkdown("> ", linePrefix = true) }
                    EditorTool("代码") { insertMarkdown("\u0060", "\u0060") }
                    EditorTool("代码块") { insertMarkdown("\u0060\u0060\u0060\n", "\n\u0060\u0060\u0060") }
                    EditorTool("分割线") { insertMarkdown("---") }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !preview, onClick = { preview = false }, label = { Text("编辑") })
                    FilterChip(selected = preview, onClick = { preview = true }, label = { Text("预览") })
                    Spacer(Modifier.weight(1f))
                    Text("$words 词 · $chars 字", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically))
                }
                Spacer(Modifier.height(8.dp))
                if (preview) {
                    Surface(Modifier.fillMaxWidth().weight(1f), color = Panel, shape = RoundedCornerShape(16.dp)) {
                        MarkdownPreview(body.text, Modifier.fillMaxSize().padding(16.dp))
                    }
                } else {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = { Text("从这里开始记录……\n\n支持 Markdown：标题、列表、任务清单、引用和代码块。") },
                        shape = RoundedCornerShape(16.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        minLines = 12
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("提示：选中正文后使用工具栏可格式化选区；未选中时会在光标处插入标记。", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun EditorTool(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Cyan)
    }
}

@Composable private fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (markdown.isBlank()) {
            Text("暂无内容", color = Muted)
        } else {
            var inCode = false
            markdown.lines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.trim().startsWith("\u0060\u0060\u0060") -> { inCode = !inCode }
                    inCode -> Text(line, color = Color(0xFF9FE8C3), modifier = Modifier.fillMaxWidth().background(Color(0xFF09121D), RoundedCornerShape(6.dp)).padding(8.dp), style = MaterialTheme.typography.bodySmall)
                    line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    line.startsWith("> ") -> Text(line.removePrefix("> "), color = Cyan, modifier = Modifier.fillMaxWidth().background(Color(0x221C9AB8), RoundedCornerShape(6.dp)).padding(10.dp))
                    line.startsWith("- [ ] ") -> Text("□  " + line.removePrefix("- [ ] "), color = Color(0xFFEAF3FF))
                    line.startsWith("- [x] ", ignoreCase = true) -> Text("☑  " + line.substringAfter("] "), color = Muted)
                    line.startsWith("- ") || line.startsWith("* ") -> Text("•  " + line.drop(2), color = Color(0xFFEAF3FF))
                    line == "---" || line == "***" -> HorizontalDivider(color = Color(0xFF34465C))
                    line.isBlank() -> Spacer(Modifier.height(3.dp))
                    else -> Text(line, color = Color(0xFFEAF3FF), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable private fun HistoryScreen(dao:NotesDao,note:NoteEntity,key:ByteArray,onCompare:(NoteVersionEntity)->Unit,onRestore:(NoteVersionEntity)->Unit) {
    val rawVersions by dao.observeVersions(note.id).collectAsState(initial=emptyList())
    val versions = remember(rawVersions, key) { rawVersions.map { it.decryptFields(key) } }
    Column(Modifier.fillMaxSize().padding(top=12.dp)) {
        Text("「${note.title}」版本时间线",style=MaterialTheme.typography.titleLarge)
        LazyColumn {
            items(versions) { v ->
                Card(Modifier.fillMaxWidth().padding(vertical=5.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("V${v.versionNo} · ${v.reason}",fontWeight=FontWeight.Bold)
                        Text(date(v.savedAt),color=Muted)
                        Text(v.title, color=Cyan)
                        Text(v.body,maxLines=3,color=Muted)
                        Row { TextButton(onClick={onCompare(v)}){Text("查看/对比")}; TextButton(onClick={onRestore(v)}){Text("恢复此版本")} }
                    }
                }
            }
        }
    }
}
@Composable private fun VersionCompare(version:NoteVersionEntity, dao:NotesDao, key:ByteArray, onClose:()->Unit) {
    var current by remember(version.id) { mutableStateOf<NoteEntity?>(null) }
    LaunchedEffect(version.id) { current = dao.getNote(version.noteId)?.decryptFields(key) }
    val oldLines = version.body.lines()
    val newLines = (current?.body ?: "").lines()
    val oldSet = oldLines.toSet()
    val newSet = newLines.toSet()
    AlertDialog(onDismissRequest=onClose,title={Text("版本对比 · V${version.versionNo}")},
        text={Column(Modifier.heightIn(max=480.dp)) {
            Text("历史版本：${date(version.savedAt)}",color=Muted)
            Text("历史标题：${version.title}",fontWeight=FontWeight.Bold)
            Text("当前标题：${current?.title ?: "读取中"}",fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("正文差异", color=Cyan, fontWeight=FontWeight.Bold)
            LazyColumn {
                items(oldLines) { line ->
                    if (line !in newSet) Text("− $line", color=Color(0xFFFF808A), modifier=Modifier.fillMaxWidth().background(Color(0x22FF6677)).padding(4.dp))
                }
                items(newLines) { line ->
                    if (line !in oldSet) Text("+ $line", color=Color(0xFF6FE3A1), modifier=Modifier.fillMaxWidth().background(Color(0x226FE3A1)).padding(4.dp))
                }
                if (oldSet == newSet) item { Text("正文没有差异", color=Muted) }
            }
        }},
        confirmButton={TextButton(onClick=onClose){Text("关闭")}})
}
@Composable private fun PasswordGeneratorDialog(onClose: () -> Unit, onUse: (String) -> Unit) {
    var length by remember { mutableIntStateOf(20) }
    var generated by remember { mutableStateOf(generateStrongPassword(length)) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("安全密码生成器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("密码长度：$length 位", color = Muted)
                Slider(value = length.toFloat(), onValueChange = {
                    length = it.toInt().coerceIn(8, 64)
                    generated = generateStrongPassword(length)
                }, valueRange = 8f..64f, steps = 55)
                OutlinedTextField(value = generated, onValueChange = {}, readOnly = true, singleLine = true)
                Text("包含大小写字母、数字和常用符号；使用安全随机数生成。", color = Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { generated = generateStrongPassword(length) }) { Text("重新生成") }
            }
        },
        confirmButton = { TextButton(onClick = { onUse(generated) }) { Text("填入密码库") } },
        dismissButton = { TextButton(onClick = onClose) { Text("关闭") } }
    )
}

private fun generateStrongPassword(length: Int): String {
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lower = "abcdefghijkmnopqrstuvwxyz"
    val digits = "23456789"
    val symbols = "!@#$%^&*()-_=+[]{}?"
    val pools = listOf(upper, lower, digits, symbols)
    val random = SecureRandom()
    val chars = mutableListOf<Char>()
    pools.forEach { chars += it[random.nextInt(it.length)] }
    val all = pools.joinToString("")
    repeat((length - chars.size).coerceAtLeast(0)) { chars += all[random.nextInt(all.length)] }
    for (i in chars.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val temp = chars[i]; chars[i] = chars[j]; chars[j] = temp
    }
    return chars.joinToString("")
}

@Composable private fun VaultEditor(item: VaultItemEntity?, key: ByteArray, initialPassword: String, onClose:()->Unit,onSave:(String,String,String,String,String)->Unit) {
    var label by remember(item?.id) { mutableStateOf(item?.label ?: "") }
    var site by remember(item?.id) { mutableStateOf(item?.site ?: "") }
    var user by remember(item?.id) { mutableStateOf(item?.let { runCatching { Security.decrypt(key,it.usernameCipher) }.getOrDefault("") } ?: "") }
    var pass by remember(item?.id, initialPassword) { mutableStateOf(item?.let { runCatching { Security.decrypt(key,it.passwordCipher) }.getOrDefault("") } ?: initialPassword) }
    var note by remember(item?.id) { mutableStateOf(item?.let { runCatching { Security.decrypt(key,it.notesCipher) }.getOrDefault("") } ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest=onClose,
        title={Text(if (item == null) "添加账号密码" else "编辑账号密码")},
        text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            OutlinedTextField(value=label,onValueChange={label=it},label={Text("名称")},singleLine=true)
            OutlinedTextField(value=site,onValueChange={site=it},label={Text("网站 / App")},singleLine=true)
            OutlinedTextField(value=user,onValueChange={user=it},label={Text("账号")},singleLine=true)
            OutlinedTextField(value=pass,onValueChange={pass=it},label={Text("密码")},singleLine=true,visualTransformation=if(showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon={TextButton(onClick={showPassword=!showPassword}){Text(if(showPassword) "隐藏" else "显示")}})
            OutlinedTextField(value=note,onValueChange={note=it},label={Text("备注")},minLines=2)
        }},
        confirmButton={TextButton(onClick={onSave(label,site,user,pass,note)},enabled=label.isNotBlank() && pass.isNotBlank()){Text("加密保存")}},
        dismissButton={TextButton(onClick=onClose){Text("取消")}}
    )
}
private suspend fun migrateLegacyNotes(dao: NotesDao, key: ByteArray) {
    val notes = dao.getAllNotes()
    notes.forEach { note ->
        if (!note.title.startsWith("SHIJI1:") || !note.body.startsWith("SHIJI1:") ||
            !note.category.startsWith("SHIJI1:") || !note.tags.startsWith("SHIJI1:")) {
            dao.updateNote(note.copy(
                title = Security.encryptNoteField(key, note.title),
                body = Security.encryptNoteField(key, note.body),
                category = Security.encryptNoteField(key, note.category),
                tags = Security.encryptNoteField(key, note.tags)
            ))
        }
    }
    dao.observeVault().first().forEach { item ->
        if (!item.label.startsWith("SHIJI1:") || !item.site.startsWith("SHIJI1:")) {
            dao.updateVault(item.copy(
                label = Security.encryptNoteField(key, item.label),
                site = Security.encryptNoteField(key, item.site)
            ))
        }
    }
    dao.getAllVersions().forEach { version ->
        if (!version.title.startsWith("SHIJI1:") || !version.body.startsWith("SHIJI1:")) {
            dao.updateVersion(version.copy(
                title = Security.encryptNoteField(key, version.title),
                body = Security.encryptNoteField(key, version.body)
            ))
        }
    }
}
private fun date(ms:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

private fun copySecret(context: Context, scope: kotlinx.coroutines.CoroutineScope, value: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("时迹$label", value))
    scope.launch {
        delay(30_000)
        val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
        if (current == value) clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}

private fun readBackupBytes(input: java.io.InputStream, maxBytes: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "备份文件超过 100 MB 限制" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
