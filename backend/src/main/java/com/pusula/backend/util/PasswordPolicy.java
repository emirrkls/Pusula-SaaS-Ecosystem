package com.pusula.backend.util;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void requireStrong(String password) {
        if (password == null || password.length() < 8
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Şifre en az 8 karakter olmalı ve harf ile rakam içermelidir.");
        }
    }
}
