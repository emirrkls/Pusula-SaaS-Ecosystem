package com.pusula.backend.service;

/** Only token verifiers may construct this from authenticated provider claims. */
public record VerifiedSocialIdentity(String provider, String subject, String email, String fullName,
                                     boolean emailAuthoritative) {}
