package com.mpfm.backend.common.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具，支持 JSON 结构与 `key=value` 文本两种输入的敏感字段掩码处理。
 */
public final class SensitiveDataSanitizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String REDACTED = "<redacted>";
    private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
            "password", "token", "secret", "key", "credential", "avatar", "base64", "content"
    );
    private static final Pattern KV_PATTERN = Pattern.compile("(?i)(password|token|secret|key|credential)=([^\\s,;]+)");

    private SensitiveDataSanitizer() {
    }

    // 校验 JSON 字符串是否包含敏感信息
    public static String sanitizeJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payload);
            sanitizeNode(root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (IOException ignored) {
            return sanitizeText(payload);
        }
    }

    // 校验文本是否包含敏感信息
    public static String sanitizeText(String payload) {
        if (payload == null) {
            return null;
        }
        return KV_PATTERN.matcher(payload).replaceAll("$1=" + REDACTED);
    }

    // 校验节点是否包含敏感信息
    private static void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            sanitizeObject((ObjectNode) node);
            return;
        }
        if (node.isArray()) {
            sanitizeArray((ArrayNode) node);
        }
    }

    // 校验对象是否包含敏感信息
    private static void sanitizeObject(ObjectNode objectNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (isSensitiveKey(entry.getKey())) {
                objectNode.put(entry.getKey(), REDACTED);
                continue;
            }
            sanitizeNode(entry.getValue());
        }
    }

    // 校验数组是否包含敏感信息
    private static void sanitizeArray(ArrayNode arrayNode) {
        for (JsonNode child : arrayNode) {
            sanitizeNode(child);
        }
    }

    // 校验敏感字段是否包含敏感信息
    private static boolean isSensitiveKey(String key) {
        String lowered = key.toLowerCase(Locale.ROOT);
        for (String token : SENSITIVE_KEY_PARTS) {
            if (lowered.contains(token)) {
                return true;
            }
        }
        return false;
    }
}





