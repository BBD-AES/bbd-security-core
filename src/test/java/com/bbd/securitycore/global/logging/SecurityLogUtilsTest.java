package com.bbd.securitycore.global.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecurityLogUtilsTest {

    @Test
    void fingerprintIsStableWithoutExposingRawValue() {
        String rawValue = "keycloak-sub-12345";

        String fingerprint = SecurityLogUtils.fingerprint(rawValue);

        assertEquals(fingerprint, SecurityLogUtils.fingerprint(rawValue));
        assertEquals(12, fingerprint.length());
        assertFalse(fingerprint.contains(rawValue));
    }

    @Test
    void fingerprintReturnsBlankMarkerForBlankValue() {
        assertEquals("blank", SecurityLogUtils.fingerprint(" "));
    }
}
