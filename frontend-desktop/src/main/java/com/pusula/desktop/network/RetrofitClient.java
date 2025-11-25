package com.pusula.desktop.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

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
}
