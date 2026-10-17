package com.pusula.desktop.support;

import com.google.gson.Gson;
import com.sun.jna.platform.win32.Crypt32Util;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Current Windows desktop target: user-scoped DPAPI, never plaintext credentials or draft content. */
public class SupportDraftStore {
    private final Path folder;
    private final Gson gson=new Gson();
    public SupportDraftStore(){this(Path.of(System.getProperty("user.home"),".pusula","support-drafts"));}
    public SupportDraftStore(Path folder){this.folder=folder.toAbsolutePath().normalize();}
    public static String accountKey(Long company,String username){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((company+":"+username).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private Path file(String key){if(!key.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Geçersiz taslak anahtarı.");return folder.resolve(key+".bin");}
    public synchronized SupportModels.Draft load(String key)throws IOException {
        var file=file(key);if(!Files.exists(file))return new SupportModels.Draft();if(Files.size(file)>10_000_000)throw new IOException("Taslak çok büyük.");
        try{return gson.fromJson(new String(Crypt32Util.cryptUnprotectData(Files.readAllBytes(file)),StandardCharsets.UTF_8),SupportModels.Draft.class);}catch(RuntimeException e){throw new IOException("Taslak bu Windows hesabında açılamadı.");}
    }
    public synchronized void save(String key,SupportModels.Draft draft)throws IOException {
        if(!com.sun.jna.Platform.isWindows())throw new IOException("Korumalı taslak kaydı bu sürümde Windows gerektirir.");
        var data=gson.toJson(draft).getBytes(StandardCharsets.UTF_8);if(data.length>9_000_000)throw new IOException("Taslak çok büyük.");
        Files.createDirectories(folder);Path target=file(key),temp=Files.createTempFile(folder,"draft-",".tmp");
        try{Files.write(temp,Crypt32Util.cryptProtectData(data));try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    public synchronized void clear(String key)throws IOException{Files.deleteIfExists(file(key));}
}
