package com.pusula.backend.service;

import com.pusula.backend.entity.PushEnvironment;

public interface ApnsGateway {
    ApnsDeliveryResult send(String deviceToken, PushEnvironment environment, String payload);
}
