package com.pusula.backend.service;

import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.*;
import java.util.Map;
import java.util.Set;

@Service
public class GooglePlayAcknowledgementService {
    private static final Logger log=LoggerFactory.getLogger(GooglePlayAcknowledgementService.class);
    private final String packageName;
    private final GooglePlayAccessTokenProvider credentials;
    private final RestTemplate rest;
    @Autowired
    public GooglePlayAcknowledgementService(@Value("${google.play.package-name:}") String packageName,
            GooglePlayAccessTokenProvider credentials) { this(packageName,credentials,template()); }
    GooglePlayAcknowledgementService(String packageName, GooglePlayAccessTokenProvider credentials, RestTemplate rest) {
        this.packageName=packageName; this.credentials=credentials; this.rest=rest;
    }
    private static RestTemplate template() {
        var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(10000); factory.setReadTimeout(20000);
        return new RestTemplate(factory);
    }
    public boolean acknowledge(String token, String product) {
        if (packageName.isBlank() || token==null || token.isBlank() || token.length()>4096 || !Set.of("usta","patron").contains(product))
            return false;
        try {
            var uri=UriComponentsBuilder.fromHttpUrl("https://androidpublisher.googleapis.com")
                    .pathSegment("androidpublisher","v3","applications",packageName,"purchases","subscriptions",product,"tokens")
                    .pathSegment(token+":acknowledge").build().encode().toUri();
            var headers=new HttpHeaders(); headers.setBearerAuth(credentials.accessToken()); headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(uri,HttpMethod.POST,new HttpEntity<>(Map.of(),headers),Void.class);
            return true;
        } catch (Exception ex) {
            log.warn("Google Play acknowledgement deferred, type={}",ex.getClass().getSimpleName());
            return false;
        }
    }
}
