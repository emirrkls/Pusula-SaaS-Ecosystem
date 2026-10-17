package com.pusula.backend.support;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController @RequestMapping("/api/support")
public class SupportController {
    private final SupportService service;
    public SupportController(SupportService service) { this.service=service; }
    @GetMapping("/unread-count") public java.util.Map<String,Long> unread(){return java.util.Map.of("count",service.unread(false));}
    @GetMapping("/tickets") public SupportDtos.Page list(@RequestParam(defaultValue="0") int page,@RequestParam(required=false) String status,@RequestParam(required=false) String category) { return service.list(false,page,status,category,null,null); }
    @GetMapping("/tickets/{id}") public SupportDtos.Detail detail(@PathVariable Long id,@RequestParam(required=false) Long before) { return service.detail(id,false,before); }
    @PostMapping("/tickets") public SupportDtos.Detail create(@Valid @RequestBody SupportDtos.Create input) { return service.create(input); }
    @PostMapping("/tickets/{id}/messages") public SupportDtos.Detail reply(@PathVariable Long id,@Valid @RequestBody SupportDtos.Reply input) { return service.reply(id,input,false); }
    @PatchMapping("/tickets/{id}/read") public void read(@PathVariable Long id) { service.read(id,false); }
    @GetMapping("/attachments/{id}") public ResponseEntity<byte[]> attachment(@PathVariable Long id) { return download(service.attachment(id,false)); }
    static ResponseEntity<byte[]> download(SupportAttachment file) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=\""+file.getFileName()+"\"").contentType(MediaType.parseMediaType(file.getMediaType())).body(file.getContent()); }
}
