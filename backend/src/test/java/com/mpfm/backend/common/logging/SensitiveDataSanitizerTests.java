package com.mpfm.backend.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTests {

    @Test
    void shouldMaskNestedSensitiveFieldsInJson() {
        String input = """
                {
                  "user": "alice",
                  "password": "abc123",
                  "profile": {
                    "token": "t-1",
                    "nested": {"secretKey": "k-1"}
                  },
                  "items": [
                    {"name": "a", "apiKey": "k1"},
                    {"name": "b", "value": 1}
                  ]
                }
                """;

        String out = SensitiveDataSanitizer.sanitizeJson(input);

        assertTrue(out.contains("\"password\":\"<redacted>\""));
        assertTrue(out.contains("\"token\":\"<redacted>\""));
        assertTrue(out.contains("\"secretKey\":\"<redacted>\""));
        assertTrue(out.contains("\"apiKey\":\"<redacted>\""));
        assertTrue(out.contains("\"user\":\"alice\""));
    }

    @Test
    void shouldMaskKeyValueLikeTextWhenNotJson() {
        String input = "password=abc token=t1 normal=v";
        String out = SensitiveDataSanitizer.sanitizeText(input);
        assertEquals("password=<redacted> token=<redacted> normal=v", out);
    }
}

