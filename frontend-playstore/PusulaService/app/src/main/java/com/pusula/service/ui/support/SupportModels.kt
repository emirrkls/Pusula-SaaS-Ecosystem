package com.pusula.service.ui.support

data class SupportContact(val enabled:Boolean=false,val email:String="pusulaiklimlendirme.didim@gmail.com",val website:String="https://www.pusulaiklimlendirme.com/destek")
data class SupportTicket(val id:Long,val number:String,val subject:String,val category:String,val status:String,val unread:Boolean,val updatedAt:String)
data class SupportAttachment(val id:Long,val fileName:String,val mediaType:String,val size:Int)
data class SupportMessage(val id:Long,val body:String,val staff:Boolean,val createdAt:String,val attachments:List<SupportAttachment>)
data class SupportDetail(val ticket:SupportTicket,val messages:List<SupportMessage>,val nextBefore:Long?)
data class SupportPage(val items:List<SupportTicket>,val page:Int,val hasNext:Boolean,val unreadCount:Long)
data class SupportUpload(val mediaType:String,val dataBase64:String)
data class SupportDraft(val clientKey:String=java.util.UUID.randomUUID().toString(),val subject:String="",val category:String="QUESTION",val body:String="",val platform:String="ANDROID",val appVersion:String=com.pusula.service.BuildConfig.VERSION_NAME,val attachments:List<SupportUpload> = emptyList(),val ticketId:Long?=null,val frozen:Boolean=false) {
    val hasContent:Boolean get()=frozen||subject.isNotEmpty()||body.isNotEmpty()||attachments.isNotEmpty()
}
fun supportLabel(value:String):String=mapOf("NEW" to "Yeni","IN_PROGRESS" to "İnceleniyor","WAITING_USER" to "Yanıtınız bekleniyor","RESOLVED" to "Çözüldü","EVALUATING" to "Değerlendiriliyor","PLANNED" to "Planlandı","QUESTION" to "Soru","BUG" to "Hata","SUGGESTION" to "Öneri")[value]?:value
