package com.pusula.backend.dto;

import java.util.List;

public record ServicePhotoPageDTO(
        List<ServicePhotoDTO> items,
        int page,
        int size,
        long totalServiceFiles,
        int totalPages,
        boolean hasNext
) {
}
