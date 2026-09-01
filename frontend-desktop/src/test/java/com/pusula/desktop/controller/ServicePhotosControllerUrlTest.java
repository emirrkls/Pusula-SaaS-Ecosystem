package com.pusula.desktop.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServicePhotosControllerUrlTest {
    @Test
    void resolvesRootRelativeAndPathRelativePhotoUrls() {
        assertEquals(
                "https://api.pusulaiklimlendirme.com/uploads/service/photo.jpg",
                ServicePhotosController.resolvePhotoUrl("/uploads/service/photo.jpg"));
        assertEquals(
                "https://api.pusulaiklimlendirme.com/uploads/service/photo.jpg",
                ServicePhotosController.resolvePhotoUrl("uploads/service/photo.jpg"));
    }

    @Test
    void keepsAbsoluteUrlsAndRejectsEmptyValues() {
        String absolute = "https://cdn.example.com/photo.jpg";
        assertEquals(absolute, ServicePhotosController.resolvePhotoUrl(absolute));
        assertThrows(IllegalArgumentException.class, () -> ServicePhotosController.resolvePhotoUrl(" "));
    }
}
