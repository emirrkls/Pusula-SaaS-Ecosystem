package com.pusula.desktop.network;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import okhttp3.Interceptor;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;

public class RetrofitClient {
        private static Retrofit retrofit = null;
        private static final String BASE_URL = "http://127.0.0.1:8080/";

        public static Retrofit getClient() {
                if (retrofit == null) {
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                                        .addInterceptor(chain -> {
                                                okhttp3.Request original = chain.request();
                                                String token = com.pusula.desktop.util.SessionManager.getToken();
                                                if (token != null && !token.isEmpty()) {
                                                        okhttp3.Request request = original.newBuilder()
                                                                        .header("Authorization", "Bearer " + token)
                                                                        .method(original.method(), original.body())
                                                                        .build();
                                                        return chain.proceed(request);
                                                }
                                                return chain.proceed(original);
                                        })
                                        .addInterceptor(new ForbiddenInterceptor())
                                        .build();

                        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                                        .registerTypeAdapter(java.time.LocalDateTime.class,
                                                        new com.pusula.desktop.util.LocalDateTimeTypeAdapter())
                                        .registerTypeAdapter(java.time.LocalDate.class,
                                                        new com.pusula.desktop.util.LocalDateTypeAdapter())
                                        .create();

                        retrofit = new Retrofit.Builder()
                                        .baseUrl(BASE_URL)
                                        .client(client)
                                        .addConverterFactory(GsonConverterFactory.create(gson))
                                        .build();
                }
                return retrofit;
        }

        /**
         * Interceptor to handle 403 Forbidden responses globally
         */
        private static class ForbiddenInterceptor implements Interceptor {
                @Override
                public Response intercept(Chain chain) throws IOException {
                        Response response = chain.proceed(chain.request());

                        if (response.code() == 403) {
                                Platform.runLater(() -> {
                                        Alert alert = new Alert(Alert.AlertType.ERROR);
                                        alert.setTitle("Erişim Reddedildi");
                                        alert.setHeaderText("Yetki Hatası");
                                        alert.setContentText(
                                                        "Erişim Reddedildi: Bu işlem için yetkiniz bulunmamaktadır.");
                                        alert.showAndWait();
                                });
                        }

                        return response;
                }
        }
}
