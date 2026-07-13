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
        public static final String BASE_URL = "https://api.pusulaiklimlendirme.com/";
        public static final String SUPPRESS_FORBIDDEN_ALERT_HEADER = "X-Pusula-Suppress-Forbidden-Alert";

        public static Retrofit getClient() {
                if (retrofit == null) {
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                                        .addInterceptor(chain -> {
                                                okhttp3.Request original = chain.request();
                                                String path = original.url().encodedPath();
                                                boolean publicAuth = "/api/auth/authenticate".equals(path)
                                                                || "/api/auth/register-individual".equals(path)
                                                                || "/api/auth/google".equals(path)
                                                                || "/api/auth/register".equals(path);
                                                String token = publicAuth ? null
                                                                : com.pusula.desktop.util.SessionManager.getToken();
                                                System.out.println("=== RetrofitClient Interceptor ===");
                                                System.out.println("Request URL: " + original.url());
                                                System.out.println("Token from SessionManager: " + (token != null
                                                                ? "EXISTS (length=" + token.length() + ")"
                                                                : "NULL"));
                                                if (token != null && !token.isEmpty()) {
                                                        okhttp3.Request request = original.newBuilder()
                                                                        .header("Authorization", "Bearer " + token)
                                                                        .method(original.method(), original.body())
                                                                        .build();
                                                        System.out.println("Authorization header added");
                                                        return chain.proceed(request);
                                                }
                                                System.out.println(
                                                                "No token - proceeding without Authorization header");
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
         * Interceptor to handle 403 Forbidden responses globally.
         */
        private static class ForbiddenInterceptor implements Interceptor {
                private static boolean forbiddenAlertVisible = false;
                private static long lastForbiddenAlertAt = 0L;
                private static final long FORBIDDEN_ALERT_COOLDOWN_MS = 3000L;

                @Override
                public Response intercept(Chain chain) throws IOException {
                        Response response = chain.proceed(chain.request());

                        if (response.code() == 403) {
                                // Do not show alerts for audit logs, public endpoints, or background refreshes.
                                String url = response.request().url().toString();
                                boolean suppressAlert = "true".equalsIgnoreCase(response.request()
                                                .header(SUPPRESS_FORBIDDEN_ALERT_HEADER));
                                if (suppressAlert) {
                                        System.err.println("Suppressed background 403 alert for URL: " + url);
                                } else if (!url.contains("/audit-logs/")
                                                && !url.contains("/public/")
                                                && !url.contains("/api/auth/")
                                                && shouldShowForbiddenAlert()) {
                                        Platform.runLater(() -> {
                                                try {
                                                        Alert alert = new Alert(Alert.AlertType.ERROR);
                                                        alert.setTitle("Eri\u015fim Reddedildi");
                                                        alert.setHeaderText("Yetki Hatas\u0131");
                                                        alert.setContentText(
                                                                        "Eri\u015fim Reddedildi: Bu i\u015flem i\u00e7in yetkiniz bulunmamaktad\u0131r.\nURL: "
                                                                                        + url);
                                                        alert.showAndWait();
                                                } finally {
                                                        markForbiddenAlertClosed();
                                                }
                                        });
                                }
                        }

                        return response;
                }

                private static synchronized boolean shouldShowForbiddenAlert() {
                        long now = System.currentTimeMillis();
                        if (forbiddenAlertVisible || now - lastForbiddenAlertAt < FORBIDDEN_ALERT_COOLDOWN_MS) {
                                return false;
                        }
                        forbiddenAlertVisible = true;
                        lastForbiddenAlertAt = now;
                        return true;
                }

                private static synchronized void markForbiddenAlertClosed() {
                        forbiddenAlertVisible = false;
                        lastForbiddenAlertAt = System.currentTimeMillis();
                }
        }
}
