package com.pusula.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServicePhoto;
import com.pusula.backend.entity.ServiceTicket;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@ContextConfiguration(classes = ServicePhotoRepositoryPaginationTest.RepositoryTestApplication.class)
class ServicePhotoRepositoryPaginationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.pusula.backend.entity")
    @EnableJpaRepositories(basePackages = "com.pusula.backend.repository")
    static class RepositoryTestApplication {
    }

    @Autowired TestEntityManager entityManager;
    @Autowired ServicePhotoRepository repository;

    @Test
    void pagesDistinctServiceFilesAndAppliesTenantAndSearchFilters() {
        Long firstTicket = createTicketWithPhoto(1L, "Ahmet Subaşı", "Klima bakımı",
                LocalDateTime.of(2026, 8, 5, 12, 0), ServicePhoto.PhotoType.AFTER, "Seri numarası");
        Long secondTicket = createTicketWithPhoto(1L, "Mehmet Yavuz", "VRF devreye alma",
                LocalDateTime.of(2026, 7, 15, 10, 0), ServicePhoto.PhotoType.BEFORE, "Dış ünite");
        createTicketWithPhoto(2L, "Başka Firma", "Gizli iş",
                LocalDateTime.of(2026, 9, 1, 9, 0), ServicePhoto.PhotoType.AFTER, "Gizli");
        entityManager.flush();

        Page<Long> firstPage = repository.findServiceFileTicketIds(
                1L,
                false, ServicePhoto.PhotoType.BEFORE,
                false, 0L,
                false, LocalDateTime.of(1970, 1, 1, 0, 0),
                false, LocalDateTime.of(2100, 1, 1, 0, 0),
                false, "%",
                PageRequest.of(0, 1));
        assertEquals(2, firstPage.getTotalElements());
        assertEquals(firstTicket, firstPage.getContent().get(0));

        Page<Long> searched = repository.findServiceFileTicketIds(
                1L,
                false, ServicePhoto.PhotoType.BEFORE,
                false, 0L,
                false, LocalDateTime.of(1970, 1, 1, 0, 0),
                false, LocalDateTime.of(2100, 1, 1, 0, 0),
                true, "%vrf%",
                PageRequest.of(0, 10));
        assertEquals(1, searched.getTotalElements());
        assertEquals(secondTicket, searched.getContent().get(0));

        Page<Long> dated = repository.findServiceFileTicketIds(
                1L,
                false, ServicePhoto.PhotoType.BEFORE,
                false, 0L,
                true, LocalDateTime.of(2026, 8, 1, 0, 0),
                false, LocalDateTime.of(2100, 1, 1, 0, 0),
                false, "%",
                PageRequest.of(0, 10));
        assertEquals(1, dated.getTotalElements());
        assertEquals(firstTicket, dated.getContent().get(0));

        Page<Long> beforePhotos = repository.findServiceFileTicketIds(
                1L,
                true, ServicePhoto.PhotoType.BEFORE,
                false, 0L,
                false, LocalDateTime.of(1970, 1, 1, 0, 0),
                false, LocalDateTime.of(2100, 1, 1, 0, 0),
                false, "%",
                PageRequest.of(0, 10));
        assertEquals(1, beforePhotos.getTotalElements());
        assertEquals(secondTicket, beforePhotos.getContent().get(0));
    }

    private Long createTicketWithPhoto(Long companyId, String customerName, String description,
                                       LocalDateTime date, ServicePhoto.PhotoType type, String note) {
        Customer customer = new Customer();
        customer.setCompanyId(companyId);
        customer.setName(customerName);
        entityManager.persist(customer);

        ServiceTicket ticket = new ServiceTicket();
        ticket.setCompanyId(companyId);
        ticket.setCustomerId(customer.getId());
        ticket.setStatus(ServiceTicket.TicketStatus.COMPLETED);
        ticket.setDescription(description);
        ticket.setScheduledDate(date);
        ticket.setCompletedAt(date);
        entityManager.persist(ticket);

        ServicePhoto photo = ServicePhoto.builder()
                .ticketId(ticket.getId())
                .url("/uploads/service-photos/" + companyId + "/" + ticket.getId() + "/photo.jpg")
                .type(type)
                .note(note)
                .uploadedByName("Teknisyen")
                .uploadedAt(date)
                .build();
        entityManager.persist(photo);
        return ticket.getId();
    }
}
