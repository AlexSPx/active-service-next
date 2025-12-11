package com.services.active.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/legal")
@Tag(name = "Legal", description = "Endpoints for legal documents")
public class LegalController {

    private final ResourceLoader resourceLoader;

    public LegalController(@Qualifier("webApplicationContext") ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private ResponseEntity<String> serveMarkdown(String classpathLocation) {
        try {
            // Prefer Spring ResourceLoader with explicit classpath prefix
            Resource resource = resourceLoader.getResource("classpath:" + classpathLocation);
            if (!resource.exists()) {
                // Fallback: try the thread context classloader
                try (InputStream is = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(classpathLocation)) {
                    if (is == null) {
                        return ResponseEntity
                                .status(404)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .body("Document not found: " + classpathLocation);
                    }
                    String content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    return ResponseEntity
                            .ok()
                            .header(HttpHeaders.CONTENT_TYPE, "text/markdown; charset=UTF-8")
                            .body(content);
                }
            }
            try (InputStream is = resource.getInputStream()) {
                String content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                return ResponseEntity
                        .ok()
                        .header(HttpHeaders.CONTENT_TYPE, "text/markdown; charset=UTF-8")
                        .body(content);
            }
        } catch (Exception ex) {
            return ResponseEntity
                    .status(500)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body("Failed to load document: " + classpathLocation);
        }
    }


    @GetMapping("/privacy-policy")
    @Operation(summary = "Get Privacy Policy (Markdown)")
    public ResponseEntity<String> getPrivacyPolicy() {
        return serveMarkdown("legal/privacy-policy.md");
    }

    @GetMapping("/terms-of-service")
    @Operation(summary = "Get Terms of Service (Markdown)")
    public ResponseEntity<String> getTermsOfService() {
        return serveMarkdown("legal/terms-and-conditions.md");
    }
}