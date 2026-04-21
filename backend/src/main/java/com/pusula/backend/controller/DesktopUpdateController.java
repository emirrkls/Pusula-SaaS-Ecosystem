package com.pusula.backend.controller;

import com.pusula.backend.dto.DesktopVersionDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/desktop-version")
public class DesktopUpdateController {

    @GetMapping
    public ResponseEntity<DesktopVersionDTO> getLatestVersion() {
        // In a real scenario, this could be fetched from a database or application properties
        DesktopVersionDTO updateInfo = DesktopVersionDTO.builder()
                .latestVersion("1.0.1") // the fake new version
                .downloadUrl("https://pusulaservis.com/downloads/v2.zip")
                .mandatory(false)
                .releaseNotes("Sistem performansı artırıldı ve finans hataları giderildi.")
                .build();
        return ResponseEntity.ok(updateInfo);
    }
}
