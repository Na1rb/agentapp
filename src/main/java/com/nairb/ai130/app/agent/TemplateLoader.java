package com.nairb.ai130.app.agent;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TemplateLoader {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-z][a-z0-9_-]*");

    public String load(String strategy, String role) {
        String safeStrategy = normalize(strategy, "strategy");
        String safeRole = normalize(role, "role");
        String path = "template/" + safeStrategy + "/" + safeRole + ".txt";
        ClassPathResource resource = new ClassPathResource(path);

        if (!resource.exists()) {
            throw new IllegalArgumentException("Agent prompt template not found: " + path);
        }

        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Agent prompt template: " + path, e);
        }
    }

    private String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
        return normalized;
    }
}
