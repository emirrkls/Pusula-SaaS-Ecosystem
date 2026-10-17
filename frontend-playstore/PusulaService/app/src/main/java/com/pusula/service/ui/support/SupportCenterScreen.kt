package com.pusula.service.ui.support

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.pusula.service.ui.guide.guideTourTarget
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SupportCenterScreen(onClose:()->Unit,viewModel:SupportViewModel=hiltViewModel(),tourComposer:Boolean=false) {
    val state by viewModel.state.collectAsState();val context=LocalContext.current;val scope=rememberCoroutineScope();val keyboard=LocalSoftwareKeyboardController.current
    var preview by remember(state.accountPartition){mutableStateOf<SupportUpload?>(null)}
    var pickerPartition by remember{mutableStateOf("")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->scope.launch {
        val expectedPartition=pickerPartition
        for(uri in uris.take(3-state.draft.attachments.size)) try {
            val upload=withContext(Dispatchers.IO) {
                val bytes=context.contentResolver.openInputStream(uri)!!.use{input->val out=ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=2*1024*1024);out.write(buffer,0,n)};out.toByteArray()}
                if(context.contentResolver.getType(uri)=="application/json"){require(bytes.size<=256*1024);SupportUpload("application/json",Base64.encodeToString(bytes,Base64.NO_WRAP))}
                else {
                    val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,options);require(options.outWidth>0&&options.outHeight>0&&options.outWidth.toLong()*options.outHeight<=16_000_000)
                    val original=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Görsel okunamadı");val scale=minOf(1.0,1600.0/maxOf(original.width,original.height));val resized=Bitmap.createScaledBitmap(original,maxOf(1,(original.width*scale).toInt()),maxOf(1,(original.height*scale).toInt()),true)
                    val out=ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,80,out);if(resized!==original)resized.recycle();original.recycle();SupportUpload("image/jpeg",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP))
                }
            }
            viewModel.attach(upload,expectedPartition)
            if(upload.mediaType=="application/json"&&expectedPartition==viewModel.state.value.accountPartition)preview=upload
        }catch(e:Exception){if(e !is CancellationException)viewModel.attachmentFailure()}
    }}
    val composing=state.composer||tourComposer
    androidx.activity.compose.BackHandler { if(state.composer)viewModel.closeComposer() else if(state.detail!=null)viewModel.back() else onClose() }
    Scaffold(modifier=Modifier.fillMaxSize().safeDrawingPadding().testTag("support.screen"),containerColor=MaterialTheme.colorScheme.surfaceContainerLow,
        topBar={TopAppBar(title={Text(if(composing)"Yeni mesaj" else "Yardım ve Destek")},actions={TextButton(onClick={keyboard?.hide();onClose()}){Text("Kapat")}})},
        bottomBar={if(composing)Surface(tonalElevation=3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={keyboard?.hide();viewModel.send()},enabled=!state.busy&&state.draft.body.isNotBlank()&&(state.draft.ticketId!=null||state.draft.subject.isNotBlank()),modifier=Modifier.fillMaxWidth().testTag("support.send").guideTourTarget("support-submit")){Text(if(state.busy)"Gönderiliyor…" else if(state.draft.frozen)"Aynı gönderimi tekrar dene" else "Gönder")}
                TextButton(onClick={keyboard?.hide();viewModel.closeComposer()},modifier=Modifier.fillMaxWidth()){Text("Taslağı saklayıp kapat")}
            }
        }}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Column(Modifier.widthIn(max=760.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            if(!composing&&state.detail==null) SupportHero()
            if(composing)Text("Konuyu ve neye ihtiyaç duyduğunuzu anlatın. Yanıtları bu merkezden takip edebilirsiniz.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let{SupportNotice(it,true)};state.success?.let{SupportNotice(it,false)}
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!state.contact.enabled||!state.authenticated)Text("Talep merkezine erişemiyorsanız e-posta ile ulaşabilirsiniz.")
            else {
                if(!composing&&state.detail==null){Button(onClick={viewModel.compose()},modifier=Modifier.fillMaxWidth().testTag("support.new"),contentPadding=PaddingValues(18.dp)){Text("Yeni talep")};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Taleplerim",style=MaterialTheme.typography.titleLarge);TextButton(onClick={viewModel.load()},enabled=!state.busy){Text("Yenile")}}}
                state.detail?.takeIf{!composing}?.let{detail->
                    SupportPanel {Text(detail.ticket.number,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(detail.ticket.subject,style=MaterialTheme.typography.headlineSmall);SupportStatus(detail.ticket.status)}
                    detail.nextBefore?.let{before->TextButton(onClick={viewModel.open(detail.ticket.id,before)}){Text("Önceki mesajlar")}}
                    detail.messages.forEach{message->SupportMessageBubble(message){file->scope.launch{
                        try{val bytes=viewModel.attachment(file);val target=withContext(Dispatchers.IO){val folder=File(context.cacheDir,"shared").apply{mkdirs()};File.createTempFile("support-",if(file.mediaType=="application/json")".json" else ".jpg",folder).apply{writeBytes(bytes)}};val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",target);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(file.mediaType).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Eki paylaş"))}
                        catch(e:Exception){if(e is CancellationException)throw e;viewModel.attachmentFailure()}
                    }}}
                    Row{TextButton(onClick={viewModel.compose(true)}){Text("Yanıtla / yeniden aç")};TextButton(onClick={viewModel.back()}){Text("Taleplere dön")}}
                }
                if(state.detail==null&&!composing)Column(Modifier.guideTourTarget("support-requests"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    state.tickets.forEach{ticket->OutlinedCard(onClick={viewModel.open(ticket.id)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Text(ticket.number,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ticket.subject,style=MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){SupportStatus(ticket.status);Text(supportDisplayDate(ticket.updatedAt),style=MaterialTheme.typography.labelSmall)}
                        if(ticket.unread)Text("Yeni yanıt var",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                    }}}
                    if(state.tickets.isEmpty()&&!state.busy)SupportPanel {Text("Henüz destek talebiniz yok.",style=MaterialTheme.typography.titleMedium);Text("Sorularınız, hata bildirimleriniz ve önerileriniz burada takip edilir.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Row{if(state.page>0)TextButton(onClick={viewModel.load(state.page-1)}){Text("Önceki")};if(state.hasNext)TextButton(onClick={viewModel.load(state.page+1)}){Text("Sonraki")}}
                }
                if(composing) {
                    val editable=!state.busy&&!state.draft.frozen
                    SupportPanel(Modifier.guideTourTarget("support-form")) {
                    if(state.draft.ticketId==null){OutlinedTextField(state.draft.subject,{v->viewModel.edit{it.copy(subject=v.take(120))}},label={Text("Konu")},enabled=editable,modifier=Modifier.fillMaxWidth());Column{listOf("QUESTION","BUG","SUGGESTION").forEach{type->FilterChip(state.draft.category==type,{viewModel.edit{it.copy(category=type)}},label={Text(supportLabel(type))},enabled=editable)}}}
                    OutlinedTextField(state.draft.body,{v->viewModel.edit{it.copy(body=v.take(4000))}},label={Text("Açıklama")},supportingText={Text("${state.draft.body.length} / 4000")},enabled=editable,minLines=5,modifier=Modifier.fillMaxWidth())
                    }
                    SupportPanel(Modifier.guideTourTarget("support-attachments")) {
                    Text("Ekler · isteğe bağlı",style=MaterialTheme.typography.titleMedium)
                    Text("Ekler isteğe bağlıdır. En fazla 3 ek; görsel 2 MB, rapor 256 KB. Ekler 30 gün, çözülen talepler 180 gün saklanır.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={pickerPartition=state.accountPartition;picker.launch(arrayOf("image/jpeg","image/png","application/json"))},enabled=editable&&state.draft.attachments.size<3){Text("Görsel / Pusula raporu ekle")}
                    state.draft.attachments.forEachIndexed{i,file->Row{Text(if(file.mediaType=="application/json")"Teknik rapor" else "Ekran görüntüsü");TextButton(onClick={preview=file}){Text("Önizle")};TextButton(onClick={viewModel.edit{it.copy(attachments=it.attachments.filterIndexed{index,_->index!=i})}},enabled=editable){Text("Kaldır")}}}
                    Text("Parola veya müşteri/finans kaydı paylaşmayın. Görseldeki hassas alanları önce kapatın.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(state.draft.frozen)Text("Sonuç henüz doğrulanmadı. Aynı içerik ve anahtarla tekrar deneyin.")
                    TextButton(onClick={keyboard?.hide()},modifier=Modifier.fillMaxWidth()){Text("Klavyeyi kapat")}
                    TextButton(onClick={runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,state.draft.body),"Taslağı paylaş"))}}){Text("Taslağı paylaş")}
                }
            }
            if(!composing)SupportPanel {Text("Diğer iletişim kanalları",style=MaterialTheme.typography.titleMedium);Text("Talep merkezine erişemediğinizde e-posta ile ulaşabilirsiniz.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${state.contact.email}")).putExtra(Intent.EXTRA_SUBJECT,"Pusula destek").putExtra(Intent.EXTRA_TEXT,state.draft.body))}}){Text(state.contact.email)}}
        }
        }
    }
    preview?.let{file->
        val bytes=remember(file){Base64.decode(file.dataBase64,Base64.NO_WRAP)}
        AlertDialog(onDismissRequest={preview=null},title={Text("Ek önizlemesi")},text={
            if(file.mediaType=="application/json")Text(String(bytes).take(12000),modifier=Modifier.verticalScroll(rememberScrollState()))
            else {val image=remember(file){BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()};if(image!=null)Image(image,contentDescription="Gönderilecek ekran görüntüsü",modifier=Modifier.fillMaxWidth())}
        },confirmButton={TextButton(onClick={preview=null}){Text("Tamam")}})
    }
}

