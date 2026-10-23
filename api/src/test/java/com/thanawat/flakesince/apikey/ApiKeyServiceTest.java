package com.thanawat.flakesince.apikey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyServiceTest {

    @Test
    void generatedKeysHavePrefixAndLength() {
        String key = ApiKeyService.generateKey();

        assertTrue(key.startsWith("fs_live_"));
        assertEquals(8 + 43, key.length());
    }

    @Test
    void generatedKeysAreDifferentEachTime() {
        assertNotEquals(ApiKeyService.generateKey(), ApiKeyService.generateKey());
    }

    @Test
    void hashMatchesKnownSha256Value() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ApiKeyService.hash("abc"));
    }
}