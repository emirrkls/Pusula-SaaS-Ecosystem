package com.pusula.service.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pusula.service.core.SessionManager
import com.pusula.service.core.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SupportState(val contact:SupportContact=SupportContact(),val authenticated:Boolean=false,val accountPartition:String="",val tickets:List<SupportTicket> = emptyList(),val detail:SupportDetail?=null,val draft:SupportDraft=SupportDraft(),val composer:Boolean=false,val busy:Boolean=false,val error:String?=null,val success:String?=null,val page:Int=0,val hasNext:Boolean=false)
@HiltViewModel class SupportViewModel @Inject constructor(private val repository:SupportRepository,private val session:SessionManager):ViewModel() {
    private val mutable=MutableStateFlow(SupportState());val state=mutable.asStateFlow()
    private var actor:SessionState?=null;private var key:String?=null;private var autosave:Job?=null
    init {viewModelScope.launch {session.state.map{Triple(it.token,it.companyId,it.supportRevision)}.distinctUntilChanged().collectLatest { bind() }}}
    private fun current(s:SessionState)=session.state.value.token==s.token && session.state.value.companyId==s.companyId && session.state.value.supportRevision==s.supportRevision
    private suspend fun bind() {
        autosave?.cancel();actor=session.state.value;mutable.value=SupportState();val captured=actor!!
        key=captured.token?.let{repository.accountKey(it,captured.companyId)}
        mutable.update{it.copy(authenticated=captured.isAuthenticated,accountPartition=key.orEmpty())}
        val saved=if(com.pusula.service.core.GuideReadOnlyGate.active.value) null else key?.let{repository.load(it)}
        if(!current(captured))return
        if(saved!=null) mutable.update{it.copy(draft=saved,composer=saved.hasContent)}
        runCatching{repository.api.contact()}.onSuccess{if(current(captured)) mutable.update {s->s.copy(contact=it)}}.onFailure{if(it !is CancellationException) mutable.update{s->s.copy(error="Bağlantı kurulamadı. Taslak korunuyor; e-posta ile de ulaşabilirsiniz.")}}
        if(current(captured)&&captured.isAuthenticated&&state.value.contact.enabled) load()
    }
    fun edit(update:(SupportDraft)->SupportDraft) {if(state.value.busy||state.value.draft.frozen)return;mutable.update{it.copy(draft=update(it.draft),success=null)};persist()}
    private fun persist(){val captured=actor?:return;val account=key?:return;val draft=state.value.draft;autosave?.cancel();autosave=viewModelScope.launch{delay(250);try{if(current(captured))repository.save(account,draft)}catch(e:Exception){if(e !is CancellationException&&current(captured))mutable.update{it.copy(error="Taslak cihazda saklanamadı. Paylaşarak yedekleyebilirsiniz.")}}}}
    fun compose(reply:Boolean=false) {
        if(state.value.busy)return
        val existing=state.value.draft;val id=if(reply)state.value.detail?.ticket?.id else null
        if(existing.hasContent && existing.ticketId!=id){mutable.update{it.copy(error="Önce mevcut taslağı tamamlayın.")};return}
        mutable.update{it.copy(composer=true,draft=if(!existing.hasContent)SupportDraft(ticketId=id) else existing)}
    }
    fun closeComposer(){mutable.update{it.copy(composer=false)}}
    fun back(){mutable.update{it.copy(detail=null,composer=false)}}
    fun load(page:Int=state.value.page){viewModelScope.launch{val captured=actor?:return@launch;val token=captured.token?:return@launch;if(state.value.busy)return@launch;mutable.update{it.copy(busy=true)};try{val result=repository.api.list("Bearer $token",page);if(current(captured))mutable.update{it.copy(tickets=result.items,page=page,hasNext=result.hasNext,error=null)}}catch(e:Exception){failure(e,captured)}finally{if(current(captured))mutable.update{it.copy(busy=false)}}}}
    fun open(id:Long,before:Long?=null){viewModelScope.launch{val captured=actor?:return@launch;val token=captured.token?:return@launch;if(state.value.busy)return@launch;mutable.update{it.copy(busy=true)};try{val result=repository.api.detail("Bearer $token",id,before);if(current(captured)){mutable.update{it.copy(detail=if(before!=null)result.copy(messages=result.messages+(it.detail?.messages?:emptyList())) else result,composer=false,error=null)};runCatching{repository.api.read("Bearer $token",id)}}}catch(e:Exception){failure(e,captured)}finally{if(current(captured))mutable.update{it.copy(busy=false)}}}}
    fun send(){viewModelScope.launch{val captured=actor?:return@launch;val token=captured.token?:return@launch;val account=key?:return@launch;if(state.value.busy)return@launch;autosave?.cancelAndJoin();val draft=state.value.draft.copy(frozen=true);mutable.update{it.copy(draft=draft,busy=true,error=null)};try{repository.save(account,draft);val detail=repository.send(token,draft);if(current(captured)){repository.clear(account);mutable.update{it.copy(draft=SupportDraft(),detail=detail,composer=false,success="Mesajınız kaydedildi.")}}}catch(e:Exception){if(e is retrofit2.HttpException&&e.code()==400&&current(captured))mutable.update{it.copy(draft=draft.copy(frozen=false,clientKey=java.util.UUID.randomUUID().toString()))};failure(e,captured)}finally{if(current(captured))mutable.update{it.copy(busy=false)}}}}
    private fun failure(e:Exception,captured:SessionState){if(e !is CancellationException&&current(captured))mutable.update{it.copy(error="İşlem tamamlanamadı. Taslak ve mevcut bilgiler korunuyor. ${if(it.draft.frozen)"Sonuç belirsiz; aynı gönderimi tekrar deneyin." else "Tekrar deneyin veya e-posta ile ulaşın."}")}}
    fun attachmentFailure(){mutable.update{it.copy(error="Ek okunamadı. En fazla 3 JPEG/PNG (2 MB) veya Pusula JSON raporu (256 KB) seçin.")}}
    fun attach(upload:SupportUpload,partition:String){if(partition.isNotEmpty()&&partition==key&&state.value.draft.attachments.size<3)edit{it.copy(attachments=it.attachments+upload)}}
    suspend fun attachment(file:SupportAttachment):ByteArray {val captured=actor?:error("Oturum gerekli");val data=repository.api.attachment("Bearer ${captured.token}",file.id).use{it.bytes()};check(current(captured));return data}
}
