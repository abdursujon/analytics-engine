package com.sujon.analytics_engine.service.hash;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class ContentHasherUnitTest {

    private static final String SHA_256_OF_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private final ContentHasher contentHasher = new ContentHasher();

    @Test
    void hashTextToHex_shouldReturnKnownSha256Digest_whenInputIsAbc() {
        assertEquals(SHA_256_OF_ABC, contentHasher.hashTextToHex("abc"));
    }

    @Test
    void hashBytesToHex_shouldMatchHashTextToHex_whenSameContentProvided() {
        byte[] abcBytes = "abc".getBytes(StandardCharsets.UTF_8);
        assertEquals(contentHasher.hashTextToHex("abc"), contentHasher.hashBytesToHex(abcBytes));
    }

    @Test
    void hashTextToHex_shouldReturn64HexCharacters_whenAnyInputProvided() {
        String hexDigest = contentHasher.hashTextToHex("driver,number\nMax,1");
        assertEquals(64, hexDigest.length());
        assertTrue(hexDigest.matches("[0-9a-f]+"));
    }

    @Test
    void hashTextToHex_shouldReturnDifferentDigests_whenInputsDiffer() {
        assertNotEquals(contentHasher.hashTextToHex("a,b\n1,2"), contentHasher.hashTextToHex("a,b\n1,3"));
    }

    @Test
    void normaliseCsvForStableHash_shouldProduceSameHash_whenOnlyLineEndingsAndBlankLinesDiffer() {
        String windowsCsv = "a,b\r\n1,2\r\n\r\n";
        String unixCsv = "a,b\n1,2";

        String windowsHash = contentHasher.hashTextToHex(contentHasher.normaliseCsvForStableHash(windowsCsv));
        String unixHash = contentHasher.hashTextToHex(contentHasher.normaliseCsvForStableHash(unixCsv));

        assertEquals(unixHash, windowsHash);
    }
}
