package com.pusula.desktop.support;
import java.util.*;
public final class SupportModels {
    private SupportModels(){}
    public record Contact(boolean enabled,String email,String website){}
    public record Ticket(Long id,String number,String subject,String category,String status,String priority,boolean unread,String updatedAt){}
    public record Attachment(Long id,String fileName,String mediaType,int size){}
    public record Message(Long id,String body,boolean staff,String createdAt,List<Attachment> attachments){}
    public record Detail(Ticket ticket,List<Message> messages,Long nextBefore){}
    public record Page(List<Ticket> items,int page,boolean hasNext,long unreadCount){}
    public record Upload(String mediaType,String dataBase64){}
    public static class Draft {
        public String clientKey=UUID.randomUUID().toString(),subject="",category="QUESTION",body="",platform="DESKTOP",appVersion=com.pusula.desktop.util.AppVersion.get();
        public List<Upload> attachments=new ArrayList<>(); public Long ticketId; public boolean frozen;
        public boolean hasContent(){return frozen||!subject.isEmpty()||!body.isEmpty()||!attachments.isEmpty();}
        public Map<String,Object> payload(){var map=new LinkedHashMap<String,Object>();map.put("clientKey",clientKey);map.put("body",body);map.put("attachments",List.copyOf(attachments));if(ticketId==null){map.put("subject",subject);map.put("category",category);map.put("platform",platform);map.put("appVersion",appVersion);}else map.put("internalNote",false);return map;}
    }
    public static String label(String value){return switch(value){case "NEW"->"Yeni";case "IN_PROGRESS"->"İnceleniyor";case "WAITING_USER"->"Yanıtınız bekleniyor";case "RESOLVED"->"Çözüldü";case "EVALUATING"->"Değerlendiriliyor";case "PLANNED"->"Planlandı";case "QUESTION"->"Soru";case "BUG"->"Hata";case "SUGGESTION"->"Öneri";default->value;};}
}
