package com.sujon.analytics_engine.service.hash;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ContentHasher {

    private String bytesToHexString(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public String hashBytesToHex(byte[] bytes) {
        return bytesToHexString(createSha256Digester().digest(bytes));
    }

    public String hashTextToHex(String input) {
        return bytesToHexString(createSha256Digester().digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    public MessageDigest createSha256Digester() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String normaliseCsvForStableHash(String csv) {
        return csv
                .replace("\r\n", "\n")
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}