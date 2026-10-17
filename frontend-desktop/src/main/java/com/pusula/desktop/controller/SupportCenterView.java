package com.pusula.desktop.controller;

import com.pusula.desktop.support.*;
import com.pusula.desktop.support.SupportModels.*;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.SessionManager;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import retrofit2.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.nio.file.Files;

public class SupportCenterView extends BorderPane {
    private static final ScheduledExecutorService IO=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"support-draft-io");t.setDaemon(true);return t;});
    private final SupportApi api;
    private final String token=SessionManager.getToken();private final long revision=SessionManager.getSupportRevision();
    private final String account=SupportDraftStore.accountKey(SessionManager.getCompanyId(),SessionManager.getUsername());
    private final SupportDraftStore store;private final Gson gson=new Gson();
    private final Label feedback=new Label();private final VBox content=new VBox(14);
    private Contact contact=new Contact(false,"pusulaiklimlendirme.didim@gmail.com","https://www.pusulaiklimlendirme.com/destek");
    private Draft draft=new Draft();private Detail detail;private Page list;private int page;private boolean composer,busy;private boolean draftReady=token==null;private volatile long version;private ScheduledFuture<?> autosave;
    public SupportCenterView(){
        this(RetrofitClient.getClient().newBuilder().client(new okhttp3.OkHttpClient.Builder().readTimeout(30,TimeUnit.SECONDS).connectTimeout(10,TimeUnit.SECONDS).build()).build().create(SupportApi.class),new SupportDraftStore());
    }
    /** Package-local injection keeps UI regression tests on a synthetic loopback server. */
    SupportCenterView(SupportApi api,SupportDraftStore store){
        this.api=Objects.requireNonNull(api);this.store=Objects.requireNonNull(store);
        setPadding(new Insets(18));feedback.setWrapText(true);setTop(new VBox(10,new Label("Yardım ve Destek"),feedback));
        var scroll=new ScrollPane(content);scroll.setFitToWidth(true);setCenter(scroll);render();
        if(token!=null)IO.execute(()->{try{var saved=store.load(account);Platform.runLater(()->{if(current()){draft=saved;composer=saved.hasContent();draftReady=true;render();}});}catch(Exception e){Platform.runLater(()->{if(current()){feedback.setText("Korumalı taslak açılamadı. Üzerine yazmamak için e-posta ile ulaşın.");render();}});}});
        call(api.contact(),value->{contact=value;render();if(token!=null&&contact.enabled())load();});
    }
    private boolean current(){return Objects.equals(token,SessionManager.getToken())&&revision==SessionManager.getSupportRevision();}
    private String bearer(){return "Bearer "+token;}
    private <T> void call(Call<T> call,Consumer<T> success){
        call.enqueue(new Callback<>(){
            public void onResponse(Call<T> c,Response<T> r){Platform.runLater(()->{
                if(!current())return;busy=false;
                if(r.isSuccessful()&&r.body()!=null)success.accept(r.body());
                else{if(r.code()==400){draft.frozen=false;draft.clientKey=UUID.randomUUID().toString();saveLater();}feedback.setText("İşlem tamamlanamadı ("+r.code()+"). Taslak korunuyor.");render();}
            });}
            public void onFailure(Call<T> c,Throwable e){Platform.runLater(()->{if(current()){busy=false;feedback.setText("Bağlantı kurulamadı. Taslak korunuyor; aynı gönderimi tekrar deneyin veya e-posta ile ulaşın.");render();}});}
        });
    }
    private Button button(String text,Runnable action){var b=new Button(text);b.setOnAction(e->action.run());b.setMaxWidth(Double.MAX_VALUE);return b;}
    private Label text(String text){var label=new Label(text);label.setWrapText(true);return label;}
    private void render(){
        content.getChildren().clear();content.getChildren().addAll(text("Soru, hata veya önerinizi iletin. Parola ve müşteri/finans kaydı göndermeyin. Görseldeki hassas alanları göndermeden önce kendiniz kapatın."),button("E-posta ile ulaş: "+contact.email(),this::email));
        if(token==null){content.getChildren().add(text("Giriş yapamıyorsanız e-posta ile ulaşabilirsiniz."));return;}
        if(!contact.enabled()){content.getChildren().add(text("Talep merkezi henüz etkin değil. E-posta ile ulaşabilirsiniz."));return;}
        if(busy||!draftReady)content.getChildren().add(new ProgressIndicator());
        if(!draftReady)return;
        var create=button("Yeni talep",()->compose(null));create.setDisable(busy);
        var refresh=button("Yenile",this::load);refresh.setDisable(busy);
        content.getChildren().add(new HBox(10,create,refresh));
        if(detail!=null){
            content.getChildren().addAll(text(detail.ticket().number()+" · "+detail.ticket().subject()),text(SupportModels.label(detail.ticket().status())));
            if(detail.nextBefore()!=null)content.getChildren().add(button("Önceki mesajlar",()->open(detail.ticket().id(),detail.nextBefore())));
            for(var message:detail.messages()){
                var box=new VBox(7,text(message.staff()?"Destek Ekibi":"Siz"),text(message.body()),text(message.createdAt()));box.setPadding(new Insets(12));box.getStyleClass().add("card");
                for(var file:message.attachments())box.getChildren().add(button(file.fileName(),()->download(file)));
                content.getChildren().add(box);
            }
            content.getChildren().add(new HBox(10,button("Yanıtla / yeniden aç",()->compose(detail.ticket().id())),button("Taleplere dön",()->{detail=null;composer=false;render();})));
        }else if(!composer&&list!=null){
            for(var ticket:list.items())content.getChildren().add(button((ticket.unread()?"● ":"")+ticket.number()+" · "+ticket.subject()+" — "+SupportModels.label(ticket.status()),()->open(ticket.id(),null)));
            if(list.items().isEmpty())content.getChildren().add(text("Henüz destek talebiniz yok."));
            var pagination=new HBox(10);if(page>0)pagination.getChildren().add(button("Önceki",()->{page--;load();}));if(list.hasNext())pagination.getChildren().add(button("Sonraki",()->{page++;load();}));content.getChildren().add(pagination);
        }
        if(composer)form();
    }
    private void compose(Long id){if(busy)return;if(draft.hasContent()&&!Objects.equals(draft.ticketId,id)){feedback.setText("Önce mevcut taslağı tamamlayın.");return;}if(!draft.hasContent()){draft=new Draft();draft.ticketId=id;}composer=true;render();}
    private void form(){
        boolean disabled=busy||draft.frozen;
        if(draft.ticketId==null){
            var subject=new TextField(draft.subject);subject.setId("support-subject");subject.setPromptText("Konu (en fazla 120 karakter)");subject.setDisable(disabled);subject.textProperty().addListener((o,a,b)->{draft.subject=b;saveLater();});
            var category=new ComboBox<String>();category.getItems().setAll("QUESTION","BUG","SUGGESTION");category.setValue(draft.category);category.setDisable(disabled);
            category.setConverter(new javafx.util.StringConverter<>(){public String toString(String s){return s==null?"":SupportModels.label(s);}public String fromString(String s){return s;}});
            category.valueProperty().addListener((o,a,b)->{draft.category=b;saveLater();});content.getChildren().addAll(subject,category);
        }
        var body=new TextArea(draft.body);body.setId("support-body");body.setPromptText("Açıklama (en fazla 4000 karakter)");body.setWrapText(true);body.setPrefRowCount(6);body.setDisable(disabled);body.textProperty().addListener((o,a,b)->{draft.body=b;saveLater();});content.getChildren().add(body);
        content.getChildren().add(text("En fazla 3 JPEG/PNG (2 MB) veya Pusula JSON raporu (256 KB). Ekler 30 gün, çözülen talepler 180 gün saklanır."));
        var add=button("Ekran görüntüsü / rapor ekle",this::attach);add.setDisable(disabled||draft.attachments.size()>=3);content.getChildren().add(add);
        for(int i=0;i<draft.attachments.size();i++){
            final int index=i;var file=draft.attachments.get(i);var remove=button("Kaldır",()->{draft.attachments.remove(index);saveLater();render();});remove.setDisable(disabled);
            var row=new HBox(10,text(file.mediaType()));
            if("application/json".equals(file.mediaType()))row.getChildren().add(button("Rapor önizlemesi",()->{
                var preview=new TextArea(new String(Base64.getDecoder().decode(file.dataBase64()),java.nio.charset.StandardCharsets.UTF_8));preview.setEditable(false);preview.setWrapText(true);
                var dialog=new Dialog<Void>();dialog.setTitle("Gönderilecek rapor");dialog.getDialogPane().setContent(preview);dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.showAndWait();
            }));else row.getChildren().add(button("Görsel önizlemesi",()->{
                var image=new javafx.scene.image.Image(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(file.dataBase64())),800,600,true,true);
                var dialog=new Dialog<Void>();dialog.setTitle("Gönderilecek görsel");dialog.getDialogPane().setContent(new javafx.scene.image.ImageView(image));dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);dialog.showAndWait();
            }));row.getChildren().add(remove);content.getChildren().add(row);
        }
        if(draft.frozen)content.getChildren().add(text("Gönderimin sonucu henüz doğrulanmadı. Aynı içerik ve anahtar korunuyor."));
        var send=button(draft.frozen?"Aynı gönderimi tekrar dene":"Gönder",this::send);send.setDisable(busy);content.getChildren().addAll(send,button("Taslağı saklayıp kapat",()->{composer=false;render();}));
    }
    private void saveLater(){
        long expected=++version;if(autosave!=null)autosave.cancel(false);var copy=gson.fromJson(gson.toJson(draft),Draft.class);
        autosave=IO.schedule(()->{if(!current()||version!=expected)return;try{store.save(account,copy);}catch(Exception e){Platform.runLater(()->{if(current())feedback.setText("Taslak cihazda saklanamadı; e-posta ile yedekleyebilirsiniz.");});}},350,TimeUnit.MILLISECONDS);
    }
    private void load(){if(busy)return;busy=true;render();call(api.list(bearer(),page),value->{list=value;feedback.setText(value.unreadCount()>0?value.unreadCount()+" okunmamış destek yanıtı":"");render();});}
    private void open(Long id,Long before){
        if(busy)return;busy=true;call(api.detail(bearer(),id,before),value->{
            if(before!=null&&detail!=null){var merged=new ArrayList<>(value.messages());merged.addAll(detail.messages());detail=new Detail(value.ticket(),merged,value.nextBefore());}else detail=value;
            composer=false;render();api.read(bearer(),id).enqueue(new Callback<>(){public void onResponse(Call<Void> c,Response<Void> r){}public void onFailure(Call<Void> c,Throwable e){}});
        });
    }
    private void send(){
        if(busy)return;if(draft.body.isBlank()||draft.body.length()>4000||draft.ticketId==null&&(draft.subject.isBlank()||draft.subject.length()>120)){feedback.setText("Konu ve açıklamayı kontrol edin.");return;}
        busy=true;draft.frozen=true;++version;if(autosave!=null)autosave.cancel(false);render();var frozen=gson.fromJson(gson.toJson(draft),Draft.class);
        IO.execute(()->{try{
            store.save(account,frozen);Platform.runLater(()->{if(!current())return;
                var request=frozen.ticketId==null?api.create(bearer(),frozen.payload()):api.reply(bearer(),frozen.ticketId,frozen.payload());
                call(request,value->{++version;detail=value;draft=new Draft();composer=false;feedback.setText("Mesajınız kaydedildi.");IO.execute(()->{try{store.clear(account);}catch(Exception ignored){}});render();});
            });
        }catch(Exception e){Platform.runLater(()->{if(current()){busy=false;feedback.setText("Gönderim öncesi korumalı taslak saklanamadı; hiçbir istek gönderilmedi.");render();}});}});
    }
    private void attach(){
        var chooser=new FileChooser();chooser.setTitle("Destek eki seç");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Görsel / Pusula raporu","*.jpg","*.jpeg","*.png","*.json"));var selected=chooser.showOpenDialog(getScene().getWindow());if(selected==null)return;
        IO.execute(()->{try{
            long size=Files.size(selected.toPath());boolean json=selected.getName().toLowerCase(Locale.ROOT).endsWith(".json");if(size<=0||size>(json?256*1024:2*1024*1024))throw new IllegalArgumentException();
            byte[] bytes=Files.readAllBytes(selected.toPath());String type=json?"application/json":selected.getName().toLowerCase(Locale.ROOT).endsWith(".png")?"image/png":"image/jpeg";
            Platform.runLater(()->{if(current()&&!draft.frozen&&draft.attachments.size()<3){draft.attachments.add(new Upload(type,Base64.getEncoder().encodeToString(bytes)));saveLater();render();}});
        }catch(Exception e){Platform.runLater(()->feedback.setText("Ek geçersiz veya boyut sınırından büyük."));}});
    }
    private void download(Attachment file){api.attachment(bearer(),file.id()).enqueue(new Callback<>(){
        public void onResponse(Call<okhttp3.ResponseBody> c,Response<okhttp3.ResponseBody> r){try(var body=r.body()){
            if(!r.isSuccessful()||body==null)throw new java.io.IOException();var bytes=body.bytes();
            Platform.runLater(()->{if(!current())return;var chooser=new FileChooser();chooser.setInitialFileName(file.mediaType().equals("application/json")?"baglanti-raporu.json":"ekran-goruntusu.jpg");var target=chooser.showSaveDialog(getScene().getWindow());if(target!=null)IO.execute(()->{try{Files.write(target.toPath(),bytes);}catch(Exception e){Platform.runLater(()->feedback.setText("Ek kaydedilemedi."));}});});
        }catch(Exception e){Platform.runLater(()->feedback.setText("Ek indirilemedi."));}}
        public void onFailure(Call<okhttp3.ResponseBody> c,Throwable e){Platform.runLater(()->feedback.setText("Ek indirilemedi."));}
    });}
    private void email(){try{java.awt.Desktop.getDesktop().mail(java.net.URI.create("mailto:"+contact.email()+"?subject=Pusula%20destek&body="+java.net.URLEncoder.encode(draft.body,java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){feedback.setText("E-posta uygulaması açılamadı. Adres: "+contact.email());}}
}
