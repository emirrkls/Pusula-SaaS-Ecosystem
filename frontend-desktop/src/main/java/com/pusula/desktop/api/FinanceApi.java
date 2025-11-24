package com.pusula.desktop.api;

import retrofit2.Call;
import retrofit2.http.GET;

import java.math.BigDecimal;

public interface FinanceApi {
    @GET("api/finance/daily-income")
    Call<BigDecimal> getDailyIncome();
}
