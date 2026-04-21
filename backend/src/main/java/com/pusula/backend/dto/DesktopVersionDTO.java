package com.pusula.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesktopVersionDTO {
    private String latestVersion;
    private String downloadUrl;
    private boolean mandatory;
    private String releaseNotes;
}
