package com.onlinecurlearnmanos.common.security;

public record EncryptedValue(
        String ciphertext,
        String iv
) {
}