@Composable private fun SupportHero() {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xff1c3461),Color(0xff14496b))),RoundedCornerShape(24.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("PUSULA DESTEK",style=MaterialTheme.typography.labelLarge,color=Color(0xff48d5ff))
        Text("Size nasıl yardımcı olabiliriz?",style=MaterialTheme.typography.headlineMedium,color=Color.White)
        Text("Sorunları birlikte çözelim, önerilerinizle Pusula’yı geliştirelim.",color=Color.White.copy(alpha=0.85f))
    }
}
@Composable private fun SupportPanel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)}
}
@Composable private fun SupportStatus(value:String) {
    val color=if(value=="RESOLVED")MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Text(supportLabel(value),Modifier.background(color.copy(alpha=0.12f),RoundedCornerShape(50)).padding(horizontal=10.dp,vertical=6.dp),color=color,style=MaterialTheme.typography.labelMedium)
}
@Composable private fun SupportNotice(value:String,error:Boolean) {
    Surface(shape=RoundedCornerShape(16.dp),color=if(error)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer){Text(value,Modifier.fillMaxWidth().padding(16.dp))}
}
@Composable private fun SupportMessageBubble(message:SupportMessage,onAttachment:(SupportAttachment)->Unit) {
    Row(Modifier.fillMaxWidth()) {
        if(!message.staff)Spacer(Modifier.width(24.dp))
        Surface(Modifier.weight(1f),shape=RoundedCornerShape(20.dp),color=if(message.staff)MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.5f) else MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if(message.staff)"Destek Ekibi" else "Siz",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                Text(message.body)
                message.attachments.forEach{file->TextButton(onClick={onAttachment(file)}){Text(file.fileName)}}
                Text(supportDisplayDate(message.createdAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(message.staff)Spacer(Modifier.width(24.dp))
    }
}
