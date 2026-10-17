package com.pusula.backend.support;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/** No arbitrary files, client filenames, EXIF, raw headers or unfiltered log text are retained. */
@Component
public class SupportFiles {
    public static final int MAX_BYTES=2*1024*1024;
    private final ObjectMapper mapper;
    public SupportFiles(ObjectMapper mapper) { this.mapper=mapper; }
    record SafeFile(String name,String type,byte[] bytes) {}
    SafeFile validate(SupportDtos.Upload file) {
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(file.dataBase64()); }
        catch (Exception e) { throw new IllegalArgumentException("Ek dosyası okunamadı."); }
        if(bytes.length==0 || bytes.length>MAX_BYTES) throw new IllegalArgumentException("Her ek en fazla 2 MB olabilir.");
        if("application/json".equals(file.mediaType())) return diagnostic(bytes);
        if(!Set.of("image/jpeg","image/png").contains(file.mediaType())) throw new IllegalArgumentException("Yalnızca JPEG, PNG ve güvenli bağlantı raporu eklenebilir.");
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IOException();
            var reader=readers.next();
            try {
                reader.setInput(input);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","png").contains(format)) throw new IOException();
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1 || h<1 || (long)w*h>16_000_000) throw new IOException();
                var image=reader.read(0);
                double scale=Math.min(1,1600.0/Math.max(w,h));
                var normalized=new BufferedImage(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),BufferedImage.TYPE_INT_RGB);
                var graphics=normalized.createGraphics();
                try { graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,normalized.getWidth(),normalized.getHeight()); graphics.drawImage(image,0,0,normalized.getWidth(),normalized.getHeight(),null); }
                finally { graphics.dispose(); }
                var output=new ByteArrayOutputStream(); ImageIO.write(normalized,"jpeg",output);
                return new SafeFile("ekran-goruntusu.jpg","image/jpeg",output.toByteArray());
            } finally { reader.dispose(); }
        } catch(IOException e) { throw new IllegalArgumentException("Görsel geçersiz veya boyutları çok büyük."); }
    }
    private SafeFile diagnostic(byte[] bytes) {
        if(bytes.length>256*1024) throw new IllegalArgumentException("Teknik rapor en fazla 256 KB olabilir.");
        try {
            JsonNode input=mapper.readTree(bytes);
            if(input.path("schemaVersion").asInt()!=1 || !input.path("records").isArray()) throw new IOException();
            ObjectNode output=mapper.createObjectNode(); output.put("schemaVersion",1);
            // Rebuild from a minimal allowlist; never persist arbitrary JSON or supplied route strings.
            ArrayNode records=output.putArray("records");
            for(JsonNode record:input.path("records")) {
                if(records.size()>=100) break;
                ObjectNode clean=records.addObject();
                for(String key:List.of("elapsedMilliseconds","status","streamErrorDomain","streamErrorCode"))
                    if(record.path(key).isIntegralNumber()) clean.put(key,record.path(key).asLong());
                for(String key:List.of("sourceAppVersion","sourceBuild","sourceOSVersion")) {
                    String value=record.path(key).asText("");
                    if(value.matches("[0-9.]{1,32}")) clean.put(key,value);
                }
                for(String key:List.of("method","outcome","interface","operation","appState")) {
                    String value=record.path(key).asText("");
                    if(Set.of("GET","POST","PUT","PATCH","DELETE","LOCAL","success","httpError","transportError","localError","cancelled","wifi","cellular","wired","other","unavailable","unknown","api","upload","download","photoLibraryRead","photoPreparation","active","inactive","background").contains(value)) clean.put(key,value);
                }
                if(record.path("startedAt").isNumber() && Double.isFinite(record.path("startedAt").asDouble())) clean.put("startedAt",record.path("startedAt").asDouble());
                String rawRoute=record.path("route").asText("");
                if(rawRoute.startsWith("/") && rawRoute.length()<=300) {
                    var safeSegments=Set.of("api","support","tickets","messages","customers","inventory","expenses","finance","technician-notes","used-parts","photos","my-assigned","all","dashboard","summary","current-accounts","payments","reports","auth","profile","feature-context","complete","reliable-mutations",":id",":redacted");
                    clean.put("route",Arrays.stream(rawRoute.split("/",-1)).limit(12).map(s->s.isEmpty()?"":safeSegments.contains(s)?s:":redacted").collect(java.util.stream.Collectors.joining("/")));
                }
                ArrayNode errors=clean.putArray("errors");
                if(record.path("errors").isArray()) for(JsonNode error:record.path("errors")) {
                    if(errors.size()>=5) break;
                    if(error.path("code").isIntegralNumber()) {
                        ObjectNode item=errors.addObject(); item.put("code",error.path("code").asInt());
                        String domain=error.path("domain").asText("");
                        if(Set.of("NSURLErrorDomain","kCFErrorDomainCFNetwork","NSPOSIXErrorDomain","NSOSStatusErrorDomain").contains(domain)) item.put("domain",domain);
                    }
                }
            }
            return new SafeFile("baglanti-raporu.json","application/json",mapper.writeValueAsBytes(output));
        } catch(IOException e) { throw new IllegalArgumentException("Geçerli bir Pusula bağlantı raporu seçin."); }
    }
}
