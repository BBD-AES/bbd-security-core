package com.bbd.securitycore.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BbdSecurityPropertiesTest {

    @Test
    void usesShortTtlForUserSnapshotNotFoundCache() {
        BbdSecurityProperties properties = new BbdSecurityProperties();

        assertEquals("user:snapshot:not-found:", properties.getUserSnapshotNotFoundCacheKeyPrefix());
        assertEquals(30, properties.getUserSnapshotNotFoundCacheTtlSeconds());
    }
}
