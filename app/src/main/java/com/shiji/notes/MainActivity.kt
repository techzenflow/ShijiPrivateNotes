package com.shiji.notes

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.shiji.notes.data.*
import com.shiji.notes.security.Security
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var selectedVersion by remember { mutableStateOf<NoteVersionEntity?>(null) }
    val notes by dao.observeNotes().collectAsState(initial = emptyList())
    val vault by dao.observeVault().collectAsState(initial = emptyList())
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
                        } else {
                            masterKey = Security.unlock(context, masterInput.toCharArray())
                            unlocked = masterKey != null
                            if (!unlocked) error = "主密码不正确"
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
                    Row(Modifier.fillMaxWidth().background(Color(0xFF0E1726), RoundedCornerShape(18.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NavChip("笔记", page == "notes") { page = "notes" }
                        NavChip("时间线", page == "timeline") { page = "timeline" }
                        NavChip("密码库", page == "vault") { page = "vault" }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (error.isNotBlank()) Text(error, color=Cyan)
                    when (page) {
                        "notes" -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("我的笔记", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = { selected = null; showEditor = true }) { Icon(Icons.Default.Add, null); Text("新建") }
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(notes) { note ->
                                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().clickable { selected = note; showEditor = true }) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(note.title.ifBlank { "无标题" }, fontWeight = FontWeight.Bold)
                                            Text(note.body, color = Muted, maxLines = 2)
                                            Text("更新于 ${date(note.updatedAt)}", color = Cyan, style = MaterialTheme.typography.labelSmall)
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
                        "history" -> selected?.let { HistoryScreen(dao, it, onCompare = { selectedVersion = it }, onRestore = { v ->
                            scope.launch {
                                val current = dao.getNote(it.id) ?: return@launch
                                val next = dao.maxVersion(it.id) + 1
                                dao.insertVersion(NoteVersionEntity(noteId=it.id, versionNo=next, title=v.title, body=v.body, savedAt=System.currentTimeMillis(), reason="从 V${v.versionNo} 恢复"))
                                dao.updateNote(current.copy(title=v.title, body=v.body, updatedAt=System.currentTimeMillis()))
                            }
                        }) }
                        "vault" -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("私密账号", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = { showVaultEditor = true }) { Icon(Icons.Default.Add, null); Text("添加") }
                            }
                            val key = masterKey
                            LazyColumn {
                                items(vault) { item ->
                                    Card(Modifier.fillMaxWidth().padding(vertical=5.dp), colors=CardDefaults.cardColors(containerColor=Panel)) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(item.label, fontWeight=FontWeight.Bold)
                                            Text(item.site, color=Muted)
                                            var reveal by remember { mutableStateOf(false) }
                                            Text(if (reveal && key != null) runCatching { Security.decrypt(key, item.usernameCipher) + " · " + Security.decrypt(key, item.passwordCipher) }.getOrDefault("解密失败") else "••••••••", color=Cyan, modifier=Modifier.clickable { reveal = !reveal })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (showEditor) NoteEditor(selected, onClose = { showEditor = false }, onSave = { title, body ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    if (selected == null) {
                        val id = dao.insertNote(NoteEntity(title=title, body=body, createdAt=now, updatedAt=now))
                        dao.insertVersion(NoteVersionEntity(noteId=id, versionNo=1, title=title, body=body, savedAt=now, reason="创建笔记"))
                    } else {
                        val old = dao.getNote(selected!!.id)!!
                        if (old.title != title || old.body != body) {
                            val v = dao.maxVersion(old.id) + 1
                            dao.insertVersion(NoteVersionEntity(noteId=old.id, versionNo=v, title=old.title, body=old.body, savedAt=now, reason="编辑前自动快照"))
                            dao.updateNote(old.copy(title=title, body=body, updatedAt=now))
                            val nv = dao.maxVersion(old.id) + 1
                            dao.insertVersion(NoteVersionEntity(noteId=old.id, versionNo=nv, title=title, body=body, savedAt=now, reason="保存修改"))
                        }
                    }
                    showEditor = false
                }
            })
            if (showVaultEditor) VaultEditor(onClose={showVaultEditor=false}, onSave={label,site,user,pass,notesText ->
                val key = masterKey
                if (key != null) scope.launch {
                    val now=System.currentTimeMillis()
                    dao.insertVault(VaultItemEntity(label=label,site=site,usernameCipher=Security.encrypt(key,user),passwordCipher=Security.encrypt(key,pass),notesCipher=Security.encrypt(key,notesText),createdAt=now,updatedAt=now))
                    showVaultEditor=false
                }
            })
            selectedVersion?.let { VersionCompare(it, dao, onClose={selectedVersion=null}) }
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
@Composable private fun NoteEditor(note:NoteEntity?,onClose:()->Unit,onSave:(String,String)->Unit) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var body by remember { mutableStateOf(note?.body ?: "") }
    AlertDialog(onDismissRequest=onClose, title={Text(if(note==null)"新建笔记" else "编辑笔记")},
        text={Column { OutlinedTextField(title,{title=it},label={Text("标题")}); Spacer(Modifier.height(8.dp)); OutlinedTextField(body,{body=it},label={Text("正文")},minLines=6) }},
        confirmButton={TextButton(onClick={onSave(title,body)}){Text("保存")}}, dismissButton={TextButton(onClick=onClose){Text("取消")}})
}
@Composable private fun HistoryScreen(dao:NotesDao,note:NoteEntity,onCompare:(NoteVersionEntity)->Unit,onRestore:(NoteVersionEntity)->Unit) {
    val versions by dao.observeVersions(note.id).collectAsState(initial=emptyList())
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
@Composable private fun VersionCompare(version:NoteVersionEntity, dao:NotesDao, onClose:()->Unit) {
    var current by remember(version.id) { mutableStateOf<NoteEntity?>(null) }
    LaunchedEffect(version.id) { current = dao.getNote(version.noteId) }
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
@Composable private fun VaultEditor(onClose:()->Unit,onSave:(String,String,String,String,String)->Unit) {
    var label by remember { mutableStateOf("") }; var site by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onClose,title={Text("添加账号密码")},text={Column {
        OutlinedTextField(value=label,onValueChange={label=it},label={Text("名称")})
        OutlinedTextField(value=site,onValueChange={site=it},label={Text("网站 / App")})
        OutlinedTextField(value=user,onValueChange={user=it},label={Text("账号")})
        OutlinedTextField(value=pass,onValueChange={pass=it},label={Text("密码")})
        OutlinedTextField(value=note,onValueChange={note=it},label={Text("备注")})
    }},confirmButton={TextButton(onClick={onSave(label,site,user,pass,note)}){Text("加密保存")}},dismissButton={TextButton(onClick=onClose){Text("取消")}})
}
private fun date(ms:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
