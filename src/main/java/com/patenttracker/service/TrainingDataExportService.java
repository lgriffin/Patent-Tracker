package com.patenttracker.service;

import com.patenttracker.dao.CorpusDocumentDao;
import com.patenttracker.dao.DocumentChunkDao;
import com.patenttracker.model.CorpusDocument;
import com.patenttracker.model.DocumentChunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TrainingDataExportService {

    private static final String EXPORT_DIR = System.getProperty("user.home") + "/.patenttracker/exports";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final CorpusDocumentDao documentDao;
    private final DocumentChunkDao chunkDao;

    public TrainingDataExportService() {
        this.documentDao = new CorpusDocumentDao();
        this.chunkDao = new DocumentChunkDao();
    }

    public TrainingDataExportService(CorpusDocumentDao documentDao, DocumentChunkDao chunkDao) {
        this.documentDao = documentDao;
        this.chunkDao = chunkDao;
    }

    public ExportResult exportJsonl(String exportName, ExportConfig config) throws IOException, SQLException {
        Files.createDirectories(Path.of(EXPORT_DIR));
        String filename = exportName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jsonl";
        Path outputPath = Path.of(EXPORT_DIR, filename);
        int lineCount = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            int offset = 0;
            int batchSize = 500;

            while (true) {
                List<DocumentChunk> chunks = chunkDao.findAll(batchSize, offset);
                if (chunks.isEmpty()) break;

                for (DocumentChunk chunk : chunks) {
                    if (config.sectionFilter != null && !config.sectionFilter.isBlank()
                            && !chunk.getSectionType().equals(config.sectionFilter)) {
                        continue;
                    }
                    if (config.domainFilter != null && !config.domainFilter.isBlank()) {
                        CorpusDocument doc = documentDao.findById(chunk.getDocumentId());
                        if (doc == null || !config.domainFilter.equals(doc.getDomain())) continue;
                    }
                    if (config.minWordCount > 0 && chunk.getWordCount() < config.minWordCount) continue;

                    ObjectNode node = mapper.createObjectNode();
                    node.put("patent_number", chunk.getPatentNumber());
                    node.put("section_type", chunk.getSectionType());
                    node.put("chunk_index", chunk.getChunkIndex());
                    node.put("text", chunk.getChunkText());
                    node.put("word_count", chunk.getWordCount());

                    if (config.includeMetadata) {
                        CorpusDocument doc = documentDao.findById(chunk.getDocumentId());
                        if (doc != null) {
                            node.put("title", doc.getTitle());
                            node.put("assignee", doc.getAssignee());
                            node.put("cpc_codes", doc.getCpcCodes());
                            node.put("domain", doc.getDomain());
                        }
                    }

                    writer.write(mapper.writeValueAsString(node));
                    writer.newLine();
                    lineCount++;
                }

                offset += batchSize;
            }
        }

        long fileSize = Files.size(outputPath);
        return new ExportResult(true, outputPath.toString(), "JSONL", lineCount, fileSize, null);
    }

    public ExportResult exportCsv(String exportName, ExportConfig config) throws IOException, SQLException {
        Files.createDirectories(Path.of(EXPORT_DIR));
        String filename = exportName.replaceAll("[^a-zA-Z0-9_-]", "_") + ".csv";
        Path outputPath = Path.of(EXPORT_DIR, filename);
        int lineCount = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("patent_number,section_type,chunk_index,word_count,text");
            if (config.includeMetadata) {
                writer.write(",title,assignee,cpc_codes,domain");
            }
            writer.newLine();

            int offset = 0;
            int batchSize = 500;

            while (true) {
                List<DocumentChunk> chunks = chunkDao.findAll(batchSize, offset);
                if (chunks.isEmpty()) break;

                for (DocumentChunk chunk : chunks) {
                    if (config.sectionFilter != null && !config.sectionFilter.isBlank()
                            && !chunk.getSectionType().equals(config.sectionFilter)) {
                        continue;
                    }
                    if (config.domainFilter != null && !config.domainFilter.isBlank()) {
                        CorpusDocument doc = documentDao.findById(chunk.getDocumentId());
                        if (doc == null || !config.domainFilter.equals(doc.getDomain())) continue;
                    }
                    if (config.minWordCount > 0 && chunk.getWordCount() < config.minWordCount) continue;

                    writer.write(escapeCsv(chunk.getPatentNumber()));
                    writer.write(",");
                    writer.write(escapeCsv(chunk.getSectionType()));
                    writer.write(",");
                    writer.write(String.valueOf(chunk.getChunkIndex()));
                    writer.write(",");
                    writer.write(String.valueOf(chunk.getWordCount()));
                    writer.write(",");
                    writer.write(escapeCsv(chunk.getChunkText()));

                    if (config.includeMetadata) {
                        CorpusDocument doc = documentDao.findById(chunk.getDocumentId());
                        if (doc != null) {
                            writer.write(",");
                            writer.write(escapeCsv(doc.getTitle()));
                            writer.write(",");
                            writer.write(escapeCsv(doc.getAssignee()));
                            writer.write(",");
                            writer.write(escapeCsv(doc.getCpcCodes()));
                            writer.write(",");
                            writer.write(escapeCsv(doc.getDomain()));
                        } else {
                            writer.write(",,,,");
                        }
                    }

                    writer.newLine();
                    lineCount++;
                }

                offset += batchSize;
            }
        }

        long fileSize = Files.size(outputPath);
        return new ExportResult(true, outputPath.toString(), "CSV", lineCount, fileSize, null);
    }

    public ExportResult exportInstructionPairs(String exportName, ExportConfig config) throws IOException, SQLException {
        Files.createDirectories(Path.of(EXPORT_DIR));
        String filename = exportName.replaceAll("[^a-zA-Z0-9_-]", "_") + "_pairs.jsonl";
        Path outputPath = Path.of(EXPORT_DIR, filename);
        int lineCount = 0;

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            int offset = 0;
            int batchSize = 500;

            while (true) {
                List<DocumentChunk> chunks = chunkDao.findAll(batchSize, offset);
                if (chunks.isEmpty()) break;

                for (DocumentChunk chunk : chunks) {
                    if (config.sectionFilter != null && !config.sectionFilter.isBlank()
                            && !chunk.getSectionType().equals(config.sectionFilter)) {
                        continue;
                    }
                    if (config.minWordCount > 0 && chunk.getWordCount() < config.minWordCount) continue;

                    List<ObjectNode> pairs = generateInstructionPairs(chunk);
                    for (ObjectNode pair : pairs) {
                        writer.write(mapper.writeValueAsString(pair));
                        writer.newLine();
                        lineCount++;
                    }
                }

                offset += batchSize;
            }
        }

        long fileSize = Files.size(outputPath);
        return new ExportResult(true, outputPath.toString(), "INSTRUCTION_PAIRS", lineCount, fileSize, null);
    }

    private List<ObjectNode> generateInstructionPairs(DocumentChunk chunk) {
        List<ObjectNode> pairs = new ArrayList<>();

        if (chunk.getSectionType().startsWith("CLAIM")) {
            ObjectNode claimPair = mapper.createObjectNode();
            claimPair.put("instruction",
                    "What does patent " + chunk.getPatentNumber() + " claim?");
            claimPair.put("input", "");
            claimPair.put("output", chunk.getChunkText());
            claimPair.put("patent_number", chunk.getPatentNumber());
            claimPair.put("type", "claim_extraction");
            pairs.add(claimPair);
        }

        if ("ABSTRACT".equals(chunk.getSectionType())) {
            ObjectNode summaryPair = mapper.createObjectNode();
            summaryPair.put("instruction",
                    "Summarize the invention described in patent " + chunk.getPatentNumber() + ".");
            summaryPair.put("input", "");
            summaryPair.put("output", chunk.getChunkText());
            summaryPair.put("patent_number", chunk.getPatentNumber());
            summaryPair.put("type", "patent_summary");
            pairs.add(summaryPair);
        }

        ObjectNode sectionPair = mapper.createObjectNode();
        sectionPair.put("instruction",
                "Describe the " + chunk.getSectionType().toLowerCase().replace("_", " ")
                        + " section of patent " + chunk.getPatentNumber() + ".");
        sectionPair.put("input", "");
        sectionPair.put("output", chunk.getChunkText());
        sectionPair.put("patent_number", chunk.getPatentNumber());
        sectionPair.put("type", "section_content");
        pairs.add(sectionPair);

        return pairs;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    public record ExportResult(boolean success, String filePath, String format,
                                int recordCount, long fileSizeBytes, String error) {}

    public record ExportConfig(String domainFilter, String sectionFilter,
                                int minWordCount, boolean includeMetadata) {
        public ExportConfig() {
            this(null, null, 0, true);
        }
    }
}
