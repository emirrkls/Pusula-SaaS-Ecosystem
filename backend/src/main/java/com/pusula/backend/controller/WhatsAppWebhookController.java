package com.pusula.backend.controller;

import com.pusula.backend.service.WhatsAppWebhookSecurity;
import com.pusula.backend.service.WhatsAppWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/public/whatsapp/webhook")
public class WhatsAppWebhookController {
    private final WhatsAppWebhookSecurity security;
    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookController(WhatsAppWebhookSecurity security,
                                     WhatsAppWebhookService webhookService) {
        this.security = security;
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!security.isVerificationRequestValid(mode, verifyToken) || challenge == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!security.isPayloadSignatureValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        webhookService.processStatusEvents(new String(rawBody, StandardCharsets.UTF_8));
        return ResponseEntity.ok().build();
    }
}
