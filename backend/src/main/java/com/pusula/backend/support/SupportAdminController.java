package com.pusula.backend.support;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController @RequestMapping("/api/superadmin/support") @PreAuthorize("hasAuthority('SUPPORT_READ')")
public class SupportAdminController {
    private final SupportService service;
    public SupportAdminController(SupportService service) { this.service=service; }
    @GetMapping("/unread-count") public java.util.Map<String,Long> unread(){return java.util.Map.of("count",service.unread(true));}
    @GetMapping("/tickets") public SupportDtos.Page list(@RequestParam(defaultValue="0") int page,@RequestParam(required=false) String status,@RequestParam(required=false) String category,@RequestParam(required=false) Long companyId,@RequestParam(required=false) String search) { return service.list(true,page,status,category,companyId,search); }
    @GetMapping("/tickets/{id}") public SupportDtos.Detail detail(@PathVariable Long id,@RequestParam(required=false) Long before) { return service.detail(id,true,before); }
    @PostMapping("/tickets/{id}/messages") @PreAuthorize("hasAuthority('SUPPORT_WRITE')") public SupportDtos.Detail reply(@PathVariable Long id,@Valid @RequestBody SupportDtos.Reply input) { return service.reply(id,input,true); }
    @PatchMapping("/tickets/{id}/read") @PreAuthorize("hasAuthority('SUPPORT_WRITE')") public void read(@PathVariable Long id) { service.read(id,true); }
    @GetMapping("/attachments/{id}") public ResponseEntity<byte[]> attachment(@PathVariable Long id) { return SupportController.download(service.attachment(id,true)); }
}
