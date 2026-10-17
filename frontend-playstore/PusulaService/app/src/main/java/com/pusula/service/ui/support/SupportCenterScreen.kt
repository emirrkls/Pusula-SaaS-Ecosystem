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

@Composable fun SupportCenterScreen(onClose:()->Unit,viewModel:SupportViewModel=hiltViewModel()) {
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
    Surface(modifier=Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Yardım ve Destek",style=MaterialTheme.typography.titleLarge);TextButton(onClick={keyboard?.hide();onClose()}){Text("Kapat")}}
            Text("Soru, hata bildirimi veya geliştirme önerinizi iletin. Parola ya da müşteri/finans kaydı paylaşmayın. Görseldeki hassas alanları göndermeden önce kendiniz kapatın.")
            TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:${state.contact.email}")).putExtra(Intent.EXTRA_SUBJECT,"Pusula destek").putExtra(Intent.EXTRA_TEXT,state.draft.body))}}){Text("E-posta ile ulaş: ${state.contact.email}")}
            TextButton(onClick={runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,state.draft.body),"Taslağı paylaş"))}}){Text("Taslağı paylaş")}
            state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};state.success?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(!state.contact.enabled||!state.authenticated)Text("Talep merkezine erişemiyorsanız e-posta ile ulaşabilirsiniz.")
            else {
                Row{TextButton(onClick={viewModel.compose()}){Text("Yeni talep")};TextButton(onClick={viewModel.load()}){Text("Yenile")}}
                state.detail?.let{detail->
                    Text("${detail.ticket.number} · ${detail.ticket.subject}",style=MaterialTheme.typography.titleMedium);Text(supportLabel(detail.ticket.status))
                    detail.nextBefore?.let{before->TextButton(onClick={viewModel.open(detail.ticket.id,before)}){Text("Önceki mesajlar")}}
                    detail.messages.forEach{message->OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(if(message.staff)"Destek Ekibi" else "Siz",style=MaterialTheme.typography.labelLarge);Text(message.body);Text(message.createdAt,style=MaterialTheme.typography.labelSmall);message.attachments.forEach{file->TextButton(onClick={scope.launch{try{val bytes=viewModel.attachment(file);val target=withContext(Dispatchers.IO){val folder=File(context.cacheDir,"shared").apply{mkdirs()};File.createTempFile("support-",if(file.mediaType=="application/json")".json" else ".jpg",folder).apply{writeBytes(bytes)}};val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",target);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(file.mediaType).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Eki paylaş"))}catch(e:Exception){viewModel.attachmentFailure()}}}){Text(file.fileName)}}}}}
                    Row{TextButton(onClick={viewModel.compose(true)}){Text("Yanıtla / yeniden aç")};TextButton(onClick={viewModel.back()}){Text("Taleplere dön")}}
                }
                if(state.detail==null&&!state.composer){state.tickets.forEach{ticket->OutlinedCard(onClick={viewModel.open(ticket.id)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("${if(ticket.unread)"● " else ""}${ticket.number} · ${ticket.subject}");Text(supportLabel(ticket.status))}}};if(state.tickets.isEmpty()&&!state.busy)Text("Henüz destek talebiniz yok.");Row{if(state.page>0)TextButton(onClick={viewModel.load(state.page-1)}){Text("Önceki")};if(state.hasNext)TextButton(onClick={viewModel.load(state.page+1)}){Text("Sonraki")}}}
                if(state.composer) {
                    val editable=!state.busy&&!state.draft.frozen
                    if(state.draft.ticketId==null){OutlinedTextField(state.draft.subject,{v->viewModel.edit{it.copy(subject=v.take(120))}},label={Text("Konu")},enabled=editable,modifier=Modifier.fillMaxWidth());Row{listOf("QUESTION","BUG","SUGGESTION").forEach{type->FilterChip(state.draft.category==type,{viewModel.edit{it.copy(category=type)}},label={Text(supportLabel(type))},enabled=editable)}}}
                    OutlinedTextField(state.draft.body,{v->viewModel.edit{it.copy(body=v.take(4000))}},label={Text("Açıklama")},enabled=editable,minLines=5,modifier=Modifier.fillMaxWidth())
                    Text("Ekler isteğe bağlıdır. En fazla 3 ek; görsel 2 MB, rapor 256 KB. Ekler 30 gün, çözülen talepler 180 gün saklanır.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={pickerPartition=state.accountPartition;picker.launch(arrayOf("image/jpeg","image/png","application/json"))},enabled=editable&&state.draft.attachments.size<3){Text("Görsel / Pusula raporu ekle")}
                    state.draft.attachments.forEachIndexed{i,file->Row{Text(if(file.mediaType=="application/json")"Teknik rapor" else "Ekran görüntüsü");TextButton(onClick={preview=file}){Text("Önizle")};TextButton(onClick={viewModel.edit{it.copy(attachments=it.attachments.filterIndexed{index,_->index!=i})}},enabled=editable){Text("Kaldır")}}}
                    if(state.draft.frozen)Text("Sonuç henüz doğrulanmadı. Aynı içerik ve anahtarla tekrar deneyin.")
                    Button(onClick={keyboard?.hide();viewModel.send()},enabled=!state.busy&&state.draft.body.isNotBlank()&&(state.draft.ticketId!=null||state.draft.subject.isNotBlank()),modifier=Modifier.fillMaxWidth()){Text(if(state.draft.frozen)"Aynı gönderimi tekrar dene" else "Gönder")}
                    TextButton(onClick={keyboard?.hide()},modifier=Modifier.fillMaxWidth()){Text("Klavyeyi kapat")}
                    TextButton(onClick={viewModel.closeComposer()}){Text("Taslağı saklayıp kapat")}
                }
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
