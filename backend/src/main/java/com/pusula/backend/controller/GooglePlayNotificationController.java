package com.pusula.backend.controller;

import com.pusula.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

@RestController
public class GooglePlayNotificationController {
    private final GooglePlayPushAuthenticator authentication;
    private final GooglePlayNotificationService notifications;
    public GooglePlayNotificationController(GooglePlayPushAuthenticator authentication, GooglePlayNotificationService notifications) {
        this.authentication = authentication; this.notifications = notifications;
    }
    @PostMapping(value = "/api/public/google/play-notifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(HttpServletRequest request) throws IOException {
        authentication.authenticate(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (request.getContentLengthLong() > 32768) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        byte[] body = request.getInputStream().readNBytes(32769);
        if (body.length > 32768) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        notifications.receive(body);
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).build();
    }
}
