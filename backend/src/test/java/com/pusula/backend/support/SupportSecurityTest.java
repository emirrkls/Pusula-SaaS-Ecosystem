package com.pusula.backend.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.context.TenantInterceptor;
import com.pusula.backend.context.TenantContext;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.service.JwtService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportSecurityTest {
    @AfterEach void clear(){SecurityContextHolder.clearContext();TenantContext.clear();}
    @Test void billingReadOnlyExemptionIsLimitedToTheExactOwnSupportWrites(){
        var companies=mock(CompanyRepository.class);var company=new Company();company.setIsReadOnly(true);when(companies.findById(7L)).thenReturn(Optional.of(company));
        var interceptor=new TenantInterceptor(mock(JwtService.class),companies);var user=new User();user.setId(1L);user.setCompanyId(7L);user.setRole("TECHNICIAN");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
        for(String path:List.of("/api/support/tickets","/api/support/tickets/12/messages","/api/support/tickets/12/read"))assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST",path),new MockHttpServletResponse(),new Object()),path);
        for(String path:List.of("/api/expenses","/api/support-other/tickets","/api/support/tickets/delete-all","/api/superadmin/support/tickets/1/messages")){var response=new MockHttpServletResponse();assertFalse(interceptor.preHandle(new MockHttpServletRequest("POST",path),response,new Object()),path);assertEquals(403,response.getStatus());}
    }
    @Test void onlyExplicitSupportStaffHaveSupportAuthorities(){
        for(String role:List.of("COMPANY_ADMIN","TECHNICIAN")){var u=new User();u.setRole(role);assertTrue(u.getAuthorities().stream().noneMatch(a->a.getAuthority().startsWith("SUPPORT_")));}
        var reader=new User();reader.setRole("SUPER_ADMIN_READONLY");assertTrue(reader.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("SUPPORT_READ")));assertTrue(reader.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("SUPPORT_WRITE")));
    }
    @Test void invalidOrOversizedFilesCannotEnterThePrivateStore(){
        var files=new SupportFiles(new ObjectMapper());
        assertThrows(IllegalArgumentException.class,()->files.validate(new SupportDtos.Upload("text/html",Base64.getEncoder().encodeToString("<script>secret</script>".getBytes()))));
        assertThrows(IllegalArgumentException.class,()->files.validate(new SupportDtos.Upload("image/jpeg","AA==")));
        assertThrows(IllegalArgumentException.class,()->files.validate(new SupportDtos.Upload("application/json",Base64.getEncoder().encodeToString("{\"token\":\"secret\"}".getBytes()))));
        assertThrows(IllegalArgumentException.class,()->files.validate(new SupportDtos.Upload("image/jpeg",Base64.getEncoder().encodeToString(new byte[SupportFiles.MAX_BYTES+1]))));
    }
    @Test void imageIsReencodedToJpegInsteadOfServingUserSuppliedMetadataOrType()throws Exception{
        var image=new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);
        var safe=new SupportFiles(new ObjectMapper()).validate(new SupportDtos.Upload("image/png",Base64.getEncoder().encodeToString(out.toByteArray())));
        assertEquals("image/jpeg",safe.type());assertEquals("ekran-goruntusu.jpg",safe.name());assertNotNull(javax.imageio.ImageIO.read(new ByteArrayInputStream(safe.bytes())));
    }
    @Test void diagnosticsPreserveOnlyKnownCodesAndScrubRoutes()throws Exception{
        String json="{\"schemaVersion\":1,\"records\":[{\"method\":\"POST\",\"outcome\":\"transportError\",\"route\":\"/api/customers/private-person\",\"startedAt\":123.456,\"Authorization\":\"Bearer secret\",\"errors\":[{\"domain\":\"NSURLErrorDomain\",\"code\":-1200}]}]}";
        var mapper=new ObjectMapper();var result=new SupportFiles(mapper).validate(new SupportDtos.Upload("application/json",Base64.getEncoder().encodeToString(json.getBytes())));var clean=mapper.readTree(result.bytes());
        assertEquals("transportError",clean.path("records").get(0).path("outcome").asText());assertEquals("/api/customers/:redacted",clean.path("records").get(0).path("route").asText());assertFalse(new String(result.bytes()).contains("secret"));assertEquals(-1200,clean.path("records").get(0).path("errors").get(0).path("code").asInt());
    }
}
