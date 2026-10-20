package com.pusula.desktop.api;

import com.pusula.desktop.dto.WhatsAppIntegrationStatusDTO;
import com.pusula.desktop.dto.WhatsAppOnboardingStartDTO;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import com.pusula.desktop.dto.WhatsAppMirrorItemDTO;
import java.util.List;

public interface WhatsAppIntegrationApi {
    @GET("api/integrations/whatsapp/status")
    Call<WhatsAppIntegrationStatusDTO> status();

    @POST("api/integrations/whatsapp/onboarding-session")
    Call<WhatsAppOnboardingStartDTO> startOnboarding();

    @GET("api/integrations/whatsapp/coexistence/contacts")
    Call<List<WhatsAppMirrorItemDTO>> contacts(@Query("page") int page);
    @GET("api/integrations/whatsapp/coexistence/messages")
    Call<List<WhatsAppMirrorItemDTO>> messages(@Query("peerKey") String peerKey, @Query("page") int page);
}
