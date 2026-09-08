package com.pusula.desktop.api;

import com.pusula.desktop.dto.WhatsAppIntegrationStatusDTO;
import com.pusula.desktop.dto.WhatsAppOnboardingStartDTO;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface WhatsAppIntegrationApi {
    @GET("api/integrations/whatsapp/status")
    Call<WhatsAppIntegrationStatusDTO> status();

    @POST("api/integrations/whatsapp/onboarding-session")
    Call<WhatsAppOnboardingStartDTO> startOnboarding();
}
