package com.pusula.backend.service;

import com.pusula.backend.dto.BusinessAssetDTO;
import com.pusula.backend.entity.BusinessAsset;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.BusinessAssetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessAssetServiceTest {
    @Mock private BusinessAssetRepository repository;
    @Mock private AuditLogService auditLogService;
    private BusinessAssetService service;

    @BeforeEach
    void setUp() {
        service = new BusinessAssetService(repository, auditLogService);
        User user = new User();
        user.setCompanyId(42L);
        user.setRole("COMPANY_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAlwaysAssignsAuthenticatedCompany() {
        when(repository.save(any())).thenAnswer(invocation -> {
            BusinessAsset asset = invocation.getArgument(0);
            asset.setId(7L);
            return asset;
        });

        BusinessAssetDTO dto = validDto();
        BusinessAssetDTO saved = service.create(dto);

        ArgumentCaptor<BusinessAsset> captor = ArgumentCaptor.forClass(BusinessAsset.class);
        verify(repository).save(captor.capture());
        assertEquals(42L, captor.getValue().getCompanyId());
        assertEquals(7L, saved.getId());
    }

    @Test
    void foreignTenantUpdateAndDeleteCannotTouchRecord() {
        when(repository.findByIdAndCompanyId(99L, 42L)).thenReturn(Optional.empty());

        assertTrue(service.update(99L, validDto()).isEmpty());
        assertTrue(!service.delete(99L));
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    private static BusinessAssetDTO validDto() {
        BusinessAssetDTO dto = new BusinessAssetDTO();
        dto.setAssetName("Vakum Pompası");
        dto.setQuantity(1);
        dto.setCondition("ACTIVE");
        return dto;
    }
}
