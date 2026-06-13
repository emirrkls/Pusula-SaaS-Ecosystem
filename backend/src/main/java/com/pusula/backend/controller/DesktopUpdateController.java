package com.pusula.backend.controller;

import com.pusula.backend.dto.DesktopVersionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/desktop-version")
public class DesktopUpdateController {

    @Value("${pusula.desktop.latest-version:3.0.0}")
    private String latestVersion;

    @Value("${pusula.desktop.download-url:}")
    private String downloadUrl;

    @Value("${pusula.desktop.mandatory:false}")
    private boolean mandatory;

    @Value("${pusula.desktop.release-notes:}")
    private String releaseNotes;

    @GetMapping
    public ResponseEntity<DesktopVersionDTO> getLatestVersion() {
        DesktopVersionDTO updateInfo = DesktopVersionDTO.builder()
                .latestVersion(latestVersion)
                .downloadUrl(downloadUrl)
                .mandatory(mandatory)
                .releaseNotes(releaseNotes)
                .build();
        return ResponseEntity.ok(updateInfo);
    }
}
