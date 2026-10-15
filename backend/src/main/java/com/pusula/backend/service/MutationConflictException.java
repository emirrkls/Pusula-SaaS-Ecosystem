package com.pusula.backend.service;

public class MutationConflictException extends RuntimeException {
    private final String code;
    public MutationConflictException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
