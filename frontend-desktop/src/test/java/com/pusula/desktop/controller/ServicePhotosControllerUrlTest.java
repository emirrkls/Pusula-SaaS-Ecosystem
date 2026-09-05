package com.pusula.desktop.controller;

import org.junit.jupiter.api.Test;

import com.pusula.desktop.dto.ServicePhotoDTO;

import java.time.LocalDateTime;
import java.util.List;
import javafx.geometry.Rectangle2D;

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

    @Test
    void groupsPhotosByTicketAndSortsNewestServiceFirst() {
        ServicePhotoDTO older = photo(10L, 1L, "Eski müşteri", LocalDateTime.of(2026, 1, 4, 10, 0));
        ServicePhotoDTO newestFirst = photo(20L, 2L, "Yeni müşteri", LocalDateTime.of(2026, 2, 5, 12, 0));
        ServicePhotoDTO newestSecond = photo(20L, 3L, "Yeni müşteri", LocalDateTime.of(2026, 2, 5, 12, 0));

        List<ServicePhotosController.TicketPhotoGroup> groups =
                ServicePhotosController.groupPhotos(List.of(older, newestSecond, newestFirst));

        assertEquals(2, groups.size());
        assertEquals(20L, groups.getFirst().ticketId());
        assertEquals(2, groups.getFirst().photos().size());
        assertEquals(3L, groups.getFirst().photos().getFirst().getId());
    }

    @Test
    void coverViewportCropsWithoutDistortingPortraitAndLandscapeImages() {
        assertEquals(new Rectangle2D(0, 375, 1000, 500),
                ServicePhotosController.coverViewport(1000, 1250, 2, 1));
        assertEquals(new Rectangle2D(500, 0, 1000, 1000),
                ServicePhotosController.coverViewport(2000, 1000, 1, 1));
    }

    @Test
    void prefersAfterPhotoAsServiceFolderCover() {
        ServicePhotoDTO before = photo(10L, 1L, "Müşteri", LocalDateTime.now());
        before.setType("BEFORE");
        ServicePhotoDTO after = photo(10L, 2L, "Müşteri", LocalDateTime.now());
        after.setType("AFTER");

        assertEquals(after, ServicePhotosController.selectCover(List.of(before, after)));
    }

    private ServicePhotoDTO photo(Long ticketId, Long id, String customer, LocalDateTime serviceDate) {
        ServicePhotoDTO photo = new ServicePhotoDTO();
        photo.setTicketId(ticketId);
        photo.setId(id);
        photo.setCustomerName(customer);
        photo.setServiceDate(serviceDate);
        photo.setUploadedAt(serviceDate.plusMinutes(id));
        return photo;
    }
}
