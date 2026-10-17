package com.pusula.desktop.controller;

import com.google.gson.Gson;
import com.pusula.desktop.support.SupportApi;
import com.pusula.desktop.support.SupportDraftStore;
import com.pusula.desktop.util.SessionManager;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Real JavaFX view + Windows DPAPI drafts; no external endpoint or real login. */
@EnabledIfEnvironmentVariable(named="SUPPORT_UI_TEST",matches="true")
class SupportCenterFlowTest {
    @TempDir Path drafts;
    private HttpServer server;
    private final AtomicInteger lists=new AtomicInteger();
    private final List<String> keys=new CopyOnWriteArrayList<>();
    private final Set<String> committed=ConcurrentHashMap.newKeySet();
    private static final String TICKET="{\"id\":7,\"number\":\"SUP-QA-7\",\"subject\":\"Yerel test talebi\",\"category\":\"QUESTION\",\"status\":\"NEW\",\"unread\":false}";

    @BeforeAll static void startFx() throws Exception {
        var ready=new CountDownLatch(1);
        try {Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}
        catch(IllegalStateException alreadyStarted){ready.countDown();}
        assertTrue(ready.await(10,TimeUnit.SECONDS));
    }
    @BeforeEach void startServer() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/",exchange->{
            String path=exchange.getRequestURI().getPath();int status=200;String response;
            if(path.endsWith("support-contact"))response="{\"enabled\":true,\"email\":\"support@example.invalid\"}";
            else if("POST".equals(exchange.getRequestMethod())){
                var request=new Gson().fromJson(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),Map.class);
                String key=String.valueOf(request.get("clientKey"));keys.add(key);committed.add(key);
                // Model a committed submission whose first response cannot confirm success.
                status=keys.size()==1?503:200;response=status==503?"{}":"{\"ticket\":"+TICKET+",\"messages\":[]}";
            } else if(lists.incrementAndGet()>1){status=503;response="{}";}
            else response="{\"items\":["+TICKET+"],\"unreadCount\":0,\"hasNext\":false}";
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        SessionManager.setSession("synthetic-support-token","qa-local","TECHNICIAN",0L);
    }
    @AfterEach void stopServer(){server.stop(0);SessionManager.clearSession();}
    private SupportCenterView view() throws Exception {
        SupportApi api=new Retrofit.Builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/")
                .addConverterFactory(GsonConverterFactory.create()).build().create(SupportApi.class);
        return fx(()->{var view=new SupportCenterView(api,new SupportDraftStore(drafts));new Scene(view,800,700);view.resize(800,700);view.applyCss();view.layout();return view;});
    }
    @Test void retryPreservesSubmissionKeyAndProtectedDraft() throws Exception {
        var view=view();waitFor(()->hasButton(view,"Yeni talep")&&!button(view,"Yeni talep").isDisabled());
        fx(()->{button(view,"Yeni talep").fire();((TextField)view.lookup("#support-subject")).setText("QA konu");((TextArea)view.lookup("#support-body")).setText("Yalnızca yapay test içeriği");button(view,"Gönder").fire();return null;});
        waitFor(()->hasButton(view,"Aynı gönderimi tekrar dene")&&!button(view,"Aynı gönderimi tekrar dene").isDisabled());
        assertEquals(1,keys.size());
        var saved=new SupportDraftStore(drafts).load(SupportDraftStore.accountKey(0L,"qa-local"));
        assertTrue(saved.frozen);assertEquals(keys.get(0),saved.clientKey);
        fx(()->{button(view,"Aynı gönderimi tekrar dene").fire();return null;});
        waitFor(()->hasButton(view,"Yanıtla / yeniden aç"));
        assertEquals(2,keys.size());assertEquals(keys.get(0),keys.get(1));assertEquals(1,committed.size());
    }
    @Test void refreshFailureKeepsExistingTicketVisible() throws Exception {
        var view=view();waitFor(()->view.lookupAll(".button").stream().anyMatch(n->n instanceof Button b&&b.getText().contains("SUP-QA-7")));
        fx(()->{button(view,"Yenile").fire();return null;});
        waitFor(()->view.lookupAll(".label").stream().anyMatch(n->n instanceof Label l&&l.getText().contains("503")));
        assertTrue(fx(()->view.lookupAll(".button").stream().anyMatch(n->n instanceof Button b&&b.getText().contains("SUP-QA-7"))));
    }
    @Test void anonymousViewHasEmailButNoSubmit() throws Exception {
        SessionManager.clearSession();var view=view();
        waitFor(()->hasButton(view,"E-posta ile ulaş: support@example.invalid"));
        assertFalse(fx(()->hasButton(view,"Yeni talep")));assertFalse(fx(()->hasButton(view,"Gönder")));assertEquals(0,lists.get());
    }
    private static Button button(SupportCenterView view,String title){return view.lookupAll(".button").stream().filter(n->n instanceof Button b&&title.equals(b.getText())).map(n->(Button)n).findFirst().orElseThrow();}
    private static boolean hasButton(SupportCenterView view,String title){return view.lookupAll(".button").stream().anyMatch(n->n instanceof Button b&&title.equals(b.getText()));}
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(!fx(condition::getAsBoolean)&&System.nanoTime()<deadline)Thread.sleep(25);
        assertTrue(fx(condition::getAsBoolean),"Expected support UI state did not appear");
    }
    private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<>(action);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS);}
}
