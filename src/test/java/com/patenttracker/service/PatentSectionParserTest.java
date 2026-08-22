package com.patenttracker.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PatentSectionParserTest {

    private final PatentSectionParser parser = new PatentSectionParser();

    @Test
    void parsesAbstract() {
        String text = """
                Some header text

                ABSTRACT

                A method for improving container orchestration through
                machine learning based resource allocation.

                CLAIMS

                1. A method comprising steps.
                """;

        List<PatentSectionParser.ParsedSection> sections = parser.parse(text);
        assertFalse(sections.isEmpty());

        PatentSectionParser.ParsedSection abstractSection = sections.stream()
                .filter(s -> "ABSTRACT".equals(s.type()))
                .findFirst()
                .orElse(null);

        assertNotNull(abstractSection);
        assertTrue(abstractSection.text().contains("container orchestration"));
    }

    @Test
    void parsesClaims() {
        String text = """
                ABSTRACT

                A test abstract.

                CLAIMS

                1. A method for processing data.
                2. The method of claim 1, further comprising a step.
                3. A system for implementing the method of claim 1.
                """;

        List<PatentSectionParser.ParsedSection> sections = parser.parse(text);

        PatentSectionParser.ParsedSection claims = sections.stream()
                .filter(s -> "CLAIMS".equals(s.type()))
                .findFirst()
                .orElse(null);

        assertNotNull(claims);
        assertTrue(claims.text().contains("method for processing"));
    }

    @Test
    void extractsIndividualClaims() {
        String claimsText = """
                1. A method comprising:
                   receiving input data;
                   processing the input data.

                2. The method of claim 1, further comprising:
                   transmitting the processed data.

                3. A system for implementing the method of claim 1.
                """;

        List<PatentSectionParser.ParsedSection> claims = parser.extractIndividualClaims(claimsText);
        assertEquals(3, claims.size());
        assertEquals("CLAIM_1", claims.get(0).type());
        assertEquals("CLAIM_2", claims.get(1).type());
        assertEquals("CLAIM_3", claims.get(2).type());
    }

    @Test
    void parsesDetailedDescription() {
        String text = """
                ABSTRACT

                A test abstract.

                DETAILED DESCRIPTION OF THE PREFERRED EMBODIMENTS

                The present invention relates to a system for managing
                virtual machines in a distributed computing environment.

                CLAIMS

                1. A method for managing VMs.
                """;

        List<PatentSectionParser.ParsedSection> sections = parser.parse(text);

        PatentSectionParser.ParsedSection desc = sections.stream()
                .filter(s -> "DETAILED_DESCRIPTION".equals(s.type()))
                .findFirst()
                .orElse(null);

        assertNotNull(desc);
        assertTrue(desc.text().contains("virtual machines"));
    }

    @Test
    void fallsBackToFullTextWhenNoSections() {
        String text = "This is a patent with no standard section headers. " +
                "It just has plain text describing the invention.";

        List<PatentSectionParser.ParsedSection> sections = parser.parse(text);
        assertEquals(1, sections.size());
        assertEquals("FULL_TEXT", sections.get(0).type());
    }

    @Test
    void parsesMultipleSections() {
        String text = """
                ABSTRACT

                Test abstract.

                FIELD OF THE INVENTION

                The present invention relates to quantum computing.

                BACKGROUND OF THE INVENTION

                Prior quantum systems have limitations.

                SUMMARY OF THE INVENTION

                We propose a novel quantum error correction approach.

                CLAIMS

                1. A quantum error correction method.
                """;

        List<PatentSectionParser.ParsedSection> sections = parser.parse(text);
        assertTrue(sections.size() >= 4);

        List<String> types = sections.stream().map(PatentSectionParser.ParsedSection::type).toList();
        assertTrue(types.contains("ABSTRACT"));
        assertTrue(types.contains("FIELD_OF_INVENTION"));
        assertTrue(types.contains("BACKGROUND"));
        assertTrue(types.contains("SUMMARY"));
        assertTrue(types.contains("CLAIMS"));
    }
}
