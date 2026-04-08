package com.patenttracker.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileNumberParser {

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.+US)-(CON|DIV)(\\d+)$");

    private final String baseFileNumber;
    private final String relationshipType; // CON, DIV, or null
    private final Integer sequenceNumber;
    private final String parentFileNumber;

    private FileNumberParser(String baseFileNumber, String relationshipType,
                             Integer sequenceNumber, String parentFileNumber) {
        this.baseFileNumber = baseFileNumber;
        this.relationshipType = relationshipType;
        this.sequenceNumber = sequenceNumber;
        this.parentFileNumber = parentFileNumber;
    }

    public static FileNumberParser parse(String fileNumber) {
        if (fileNumber == null || fileNumber.isBlank()) {
            return new FileNumberParser(null, null, null, null);
        }

        String trimmed = fileNumber.trim();
        Matcher matcher = SUFFIX_PATTERN.matcher(trimmed);

        if (matcher.matches()) {
            String parent = matcher.group(1);
            String type = matcher.group(2);
            int seq = Integer.parseInt(matcher.group(3));
            return new FileNumberParser(trimmed, type, seq, parent);
        }

        return new FileNumberParser(trimmed, null, null, null);
    }

    public String getBaseFileNumber() { return baseFileNumber; }
    public String getRelationshipType() { return relationshipType; }
    public Integer getSequenceNumber() { return sequenceNumber; }
    public String getParentFileNumber() { return parentFileNumber; }

    public boolean isContinuation() { return "CON".equals(relationshipType); }
    public boolean isDivisional() { return "DIV".equals(relationshipType); }
    public boolean hasParent() { return parentFileNumber != null; }

    public String getSuffix() {
        if (relationshipType == null) {
            // Extract suffix from base — everything from last letter group
            if (baseFileNumber != null && baseFileNumber.contains("US")) {
                int idx = baseFileNumber.lastIndexOf("US");
                return baseFileNumber.substring(idx);
            }
            return "US";
        }
        return "US-" + relationshipType + sequenceNumber;
    }
}
