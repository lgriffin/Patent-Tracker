package com.patenttracker.service;

import com.patenttracker.model.DocumentChunk;

import java.util.ArrayList;
import java.util.List;

public class PatentChunkingService {

    private static final int DEFAULT_MAX_CHUNK_WORDS = 500;
    private static final int DEFAULT_OVERLAP_WORDS = 50;

    private final int maxChunkWords;
    private final int overlapWords;

    public PatentChunkingService() {
        this(DEFAULT_MAX_CHUNK_WORDS, DEFAULT_OVERLAP_WORDS);
    }

    public PatentChunkingService(int maxChunkWords, int overlapWords) {
        this.maxChunkWords = maxChunkWords;
        this.overlapWords = overlapWords;
    }

    public List<DocumentChunk> chunkDocument(int documentId, String patentNumber, String fullText) {
        PatentSectionParser parser = new PatentSectionParser();
        List<PatentSectionParser.ParsedSection> sections = parser.parse(fullText);
        List<DocumentChunk> allChunks = new ArrayList<>();
        int globalIndex = 0;

        for (PatentSectionParser.ParsedSection section : sections) {
            if ("CLAIMS".equals(section.type())) {
                List<PatentSectionParser.ParsedSection> claims = parser.extractIndividualClaims(section.text());
                if (!claims.isEmpty()) {
                    for (PatentSectionParser.ParsedSection claim : claims) {
                        allChunks.add(DocumentChunk.builder()
                                .documentId(documentId)
                                .patentNumber(patentNumber)
                                .sectionType("CLAIM_INDIVIDUAL")
                                .chunkIndex(globalIndex++)
                                .chunkText(claim.text())
                                .wordCount(countWords(claim.text()))
                                .startPosition(section.startPos() + claim.startPos())
                                .endPosition(section.startPos() + claim.endPos())
                                .build());
                    }
                } else {
                    List<DocumentChunk> claimChunks = splitIntoChunks(
                            documentId, patentNumber, "CLAIMS", section.text(),
                            section.startPos(), globalIndex);
                    globalIndex += claimChunks.size();
                    allChunks.addAll(claimChunks);
                }
            } else {
                List<DocumentChunk> sectionChunks = splitIntoChunks(
                        documentId, patentNumber, section.type(), section.text(),
                        section.startPos(), globalIndex);
                globalIndex += sectionChunks.size();
                allChunks.addAll(sectionChunks);
            }
        }

        return allChunks;
    }

    private List<DocumentChunk> splitIntoChunks(int documentId, String patentNumber,
            String sectionType, String text, int basePosition, int startIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");

        if (words.length <= maxChunkWords) {
            chunks.add(DocumentChunk.builder()
                    .documentId(documentId)
                    .patentNumber(patentNumber)
                    .sectionType(sectionType)
                    .chunkIndex(startIndex)
                    .chunkText(text)
                    .wordCount(words.length)
                    .startPosition(basePosition)
                    .endPosition(basePosition + text.length())
                    .build());
            return chunks;
        }

        int pos = 0;
        int chunkIdx = startIndex;

        while (pos < words.length) {
            int end = Math.min(pos + maxChunkWords, words.length);

            if (end < words.length) {
                int bestBreak = findParagraphBreak(words, pos, end);
                if (bestBreak > pos) {
                    end = bestBreak;
                }
            }

            StringBuilder chunkText = new StringBuilder();
            for (int i = pos; i < end; i++) {
                if (i > pos) chunkText.append(" ");
                chunkText.append(words[i]);
            }

            String chunk = chunkText.toString();
            int charStart = basePosition + findWordPosition(text, words, pos);
            int charEnd = basePosition + findWordPosition(text, words, end - 1) + words[end - 1].length();

            chunks.add(DocumentChunk.builder()
                    .documentId(documentId)
                    .patentNumber(patentNumber)
                    .sectionType(sectionType)
                    .chunkIndex(chunkIdx++)
                    .chunkText(chunk)
                    .wordCount(end - pos)
                    .startPosition(charStart)
                    .endPosition(charEnd)
                    .build());

            pos = Math.max(pos + 1, end - overlapWords);
        }

        return chunks;
    }

    private int findParagraphBreak(String[] words, int start, int end) {
        for (int i = end - 1; i > start + (maxChunkWords / 2); i--) {
            if (words[i].endsWith(".") || words[i].endsWith(".\n") || words[i].endsWith(".\r\n")) {
                return i + 1;
            }
        }
        return end;
    }

    private int findWordPosition(String text, String[] words, int wordIndex) {
        int pos = 0;
        for (int i = 0; i < wordIndex && i < words.length; i++) {
            int idx = text.indexOf(words[i], pos);
            if (idx >= 0) {
                pos = idx + words[i].length();
            }
        }
        if (wordIndex < words.length) {
            int idx = text.indexOf(words[wordIndex], pos);
            if (idx >= 0) return idx;
        }
        return pos;
    }

    static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.split("\\s+").length;
    }
}
