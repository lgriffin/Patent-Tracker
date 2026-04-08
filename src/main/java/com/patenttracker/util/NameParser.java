package com.patenttracker.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameParser {

    private static final Pattern NAME_WITH_USERNAME = Pattern.compile("^(.+?)\\s*\\(([^)]+)\\)$");

    private final String fullName;
    private final String username;

    private NameParser(String fullName, String username) {
        this.fullName = fullName;
        this.username = username;
    }

    public static NameParser parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new NameParser(null, null);
        }

        String trimmed = raw.trim();
        Matcher matcher = NAME_WITH_USERNAME.matcher(trimmed);

        if (matcher.matches()) {
            String name = matcher.group(1).trim();
            String user = matcher.group(2).trim();
            return new NameParser(name, user);
        }

        // No parenthetical username — could be just a username or a name without username
        // If it contains a space, treat as a full name; otherwise treat as username
        if (trimmed.contains(" ")) {
            return new NameParser(trimmed, null);
        } else {
            return new NameParser(trimmed, trimmed);
        }
    }

    public String getFullName() { return fullName; }
    public String getUsername() { return username; }

    public boolean isEmpty() { return fullName == null && username == null; }
}
