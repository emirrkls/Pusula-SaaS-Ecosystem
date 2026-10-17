package com.pusula.backend.support;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.core.MethodParameter;
import java.io.*;
import java.lang.reflect.Type;

@ControllerAdvice(assignableTypes={SupportController.class,SupportAdminController.class})
public class SupportBodyAdvice extends RequestBodyAdviceAdapter {
    static final long MAX_BODY=8_500_000;
    @Override public boolean supports(MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) { return true; }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter parameter,Type type,Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if(input.getHeaders().getContentLength()>MAX_BODY) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Destek gönderimi çok büyük.");
        return new HttpInputMessage() {
            public HttpHeaders getHeaders(){return input.getHeaders();}
            public InputStream getBody() throws IOException {return new FilterInputStream(input.getBody()) {
                long total;
                private void count(int size)throws IOException{if(size>0 && (total+=size)>MAX_BODY)throw new IOException("Support body limit exceeded");}
                @Override public int read()throws IOException{int value=super.read();count(value<0?0:1);return value;}
                @Override public int read(byte[] data,int offset,int length)throws IOException{int size=in.read(data,offset,length);count(size);return size;}
            };}
        };
    }
}
