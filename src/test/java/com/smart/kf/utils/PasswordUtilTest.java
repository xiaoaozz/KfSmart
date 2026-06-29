package com.smart.kf.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void encode_returnsNonNullBcryptHash() {
        String encoded = PasswordUtil.encode("password123");
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$"));
    }

    @Test
    void encode_samePasswordProducesDifferentHashes() {
        String hash1 = PasswordUtil.encode("samePassword");
        String hash2 = PasswordUtil.encode("samePassword");
        assertNotEquals(hash1, hash2, "BCrypt should produce different salts each time");
    }

    @Test
    void matches_correctPassword_returnsTrue() {
        String raw = "mySecret";
        String encoded = PasswordUtil.encode(raw);
        assertTrue(PasswordUtil.matches(raw, encoded));
    }

    @Test
    void matches_wrongPassword_returnsFalse() {
        String encoded = PasswordUtil.encode("correctPassword");
        assertFalse(PasswordUtil.matches("wrongPassword", encoded));
    }

    @Test
    void matches_emptyPassword_doesNotMatchNonEmpty() {
        String encoded = PasswordUtil.encode("notEmpty");
        assertFalse(PasswordUtil.matches("", encoded));
    }

    @Test
    void matches_differentCases_returnsFalse() {
        String encoded = PasswordUtil.encode("Password");
        assertFalse(PasswordUtil.matches("password", encoded));
    }
}
