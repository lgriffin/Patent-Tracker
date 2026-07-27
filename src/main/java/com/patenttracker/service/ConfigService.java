package com.patenttracker.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigService {

    private static final ConfigService INSTANCE = new ConfigService();
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.patenttracker";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.properties";
    private static final long CACHE_TTL_MS = 5000;

    private volatile Properties cachedProps;
    private volatile long lastLoadTime;

    private ConfigService() {}

    ConfigService(Properties overrides) {
        this.cachedProps = overrides;
        this.lastLoadTime = Long.MAX_VALUE;
    }

    public static ConfigService getInstance() {
        return INSTANCE;
    }

    public void reload() {
        cachedProps = loadFromDisk();
        lastLoadTime = System.currentTimeMillis();
    }

    private Properties getProps() {
        long now = System.currentTimeMillis();
        if (cachedProps == null || (now - lastLoadTime) > CACHE_TTL_MS) {
            cachedProps = loadFromDisk();
            lastLoadTime = now;
        }
        return cachedProps;
    }

    private static Properties loadFromDisk() {
        Properties props = new Properties();
        Path path = Path.of(CONFIG_FILE);
        if (Files.exists(path)) {
            try (InputStream is = new FileInputStream(path.toFile())) {
                props.load(is);
            } catch (IOException e) {
                // Use defaults
            }
        }
        return props;
    }

    public String getOwnerName() {
        return getProps().getProperty("owner.name", "Leigh Griffin");
    }

    public String getApiKey() {
        return getProps().getProperty("uspto.api.key", "");
    }

    public String getClaudeCliPath() {
        return getProps().getProperty("claude.cli.path", "claude");
    }

    public int getAnalysisTimeout() {
        return getIntProperty("claude.analysis.timeout", 600);
    }

    public int getRateLimitDelay() {
        return getIntProperty("uspto.rate.delay", 1100);
    }

    public int getIdleTimeout() {
        return getIntProperty("claude.idle.timeout", 120);
    }

    public int getBatchSize() {
        return getIntProperty("claude.batch.size", 30);
    }

    private int getIntProperty(String key, int defaultValue) {
        String val = getProps().getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
