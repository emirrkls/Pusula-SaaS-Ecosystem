package com.pusula.backend.support;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestControllerAdvice(assignableTypes={SupportController.class,SupportAdminController.class,SupportContactController.class})
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class SupportErrors {
    @ModelAttribute public void privateResponse(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff"); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<?> database() { return ResponseEntity.status(503).body(Map.of("code","SUPPORT_TEMPORARILY_UNAVAILABLE","message","Destek kaydı şu anda doğrulanamadı. Aynı gönderimi daha sonra tekrar deneyin.")); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("code","SUPPORT_ERROR","message",e.getReason()==null?"Destek işlemi tamamlanamadı.":e.getReason())); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<?> forbidden() {return ResponseEntity.status(403).body(Map.of("code","SUPPORT_FORBIDDEN","message","Bu destek işlemi için yetkiniz yok."));}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid() {return ResponseEntity.badRequest().body(Map.of("code","SUPPORT_INVALID","message","Alanları ve ek dosya sınırlarını kontrol edin."));}
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<?> unexpected() {return ResponseEntity.status(503).body(Map.of("code","SUPPORT_TEMPORARILY_UNAVAILABLE","message","Sonuç doğrulanamadı. Aynı gönderimle tekrar deneyin."));}
}
