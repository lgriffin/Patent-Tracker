package com.patenttracker.service;

import com.patenttracker.dao.PatentDao;
import com.patenttracker.dao.TagDao;
import com.patenttracker.model.Patent;
import com.patenttracker.model.Tag;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

public class AutoTagService {

    private final PatentDao patentDao;
    private final TagDao tagDao;

    // Tag name -> list of keyword patterns (case-insensitive matching against title)
    private static final Map<String, List<Pattern>> TAG_RULES = new LinkedHashMap<>();

    static {
        addRule("Quantum Computing", "quantum", "qubit", "entangle", "superposition",
                "teleportation", "decoherence", "qecc", "qiz", "quantum isolation",
                "superdense", "\\bqkd\\b");
        addRule("Containers", "container", "unikernel", "sidecar", "cgroup",
                "containerized", "docker", "container image", "container layer",
                "container runtime", "container migration", "image layer",
                "\\bpod\\b", "\\bpvc\\b");
        addRule("Security", "access control", "authentication", "encrypt",
                "cryptographic", "eavesdropper", "zero.trust", "key management",
                "key invalidation", "secure transmission", "rbac", "role.based access",
                "code signing", "signing file", "\\brfid\\b", "vulnerabilit",
                "\\bsecret", "secure enclave", "policy decision", "access policies",
                "token generator", "seed.*token", "\\bpolicy\\b.*access",
                "homomorphic", "k.anonymous", "security algorithm",
                "isolation zone");
        addRule("Machine Learning", "machine.learn", "\\bml\\b", "neural network",
                "\\bllm\\b", "\\bmlm\\b", "large language model", "\\bai\\b",
                "artificial intelligence", "training", "inference",
                "foundational model", "unlearning", "tensor", "vector.based training",
                "prompt.*llm", "prompt.*model", "prompt.*analysis",
                "model layer", "model guardian", "data annotation",
                "predictive model", "interoperability.*model");
        addRule("Edge Computing", "\\bedge\\b", "\\biot\\b", "internet.of.things",
                "edge device", "edge node", "edge network", "nanotech");
        addRule("Mesh Networking", "\\bmesh\\b", "mesh network", "mesh device",
                "peer device", "v2x");
        addRule("DevOps & CI/CD", "\\bci\\b", "continuous integration",
                "continuous deployment", "pipeline", "\\bgit\\b", "build system",
                "packager", "\\brpm\\b", "version control", "deploying patch",
                "\\btesting\\b", "test instance", "test suite",
                "debugging", "specification file", "configuration file",
                "code snippet", "code coverage", "monitoring.*application",
                "update management", "delta.difference", "automation.*management",
                "error correction", "error management");
        addRule("Cloud & Migration", "cloud", "migration", "hybrid cloud",
                "serverless", "migrate", "on.prem", "\\bsaas\\b",
                "migrating persistent");
        addRule("Virtualization", "virtual machine", "\\bvm\\b", "hypervisor",
                "paravirtualization", "virtualization");
        addRule("Operating Systems", "\\bboot", "filesystem", "operating system",
                "\\bkernel\\b", "\\bos\\b", "initram", "ostree", "firmware");
        addRule("Functional Safety", "safety", "\\bfusa\\b", "compliance",
                "regulatory", "\\bsbom\\b", "certification",
                "non.compliant program");
        addRule("API & Services", "\\bapi\\b", "gateway", "microservice",
                "service discovery", "service orchestration", "circuit breaker",
                "service management", "service version", "service promotion");
        addRule("Data Management", "data provenance", "data service", "data fusion",
                "data anomaly", "data flow", "cross.domain data",
                "data collection", "data unit");
        addRule("Automotive", "vehicle", "automotive", "\\bv2x\\b", "camera video",
                "electronic control unit");
        addRule("Energy & Power", "energy.efficient", "energy consumption",
                "power management", "wireless charging", "energy profile",
                "minimum viable charge", "energy utilization");
        addRule("Package Management", "\\brpm\\b", "package", "dependency",
                "\\bsbom\\b", "software supply chain", "\\bcve\\b");
        addRule("Distributed Computing", "distributed", "\\bcluster\\b",
                "workload.*distribut", "workload.*node", "node.*propert",
                "resource reclamation", "resource usage", "resource allocation",
                "node management");
        addRule("Application Lifecycle", "application lifecycle",
                "application profile", "application priorit",
                "application update", "application execution",
                "runtime.*optim", "runtime environment",
                "workload processing", "stateful.*stateless",
                "function invocation", "interfaces.*optim",
                "redundant interface", "codebases");
        addRule("Networking", "network configur", "network optim",
                "media stream", "\\bsdn\\b", "self.organizing network",
                "adaptive network");
    }

    private static void addRule(String tagName, String... keywords) {
        List<Pattern> patterns = new ArrayList<>();
        for (String kw : keywords) {
            patterns.add(Pattern.compile(kw, Pattern.CASE_INSENSITIVE));
        }
        TAG_RULES.put(tagName, patterns);
    }

    public AutoTagService() {
        this.patentDao = new PatentDao();
        this.tagDao = new TagDao();
    }

    public AutoTagResult autoTagAll() throws SQLException {
        List<Patent> patents = patentDao.findAll();
        int tagged = 0;
        int totalTagsApplied = 0;

        for (Patent patent : patents) {
            List<String> suggestedTags = suggestTags(patent.getTitle());
            if (!suggestedTags.isEmpty()) {
                for (String tagName : suggestedTags) {
                    Tag tag = tagDao.findOrCreate(tagName);
                    tagDao.addToPatent(patent.getId(), tag.getId(), "AI");
                }
                tagged++;
                totalTagsApplied += suggestedTags.size();
            }
        }

        return new AutoTagResult(patents.size(), tagged, totalTagsApplied);
    }

    public List<String> suggestTags(String title) {
        if (title == null || title.isBlank()) return List.of();

        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, List<Pattern>> entry : TAG_RULES.entrySet()) {
            for (Pattern pattern : entry.getValue()) {
                if (pattern.matcher(title).find()) {
                    matched.add(entry.getKey());
                    break; // One match per tag is enough
                }
            }
        }
        return matched;
    }

    public record AutoTagResult(int totalPatents, int patentsTagged, int tagsApplied) {}
}
