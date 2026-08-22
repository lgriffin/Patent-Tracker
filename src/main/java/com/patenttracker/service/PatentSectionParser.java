package com.patenttracker.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatentSectionParser {

    private static final Pattern ABSTRACT_PATTERN = Pattern.compile(
            "(?i)^\\s*ABSTRACT\\s*(OF THE DISCLOSURE)?\\s*$", Pattern.MULTILINE);
    private static final Pattern CLAIMS_PATTERN = Pattern.compile(
            "(?i)^\\s*(CLAIMS?|What is claimed is:?)\\s*$", Pattern.MULTILINE);
    private static final Pattern INDIVIDUAL_CLAIM_PATTERN = Pattern.compile(
            "(?m)^\\s*(\\d+)\\.\\s+");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
            "(?i)^\\s*DESCRIPTION\\s*(OF (THE )?PREFERRED EMBODIMENTS?)?\\s*$", Pattern.MULTILINE);

    private static final Pattern[] SECTION_PATTERNS = {
        Pattern.compile("(?i)^\\s*FIELD OF (THE )?INVENTION\\s*$", Pattern.MULTILINE),
        Pattern.compile("(?i)^\\s*BACKGROUND( OF (THE )?INVENTION)?\\s*$", Pattern.MULTILINE),
        Pattern.compile("(?i)^\\s*SUMMARY( OF (THE )?INVENTION)?\\s*$", Pattern.MULTILINE),
        Pattern.compile("(?i)^\\s*BRIEF DESCRIPTION OF (THE )?DRAWINGS?\\s*$", Pattern.MULTILINE),
        Pattern.compile("(?i)^\\s*DETAILED DESCRIPTION( OF (THE )?(PREFERRED )?EMBODIMENTS?)?\\s*$", Pattern.MULTILINE),
        DESCRIPTION_PATTERN,
    };

    private static final String[] SECTION_NAMES = {
        "FIELD_OF_INVENTION", "BACKGROUND", "SUMMARY",
        "DRAWINGS_DESCRIPTION", "DETAILED_DESCRIPTION", "DESCRIPTION"
    };

    public record ParsedSection(String type, String text, int startPos, int endPos) {}

    public List<ParsedSection> parse(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }

        Map<Integer, ParsedSection> positionedSections = new LinkedHashMap<>();

        Matcher abstractMatcher = ABSTRACT_PATTERN.matcher(fullText);
        if (abstractMatcher.find()) {
            int start = abstractMatcher.end();
            int end = findNextSectionStart(fullText, start);
            String text = fullText.substring(start, end).trim();
            if (!text.isBlank()) {
                positionedSections.put(abstractMatcher.start(),
                    new ParsedSection("ABSTRACT", text, abstractMatcher.start(), end));
            }
        }

        for (int i = 0; i < SECTION_PATTERNS.length; i++) {
            Matcher m = SECTION_PATTERNS[i].matcher(fullText);
            if (m.find()) {
                int start = m.end();
                int end = findNextSectionStart(fullText, start);
                String text = fullText.substring(start, end).trim();
                if (!text.isBlank()) {
                    positionedSections.put(m.start(),
                        new ParsedSection(SECTION_NAMES[i], text, m.start(), end));
                }
            }
        }

        Matcher claimsMatcher = CLAIMS_PATTERN.matcher(fullText);
        if (claimsMatcher.find()) {
            int start = claimsMatcher.end();
            String claimsText = fullText.substring(start).trim();
            if (!claimsText.isBlank()) {
                positionedSections.put(claimsMatcher.start(),
                    new ParsedSection("CLAIMS", claimsText, claimsMatcher.start(), fullText.length()));
            }
        }

        List<ParsedSection> result = new ArrayList<>(positionedSections.values());

        if (result.isEmpty()) {
            result.add(new ParsedSection("FULL_TEXT", fullText.trim(), 0, fullText.length()));
        }

        return result;
    }

    public List<ParsedSection> extractIndividualClaims(String claimsText) {
        List<ParsedSection> claims = new ArrayList<>();
        Matcher m = INDIVIDUAL_CLAIM_PATTERN.matcher(claimsText);

        List<int[]> claimPositions = new ArrayList<>();
        while (m.find()) {
            claimPositions.add(new int[]{m.start(), Integer.parseInt(m.group(1))});
        }

        for (int i = 0; i < claimPositions.size(); i++) {
            int start = claimPositions.get(i)[0];
            int claimNum = claimPositions.get(i)[1];
            int end = (i + 1 < claimPositions.size()) ? claimPositions.get(i + 1)[0] : claimsText.length();
            String claimText = claimsText.substring(start, end).trim();
            if (!claimText.isBlank()) {
                claims.add(new ParsedSection("CLAIM_" + claimNum, claimText, start, end));
            }
        }

        return claims;
    }

    private int findNextSectionStart(String text, int fromPos) {
        int earliest = text.length();

        Matcher claimsMatcher = CLAIMS_PATTERN.matcher(text);
        if (claimsMatcher.find(fromPos) && claimsMatcher.start() < earliest) {
            earliest = claimsMatcher.start();
        }

        for (Pattern p : SECTION_PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find(fromPos) && m.start() < earliest) {
                earliest = m.start();
            }
        }

        return earliest;
    }
}
