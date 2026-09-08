package com.pusula.desktop.controller;

import com.pusula.desktop.api.*;
import com.pusula.desktop.dto.*;
import com.pusula.desktop.dto.NetworkDTOs.*;
import com.pusula.desktop.network.RetrofitClient;
import com.pusula.desktop.util.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import retrofit2.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Inline, scrollable forms keep actions visible on small laptop screens. */
public class ServiceNetworkView extends VBox {
    private final ServiceNetworkApi api;
    private final MainDashboardController shell;
    private final Label feedback=new Label();
    private Context context;
    private int pageIndex;
    private int generation;
    private int pendingMutations;
    private String direction="incoming";
    private String search="";
    private String state="";
    private LocalDate from,to;
    private boolean memberMode;

    public ServiceNetworkView(MainDashboardController shell) {
        this(shell,RetrofitClient.getClient().create(ServiceNetworkApi.class));
    }
    ServiceNetworkView(MainDashboardController shell,ServiceNetworkApi api) {
        this(shell,api,null,null);
    }
    public ServiceNetworkView(MainDashboardController shell,String reference,Long id) {
        this(shell,RetrofitClient.getClient().create(ServiceNetworkApi.class),reference,id);
    }
    ServiceNetworkView(MainDashboardController shell,ServiceNetworkApi api,String reference,Long id) {
        this.api=api;this.shell=shell;setSpacing(14);setPadding(new Insets(16));feedback.setWrapText(true);
        getStyleClass().add("network-view");feedback.getStyleClass().add("network-feedback");
        if(!SessionManager.isAdmin()) { getChildren().setAll(text("Servis ağı yalnızca yöneticilere açıktır."));return; }
        frame("Servis Ağı",text("Yükleniyor…"));
        call(api.context(),c->{
            context=c;
            if("NETWORK_ORDER".equals(reference)&&id!=null)detail(id);
            else if("NETWORK_MEMBER".equals(reference)&&id!=null)call(api.member(id),m->{memberMode=true;showForm("Servis Ağı Bağlantısı",new VBox(12,memberCard(m)),button("Listeyi Aç",this::browse));});
            else browse();
        });
    }
    private void reloadContext() { call(api.context(),c->{context=c;browse();}); }
    private void frame(String title,Node body,Node... actions) {
        generation++;
        Label eyebrow=text("SERVİS AĞI");eyebrow.setMinWidth(Region.USE_PREF_SIZE);eyebrow.getStyleClass().add("network-eyebrow");
        Label heading=text(title);heading.setMaxWidth(520);heading.setMinWidth(0);heading.getStyleClass().add("network-title");
        VBox headingBlock=new VBox(3,eyebrow,heading);headingBlock.getStyleClass().add("network-heading-block");
        FlowPane actionPane=new FlowPane(8,8);actionPane.setAlignment(Pos.CENTER_RIGHT);actionPane.getStyleClass().add("network-page-actions");actionPane.getChildren().addAll(actions);
        HBox toolbar=new HBox(12,headingBlock);toolbar.setAlignment(Pos.CENTER_LEFT);HBox.setHgrow(headingBlock,Priority.ALWAYS);toolbar.getChildren().add(actionPane);toolbar.getStyleClass().add("network-toolbar");
        feedback.setText("");feedback.setVisible(false);feedback.setManaged(false);
        getChildren().setAll(toolbar,feedback,body);VBox.setVgrow(body,Priority.ALWAYS);
    }
    private void browse() {
        if(context==null) return;
        VBox content=new VBox(10);
        ComboBox<String> section=new ComboBox<>(FXCollections.observableArrayList("Gelen İşler","Gönderilen İşler","Alt Servisler / Davetler"));
        section.setValue(memberMode?"Alt Servisler / Davetler":direction.equals("incoming")?"Gelen İşler":"Gönderilen İşler");
        section.setOnAction(e->{memberMode=section.getValue().startsWith("Alt");direction=section.getValue().startsWith("Gönderilen")?"outgoing":"incoming";pageIndex=0;browse();});
        TextField q=new TextField(search);q.setPromptText("İsim, iş başlığı, firma veya iş no ara");q.setPrefWidth(300);
        ComboBox<String> status=new ComboBox<>(FXCollections.observableArrayList("","SENT","ACCEPTED","REJECTED","CANCELLED"));status.setConverter(labels());status.setValue(state);
        DatePicker start=new DatePicker(from),end=new DatePicker(to);
        start.setPromptText("Başlangıç");end.setPromptText("Bitiş");
        Runnable apply=()->{search=q.getText();state=status.getValue();from=start.getValue();to=end.getValue();pageIndex=0;browse();};q.setOnAction(e->apply.run());
        section.setPrefWidth(205);status.setPrefWidth(150);start.setPrefWidth(155);end.setPrefWidth(155);
        FlowPane filters=new FlowPane(10,8,section,q);filters.getStyleClass().addAll("filter-bar","network-filter-card");
        if(!memberMode) filters.getChildren().addAll(status,start,end);
        filters.getChildren().addAll(button("Ara",apply),button("Temizle",()->{search="";state="";from=null;to=null;pageIndex=0;browse();}));
        ListView<Object> list=new ListView<>();list.getStyleClass().add("network-list");list.setMinHeight(360);list.setPrefHeight(520);
        list.setPlaceholder(emptyState(
                memberMode ? "Henüz alt servis bağlantısı yok" : "Bu görünümde iş bulunamadı",
                memberMode ? "Yeni bir alt servis oluşturarak veya işletme davet ederek ağınızı kurabilirsiniz."
                        : "Filtreleri temizleyin veya diğer iş görünümünü kontrol edin.",
                memberMode && context.canManage() && context.writable() ? ()->memberForm(true) : null));
        list.setCellFactory(v->new ListCell<>() { @Override protected void updateItem(Object value,boolean empty) {
            super.updateItem(value,empty);setText(null);
            Node graphic=empty||value==null?null:value instanceof Member m?memberCard(m):orderCard((Order)value);
            if(graphic instanceof Region region){region.setMinWidth(0);region.prefWidthProperty().bind(list.widthProperty().subtract(36));region.maxWidthProperty().bind(list.widthProperty().subtract(36));}
            setGraphic(graphic);
        } });
        Label count=text("Yükleniyor…");count.getStyleClass().add("pagination-count");Button prev=button("Önceki",()->{pageIndex--;browse();}),next=button("Sonraki",()->{pageIndex++;browse();});prev.setDisable(pageIndex==0);next.setDisable(true);
        FlowPane metrics=new FlowPane(10,10);metrics.getStyleClass().add("network-metrics");
        metrics.getChildren().addAll(
                metric("Aktif alt servis",context.usedMembers()+" / "+context.maxMembers()),
                metric("Bu ay gönderilen",context.usedMonthlyOrders()+" / "+context.maxMonthlyOrders()),
                metric("Ağ yönetimi",context.canManage()?"Açık":"Sınırlı"));
        HBox pagination=new HBox(10,prev,count,next);pagination.setAlignment(Pos.CENTER);pagination.getStyleClass().add("pagination-bar");
        content.getChildren().addAll(metrics,filters,list,pagination);content.getStyleClass().add("network-browser");VBox.setVgrow(list,Priority.ALWAYS);
        Button invite=button("İşletme Davet Et",()->memberForm(false)),create=button("Yeni Alt Servis",()->memberForm(true));
        create.getStyleClass().remove("button-secondary");create.getStyleClass().add("btn-primary");
        invite.setDisable(!context.canManage()||!context.writable());create.setDisable(invite.isDisabled());
        List<Node> toolbarActions=new ArrayList<>(List.of(invite,create,button("Yenile",this::reloadContext)));
        if("SUPER_ADMIN".equals(SessionManager.getUserRole())) toolbarActions.add(button("Ağ Yetkisi",this::policyForm));
        frame("Ağ Operasyonları",content,toolbarActions.toArray(Node[]::new));
        int version=generation;
        if(memberMode) call(api.members(pageIndex,search),p->{if(version!=generation)return;list.getItems().setAll(p.items());count.setText(p.totalElements()+" kayıt · Sayfa "+(p.page()+1));next.setDisable(!p.hasNext());});
        else call(api.orders(direction,search,state==null||state.isEmpty()?null:state,from==null?null:from.toString(),to==null?null:to.toString(),pageIndex),p->{if(version!=generation)return;list.getItems().setAll(p.items());count.setText(p.totalElements()+" iş · Sayfa "+(p.page()+1));next.setDisable(!p.hasNext());});
    }
    private Node memberCard(Member m) {
        VBox card=new VBox(6,text(m.parentName()+" → "+m.childName()),text(label(m.status())+" · "+safe(m.region())));
        FlowPane actions=new FlowPane(8,8);
        boolean ownParent=Objects.equals(m.parentCompanyId(),context.companyId());
        if("ACTIVE".equals(m.status())&&ownParent&&context.canManage()) actions.getChildren().add(styledButton("İş Gönder",()->dispatchForm(m),"btn-primary"));
        if("INVITED".equals(m.status())&&!ownParent) actions.getChildren().addAll(styledButton("Daveti Kabul Et",()->call(api.decide(m.id(),Map.of("accept",true)),x->reloadContext()),"btn-success"),styledButton("Reddet",()->call(api.decide(m.id(),Map.of("accept",false)),x->reloadContext()),"btn-danger"));
        if(List.of("ACTIVE","INVITED").contains(m.status())) actions.getChildren().add(styledButton("Bağlantıyı Kapat",()->noteForm("Bağlantıyı kapat",note->call(api.close(m.id(),Map.of("note",note)),x->reloadContext())),"btn-danger"));
        actions.setDisable(!context.writable());card.getChildren().add(actions);card.getStyleClass().add("network-card");return card;
    }
    private Node orderCard(Order o) {
        VBox card=new VBox(5,text("#"+o.id()+" · "+o.title()),text(o.customerName()+" · "+o.parentName()+" → "+o.childName()),
                text(date(o.scheduledDate())+" · "+label(o.status())+(o.ticketStatus()==null?"":" · "+label(o.ticketStatus()))),button("İşi ve Geçmişi Aç",()->detail(o.id())));
        card.getStyleClass().add("network-card");return card;
    }
    private void memberForm(boolean create) {
        String requestKey=UUID.randomUUID().toString();
        VBox form=new VBox(12);TextField code=field("İşletme kodu"),name=field("İşletme adı"),region=field("Bölge / il"),admin=field("Yönetici adı soyadı"),username=field("Kullanıcı adı");PasswordField password=new PasswordField();password.setPromptText("En az 8 karakter, harf ve rakam");
        if(create) form.getChildren().addAll(text("Alt servis ayrı bir işletme ve Çırak planında 14 günlük deneme hesabı olarak açılır. Giriş kodu ve kullanıcı adını işlem sonunda kaydedin."),name,admin,username,password);
        else form.getChildren().addAll(text("Davet, işletmenin kendi yöneticisi kabul ettikten sonra etkinleşir."),code);
        form.getChildren().add(region);
        Button save=button(create?"Alt Servis Oluştur":"Davet Gönder",()->{
            if(create) call(api.create(Map.of("requestKey",requestKey,"name",name.getText(),"region",region.getText(),"adminName",admin.getText(),"username",username.getText(),"password",password.getText())),created->{password.clear();VBox receipt=new VBox(12,text("Alt servis oluşturuldu: "+created.member().childName()),text("İşletme kodu ve kullanıcı adını alt servis yöneticisiyle paylaşın."));TextArea credentials=new TextArea("İşletme kodu: "+created.orgCode()+"\nKullanıcı adı: "+created.username());credentials.setEditable(false);receipt.getChildren().add(credentials);frame("Hesap Bilgileri",receipt,button("Ağa Dön",this::reloadContext));});
            else call(api.invite(Map.of("orgCode",code.getText(),"region",region.getText())),x->reloadContext());
        });
        showForm(create?"Yeni Alt Servis":"İşletme Daveti",form,save);
    }
    private void dispatchForm(Member member) {
        TextField title=field("İş emri başlığı"),name=field("Müşteri adı"),phone=field("Telefon"),address=field("Servis adresi"),time=field("Başlangıç saati: 14:00"),endTime=field("Bitiş saati: 16:00 (isteğe bağlı)");
        DatePicker date=new DatePicker(LocalDate.now());TextArea instruction=new TextArea();instruction.setPromptText("Alt servis ve atanan teknisyen için özel iş talimatı");instruction.setWrapText(true);instruction.setPrefRowCount(4);
        String requestKey=UUID.randomUUID().toString();
        VBox form=new VBox(12,text("Alıcı: "+member.childName()),text("Bu iş, alt servis kabul ettiğinde onun servis fişine dönüşür. Ağ gönderimi ayrıca gelir veya gider oluşturmaz."),title,name,phone,address,date,time,endTime,instruction);
        showForm("Yeni Ağ İş Emri",form,button("Alt Servise Gönder",()->{
            try {
                LocalDate day=date.getValue();if(day==null)throw new IllegalArgumentException("Tarih seçin.");
                LocalDateTime start=day.atTime(LocalTime.parse(time.getText().trim()));
                Map<String,Object> body=new HashMap<>(Map.of("membershipId",member.id(),"requestKey",requestKey,"title",title.getText(),"customerName",name.getText(),"customerPhone",phone.getText(),"customerAddress",address.getText(),"instruction",instruction.getText(),"scheduledDate",start.toString()));
                if(!endTime.getText().isBlank())body.put("scheduledEndDate",day.atTime(LocalTime.parse(endTime.getText().trim())).toString());
                call(api.dispatch(body),o->detail(o.id()));
            } catch(Exception ex){error("Tarih ve saatleri kontrol edin. Saat biçimi: 14:00.");}
        }));
    }
    private void detail(Long id) { generation++;call(api.order(id),this::renderDetail); }
    private void renderDetail(Order o) {
        memberMode=false;direction=Objects.equals(context.companyId(),o.parentCompanyId())?"outgoing":"incoming";
        VBox content=new VBox(12,text(o.parentName()+" → "+o.childName()),text(o.customerName()+" · "+safe(o.customerPhone())),text(safe(o.customerAddress())),
                text("İlk randevu: "+date(o.scheduledDate())+" – "+date(o.scheduledEndDate())),text("Durum: "+label(o.status())+" · "+label(o.ticketStatus())),
                text("Güncel randevu: "+date(o.currentScheduledDate())),text("İş talimatı: "+safe(o.instruction())),text("Sonuç: "+safe(o.resolutionNote())));
        FlowPane actions=new FlowPane(10,8);boolean child=Objects.equals(context.companyId(),o.childCompanyId());
        if("SENT".equals(o.status())) {
            if(child) actions.getChildren().addAll(button("Kabul Et ve Fiş Aç",()->acceptForm(o)),button("İşi Reddet",()->noteForm("Ret gerekçesi",note->call(api.reject(o.id(),Map.of("note",note)),x->detail(o.id())))));
            else actions.getChildren().add(button("Gönderimi Geri Çek",()->noteForm("Geri çekme gerekçesi",note->call(api.cancel(o.id(),Map.of("note",note)),x->detail(o.id())))));
        }
        actions.getChildren().add(button("Karşı Firmaya Not",()->noteForm("Paylaşılan iş notu",note->call(api.note(o.id(),Map.of("note",note)),x->detail(o.id())))));
        actions.setDisable(!context.writable());content.getChildren().add(actions);
        if(child&&o.acceptedTicketId()!=null) content.getChildren().add(button("Servis Fişini Aç",()->shell.openTicketFromNotification(o.acceptedTicketId())));
        else if(o.acceptedTicketId()!=null)content.getChildren().add(text("Alt servis fiş no: "+o.acceptedTicketId()+". Finans, stok ve özel servis notları yalnızca alt serviste yönetilir."));
        VBox history=new VBox(8);Button more=button("Daha Fazla Geçmiş",()->{});int[] historyPage={0};
        Runnable loadHistory=()->call(api.history(o.id(),historyPage[0]),p->{p.items().forEach(e->history.getChildren().add(text(date(e.createdAt())+" · "+label(e.action())+"\n"+safe(e.note()))));historyPage[0]++;more.setVisible(p.hasNext());more.setManaged(p.hasNext());});
        more.setOnAction(e->loadHistory.run());content.getChildren().addAll(text("İş Geçmişi"),history,more);
        showForm("#"+o.id()+" · "+o.title(),content,button("Yenile",()->detail(o.id())));loadHistory.run();
    }
    private void acceptForm(Order o) {
        CheckBox existing=new CheckBox("Kendi müşteri kaydıma bağla");TextField search=field("Mevcut müşteri ara");ComboBox<CustomerDTO> customer=new ComboBox<>();customer.setMaxWidth(Double.MAX_VALUE);
        customer.setConverter(new StringConverter<>(){public String toString(CustomerDTO c){return c==null?"":c.getName()+" · "+safe(c.getPhone());}public CustomerDTO fromString(String s){return null;}});
        ComboBox<UserDTO> tech=new ComboBox<>();tech.setMaxWidth(Double.MAX_VALUE);tech.setPromptText("Teknisyen daha sonra atanabilir");
        tech.setConverter(new StringConverter<>(){public String toString(UserDTO u){return u==null?"":u.getFullName();}public UserDTO fromString(String s){return null;}});
        List<CustomerDTO> all=new ArrayList<>();search.textProperty().addListener((obs,a,b)->{customer.getItems().setAll(all.stream().filter(c->!b.isBlank()&&(c.getName()+" "+safe(c.getPhone())).toLowerCase(Locale.forLanguageTag("tr")).contains(b.toLowerCase(Locale.forLanguageTag("tr")))).limit(40).toList());});
        search.disableProperty().bind(existing.selectedProperty().not());customer.disableProperty().bind(existing.selectedProperty().not());
        VBox form=new VBox(12,text("Kabul, alt serviste normal servis fişi açar. Mevcut müşteri seçilmezse bu işteki ad/telefon/adresle yeni müşteri oluşturulur."),existing,search,customer,text("Atanacak teknisyen"),tech);
        showForm("İşi Kabul Et",form,button("Kabul Et ve Servis Fişini Aç",()->{
            if(existing.isSelected()&&customer.getValue()==null){error("Mevcut müşteriyi seçin.");return;}
            Map<String,Object> body=new HashMap<>();if(existing.isSelected())body.put("customerId",customer.getValue().getId());if(tech.getValue()!=null)body.put("technicianId",tech.getValue().getId());
            call(api.accept(o.id(),body),x->detail(o.id()));
        }));
        call(RetrofitClient.getClient().create(CustomerApi.class).getAllCustomers(),all::addAll);
        call(RetrofitClient.getClient().create(UserApi.class).getTechnicians(),list->tech.getItems().setAll(list));
    }
    private void noteForm(String title,Consumer<String> save) { TextArea note=new TextArea();note.setWrapText(true);note.setPromptText("Gerekçe veya paylaşılan not");showForm(title,new VBox(12,note),button("Kaydet",()->{if(note.getText().isBlank()){error("Not yazın.");return;}save.accept(note.getText());})); }
    private void policyForm() {
        TextField id=field("Yetki verilecek işletme ID"),limit=field("Alt servis limiti"),monthly=field("Aylık gönderim limiti");CheckBox enabled=new CheckBox("Ağ yönetimi etkin");
        showForm("Servis Ağı Yetkisi",new VBox(12,id,limit,monthly,enabled),button("Yetkiyi Kaydet",()->{try{call(api.configure(Long.valueOf(id.getText()),Map.of("enabled",enabled.isSelected(),"maxMembers",Integer.valueOf(limit.getText()),"maxMonthlyOrders",Integer.valueOf(monthly.getText()))),x->reloadContext());}catch(NumberFormatException ex){error("İşletme ID ve limitler tam sayı olmalıdır.");}}));
    }
    private void showForm(String title,VBox form,Button save) {
        form.setMaxWidth(760);form.setPadding(new Insets(4));
        ScrollPane scroll=new ScrollPane(form);scroll.setFitToWidth(true);scroll.getStyleClass().add("responsive-form-scroll");
        save.getStyleClass().remove("button-secondary");save.getStyleClass().add("btn-primary");
        FlowPane actions=new FlowPane(10,8,button("Ağa Dön",this::browse),save);
        actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);actions.getStyleClass().add("dialog-action-bar");
        VBox container=new VBox(12,scroll,actions);VBox.setVgrow(scroll,Priority.ALWAYS);
        frame(title,container);
    }
    private <T> void call(Call<T> call,Consumer<T> success) {
        int requestGeneration=generation;
        boolean mutation=!"GET".equals(call.request().method());
        if(mutation){pendingMutations++;setDisable(true);}
        call.enqueue(new Callback<>() {
            public void onResponse(Call<T> c,Response<T> r) {
                String message="İşlem tamamlanamadı (HTTP "+r.code()+").";
                if(!r.isSuccessful())try{var json=com.google.gson.JsonParser.parseString(r.errorBody().string()).getAsJsonObject();if(json.has("message"))message=json.get("message").getAsString();}catch(Exception ignored){}
                String detail=message;Platform.runLater(()->{
                    if(mutation){pendingMutations--;setDisable(pendingMutations>0);}
                    if(requestGeneration!=generation)return;
                    if(r.isSuccessful())success.accept(r.body());else error(detail);
                });
            }
            public void onFailure(Call<T> c,Throwable e) {Platform.runLater(()->{
                if(mutation){pendingMutations--;setDisable(pendingMutations>0);}
                if(requestGeneration==generation)error("Sunucuya ulaşılamadı. Yenileyerek tekrar deneyin.");
            });}
        });
    }
    private void error(String message){feedback.setText(message);feedback.setVisible(true);feedback.setManaged(true);}
    private static VBox metric(String label,String value){
        Label caption=text(label);caption.getStyleClass().add("network-metric-label");
        Label amount=text(value);amount.getStyleClass().add("network-metric-value");
        VBox card=new VBox(5,caption,amount);card.setPrefWidth(190);card.getStyleClass().add("network-metric-card");return card;
    }
    private static VBox emptyState(String title,String message,Runnable action){
        Label glyph=new Label();glyph.setGraphic(FontIcon.of(MaterialDesignA.ACCOUNT_MULTIPLE,28));glyph.getStyleClass().add("network-empty-icon");
        Label heading=text(title);heading.getStyleClass().add("empty-state-title");
        Label detail=text(message);detail.getStyleClass().add("empty-state-subtitle");detail.setMaxWidth(420);detail.setAlignment(Pos.CENTER);
        VBox box=new VBox(8,glyph,heading,detail);box.setAlignment(Pos.CENTER);box.setMaxWidth(500);box.getStyleClass().add("network-empty-state");
        if(action!=null)box.getChildren().add(styledButton("Yeni Alt Servis",action,"btn-primary"));
        return box;
    }
    private static Label text(String s){Label l=new Label(s);l.setWrapText(true);l.setMaxWidth(Double.MAX_VALUE);return l;}
    private static TextField field(String hint){TextField f=new TextField();f.setPromptText(hint);return f;}
    private static Button button(String title,Runnable action){return styledButton(title,action,"button-secondary");}
    private static Button styledButton(String title,Runnable action,String styleClass){Button b=new Button(title);b.setMinWidth(Region.USE_PREF_SIZE);b.setAccessibleText(title);b.setOnAction(e->action.run());b.getStyleClass().add(styleClass);return b;}
    private static String safe(String value){return value==null?"":value;}
    private static String date(String raw){if(raw==null)return "—";try{return LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));}catch(Exception e){return raw;}}
    private static StringConverter<String> labels(){return new StringConverter<>(){public String toString(String v){return label(v);}public String fromString(String v){return v;}};}
    public static String label(String s){if(s==null||s.isEmpty())return "Tümü";return switch(s){case "SENT"->"Kabul bekliyor";case "ACCEPTED"->"Kabul edildi";case "REJECTED"->"Reddedildi";case "CANCELLED"->"İptal edildi";case "INVITED"->"Davet bekliyor";case "ACTIVE"->"Aktif";case "DECLINED"->"Davet reddedildi";case "CLOSED"->"Bağlantı kapalı";case "PENDING"->"Atama bekliyor";case "ASSIGNED"->"Atandı";case "IN_PROGRESS"->"İşlemde";case "COMPLETED"->"Tamamlandı";case "NOTE"->"Paylaşılan not";default->s;};}
}
