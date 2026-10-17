package com.pusula.service.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pusula.service.R
import com.pusula.service.core.SessionState
import com.pusula.service.data.model.guide.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ApplicationGuideEntry(session: SessionState) {
    val scope = remember(session.token, session.companyId) { GuideIdentity.scope(session.token, session.companyId) }
    var open by rememberSaveable(scope, session.role) { mutableStateOf(false) }
    TextButton(onClick = { open = true }, enabled = scope != null, modifier = Modifier.testTag("account.guide.open")) { Text("Uygulama Rehberi") }
    if (open && scope != null) key(scope, session.role) {
        ModalBottomSheet(onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null, contentWindowInsets = { WindowInsets.safeDrawing }) {
            GuideCenterScreen(scope, session.role, session.features, onClose = { open = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GuideCenterScreen(scope: String, role: String, features: Map<String, Boolean>, onClose: () -> Unit) {
    val context = LocalContext.current
    val progress = remember(context) { GuideProgressRepository(context) }
    var catalog by remember { mutableStateOf<GuideCatalog?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var unfinished by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var changed by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(attempt) {
        failed = false
        catalog = withContext(Dispatchers.IO) { runCatching { context.assets.open("app-guide.json").bufferedReader().use { GuideCatalog.decode(it.readText()) } }.getOrNull() }
        failed = catalog == null
    }
    val visible = catalog?.visible(role).orEmpty()
    val completed = remember(visible, changed) { visible.count { progress.read(scope, role, it).complete(it) } }
    val topic = visible.find { it.id == selected }
    fun closeLesson() {
        topic?.let { progress.save(scope, role, it, index) }
        selected = null; changed++
    }
    BackHandler { if (topic != null) closeLesson() else onClose() }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag("guide.screen"),
        topBar = {
            TopAppBar(title = { Text(topic?.title ?: "Uygulama Rehberi") }, actions = {
                TextButton(onClick = { if (topic != null) closeLesson() else onClose() }, modifier = Modifier.testTag("guide.close")) { Text("Kapat") }
            })
        },
        bottomBar = {
            if (topic != null) {
                val cursor = GuideCursor(topic.steps.size, index)
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${cursor.boundedIndex + 1} / ${cursor.count}", Modifier.align(Alignment.CenterHorizontally).testTag("guide.step.progress"))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(enabled = cursor.boundedIndex > 0, onClick = {
                                index = cursor.back().boundedIndex; progress.save(scope, role, topic, index)
                            }, modifier = Modifier.testTag("guide.back")) { Text("Geri") }
                            Button(onClick = {
                                progress.save(scope, role, topic, if (cursor.isLast) index else cursor.next().boundedIndex, completed = cursor.isLast)
                                if (cursor.isLast) { selected = null; changed++ } else index = cursor.next().boundedIndex
                            }, modifier = Modifier.testTag("guide.next")) { Text(if (cursor.isLast) "Bitir" else "Devam") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("guide.list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (topic != null) {
                val step = topic.steps[GuideCursor(topic.steps.size, index).boundedIndex]
                item { Text(topic.locations["android"].orEmpty(), color = MaterialTheme.colorScheme.primary) }
                item { Text(step.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("guide.step.title")) }
                item { Text(step.body, style = MaterialTheme.typography.bodyLarge) }
                item { Text("Bu anlatım gerçek bir işlem gerçekleştirmez.", style = MaterialTheme.typography.bodySmall) }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.pusula_app_icon), "Pusula uygulama simgesi", Modifier.size(48.dp).testTag("pusula.brand"))
                        Text("İhtiyacınız olan konuyu seçin", style = MaterialTheme.typography.titleMedium)
                    }
                }
                item { Text("Rehber bilgilendirme içindir. Kayıt oluşturmaz, silmez, ödeme veya mesaj göndermez.", style = MaterialTheme.typography.bodySmall) }
                if (catalog == null) {
                    item {
                        if (failed) {
                            Text("Rehber yüklenemedi")
                            TextButton(onClick = { attempt++ }) { Text("Tekrar Dene") }
                        } else CircularProgressIndicator()
                    }
                } else {
                    item { Text("$completed / ${visible.size} konu tamamlandı", Modifier.testTag("guide.progress")) }
                    item {
                        OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Özellik veya konu ara") },
                            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("guide.search"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
                    }
                    fun open(t: GuideTopic) { keyboard?.hide(); index = progress.read(scope, role, t).start(t); selected = t.id }
                    if (query.isBlank() && category == null) visible.firstOrNull { it.id.startsWith("quick-start-") }?.let { quick ->
                        item { Button(onClick = { open(quick) }, modifier = Modifier.fillMaxWidth().testTag("guide.quick-start")) { Text("Hızlı Başlangıç") } }
                    }
                    item {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(category == null, onClick = { category = null }, label = { Text("Tümü") }, modifier = Modifier.testTag("guide.category.all"))
                            catalog!!.categories.filter { c -> visible.any { it.category == c.id } }.forEach { c ->
                                FilterChip(category == c.id, onClick = { category = c.id }, label = { Text(c.title) }, modifier = Modifier.testTag("guide.category.${c.id}"))
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(unfinished, { unfinished = it }, Modifier.testTag("guide.unfinished"))
                            Text("Yalnızca tamamlanmayanlar")
                        }
                    }
                    val results = visible.filter { (category == null || it.category == category) && it.matches(query) && (!unfinished || !progress.read(scope, role, it).complete(it)) }
                    if (results.isEmpty()) item { Text("Konu bulunamadı") }
                    items(results, key = { it.id }) { t ->
                        OutlinedCard(onClick = { open(t) }, modifier = Modifier.fillMaxWidth().testTag("guide.topic.${t.id}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium)
                                Text(t.summary)
                                Text("${t.steps.size} adım" + if (progress.read(scope, role, t).complete(t)) " • Tamamlandı" else if (t.isNew) " • Yeni" else "", style = MaterialTheme.typography.bodySmall)
                                if (t.requiredFeature != null && features[t.requiredFeature] != true) Text("Paket / yetkiye bağlı", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
