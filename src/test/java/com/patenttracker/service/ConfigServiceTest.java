package com.patenttracker.service;

import org.junit.jupiter.api.*;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {

    @Test
    void defaultValues() {
        // Use empty Properties to trigger default values
        Properties props = new Properties();
        ConfigService config = new ConfigService(props);

        assertEquals("Leigh Griffin", config.getOwnerName());
        assertEquals("", config.getApiKey());
        assertEquals("claude", config.getClaudeCliPath());
        assertEquals(600, config.getAnalysisTimeout());
        assertEquals(1100, config.getRateLimitDelay());
        assertEquals(120, config.getIdleTimeout());
        assertEquals(30, config.getBatchSize());
    }

    @Test
    void customProperties() {
        Properties props = new Properties();
        props.setProperty("owner.name", "Test User");
        props.setProperty("uspto.api.key", "my-api-key");
        props.setProperty("claude.cli.path", "/usr/local/bin/claude");
        props.setProperty("claude.analysis.timeout", "300");
        props.setProperty("uspto.rate.delay", "2000");
        props.setProperty("claude.idle.timeout", "60");
        props.setProperty("claude.batch.size", "15");

        ConfigService config = new ConfigService(props);

        assertEquals("Test User", config.getOwnerName());
        assertEquals("my-api-key", config.getApiKey());
        assertEquals("/usr/local/bin/claude", config.getClaudeCliPath());
        assertEquals(300, config.getAnalysisTimeout());
        assertEquals(2000, config.getRateLimitDelay());
        assertEquals(60, config.getIdleTimeout());
        assertEquals(15, config.getBatchSize());
    }

    @Test
    void intParsing_invalid() {
        Properties props = new Properties();
        props.setProperty("claude.analysis.timeout", "not-a-number");
        props.setProperty("uspto.rate.delay", "abc");
        props.setProperty("claude.idle.timeout", "");
        props.setProperty("claude.batch.size", "xyz");

        ConfigService config = new ConfigService(props);

        // Should fall back to defaults on parse errors
        assertEquals(600, config.getAnalysisTimeout());
        assertEquals(1100, config.getRateLimitDelay());
        assertEquals(120, config.getIdleTimeout());
        assertEquals(30, config.getBatchSize());
    }
}
