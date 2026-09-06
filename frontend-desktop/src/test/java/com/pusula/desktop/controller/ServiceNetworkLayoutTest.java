package com.pusula.desktop.controller;

import com.pusula.desktop.api.ServiceNetworkApi;
import com.pusula.desktop.util.SessionManager;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic localhost fixtures only. Never authenticates or calls the live backend. */
@EnabledIfEnvironmentVariable(named="NETWORK_UI_TEST",matches="true")
class ServiceNetworkLayoutTest {
    @BeforeAll static void fx() throws Exception {
        CountDownLatch ready=new CountDownLatch(1);
        try{Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});}catch(IllegalStateException alreadyStarted){ready.countDown();}
        assertTrue(ready.await(10,TimeUnit.SECONDS));
    }
    @Test void rendersDirectoryAndFormWithVisibleActionsOnSmallScreens() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/service-network/",exchange->{
            String body=exchange.getRequestURI().getPath().endsWith("context")
                    ? "{\"companyId\":1,\"canManage\":true,\"writable\":true,\"maxMembers\":400,\"usedMembers\":128,\"maxMonthlyOrders\":20000,\"usedMonthlyOrders\":750}"
                    : """
                    {"items":[{"id":42,"membershipId":1,"parentCompanyId":2,"childCompanyId":1,
                    "parentName":"Örnek Isıtma Sistemleri Ana Firma","childName":"Örnek Yetkili Servis",
                    "title":"Çok uzun açıklamalı ticari klima sistemi kontrolü ve iç dış ünite montajının planlanması",
                    "customerName":"Örnek Müşteri","scheduledDate":"2026-10-05T14:00:00","status":"SENT"}],
                    "totalElements":1,"page":0,"totalPages":1,"hasNext":false}
                    """;
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            ServiceNetworkApi api=new Retrofit.Builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/")
                    .addConverterFactory(GsonConverterFactory.create()).build().create(ServiceNetworkApi.class);
            SessionManager.setSession("synthetic-test","Test admin","COMPANY_ADMIN",1L);
            ServiceNetworkView view=onFx(()->new ServiceNetworkView(null,api));
            Scene scene=onFx(()->{Scene s=new Scene(view,900,600);s.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());return s;});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(onFx(()->view.getChildren().isEmpty())&&System.nanoTime()<deadline)Thread.sleep(25);
            assertFalse(onFx(()->view.getChildren().isEmpty()));
            while(onFx(()->view.lookupAll(".list-view").stream().noneMatch(n->n instanceof javafx.scene.control.ListView<?> list&&!list.getItems().isEmpty()))&&System.nanoTime()<deadline)Thread.sleep(25);
            onFx(()->{render(view,900,600,"network-list-900x600.png");assertButtonsFit(view);return null;});
            onFx(()->{view.lookupAll(".button").stream().filter(n->n instanceof Button b&&"Yeni Alt Servis".equals(b.getText())).map(n->(Button)n).findFirst().orElseThrow().fire();return null;});
            onFx(()->{render(view,900,600,"network-form-900x600.png");assertButtonsFit(view);return null;});
            assertNotNull(scene.getRoot());
        } finally {server.stop(0);SessionManager.clearSession();}
    }
    private void assertButtonsFit(ServiceNetworkView view){
        for(var node:view.lookupAll(".button"))if(node instanceof Button b&&b.isVisible()) {
            assertTrue(b.getWidth()+1>=b.minWidth(-1),"Clipped action: "+b.getText());
            var bounds=b.localToScene(b.getBoundsInLocal());assertTrue(bounds.getMaxX()<=901,"Action outside viewport: "+b.getText());
        }
    }
    private void render(ServiceNetworkView view,int width,int height,String file) throws Exception {
        view.resize(width,height);view.applyCss();view.layout();WritableImage image=view.snapshot(null,new WritableImage(width,height));
        BufferedImage output=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        Files.createDirectories(Path.of("target/network-ui"));ImageIO.write(output,"png",Path.of("target/network-ui",file).toFile());
    }
    private static <T>T onFx(Callable<T> work)throws Exception{FutureTask<T> future=new FutureTask<>(work);Platform.runLater(future);return future.get(10,TimeUnit.SECONDS);}
}
