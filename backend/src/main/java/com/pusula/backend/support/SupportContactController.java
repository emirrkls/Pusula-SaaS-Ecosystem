package com.pusula.backend.support;

import org.springframework.web.bind.annotation.*;
@RestController
public class SupportContactController {
    private final SupportService service;
    public SupportContactController(SupportService service) { this.service=service; }
    @GetMapping("/api/public/support-contact") public SupportDtos.Contact contact() { return service.contact(); }
}
