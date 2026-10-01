package com.detective;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class BugInvestigationService {

    private final RagService ragService;
    private final OllamaService ollamaService;
    private final CallChainService callChainService;

    // Reduced from 6000 to 2500 — llama3.2 processes less context = much faster
    private static final int MAX_CONTEXT_CHARS = 2500;
    private static final int MAX_CHUNK_CHARS = 700;

    public BugInvestigationService(RagService ragService,
                                   OllamaService ollamaService,
                                   CallChainService callChainService) {
        this.ragService = ragService;
        this.ollamaService = ollamaService;
        this.callChainService = callChainService;
    }

    public InvestigationReport investigate(String bugDescription) {
        long start = System.currentTimeMillis();
        InvestigationReport report = new InvestigationReport();
        report.bugDescription = bugDescription;

        try {
            System.out.println("[Detective] Phase 1: Retrieving relevant code...");
            // Reduced from 6 to 4 chunks — less to process
            List<CodeChunk> relevantChunks = ragService.retrieveForQuestion(bugDescription, 4);

            if (relevantChunks.isEmpty()) {
                report.error = "No repository analyzed yet. Please analyze a repo first.";
                report.durationMs = System.currentTimeMillis() - start;
                return report;
            }

            Set<String> filePaths = new LinkedHashSet<>();
            for (CodeChunk c : relevantChunks) filePaths.add(c.getFilePath());
            report.relevantFiles = new ArrayList<>(filePaths);

            for (CodeChunk c : relevantChunks) {
                String snippet = c.getContent().replaceAll("\\s+", " ").trim();
                if (snippet.length() > 120) snippet = snippet.substring(0, 120) + "...";
                report.relevantChunkSummaries.add(c.getFilePath() + "  ->  " + snippet);
            }

            System.out.println("[Detective] Found " + filePaths.size() + " relevant files");

            System.out.println("[Detective] Phase 2: Tracing call chain...");
            DependencyGraph graph = ragService.getCurrentGraph();
            if (graph != null) {
                Set<String> seeds = callChainService.findSeedNodes(graph, new ArrayList<>(filePaths));
                report.callChain = callChainService.buildCallChain(graph, seeds);
            }

            String codeContext = buildCodeContext(relevantChunks);
            System.out.println("[Detective] Context size: " + codeContext.length() + " chars");

            long t0 = System.currentTimeMillis();

            // BATCH 1: root cause + reproduction
            System.out.println("[Detective] Batch 1: root cause + reproduction...");
            CompletableFuture<String> rootCauseFuture = CompletableFuture.supplyAsync(() ->
                ollamaService.chat(buildRootCausePrompt(bugDescription, codeContext, report.callChain))
            );
            CompletableFuture<String> reproFuture = CompletableFuture.supplyAsync(() ->
                ollamaService.chat(buildReproPrompt(bugDescription, codeContext))
            );

            CompletableFuture.allOf(rootCauseFuture, reproFuture).join();
            System.out.println("[Detective] Batch 1 done (" + (System.currentTimeMillis() - t0) / 1000 + "s)");

            // BATCH 2: fix + impact
            System.out.println("[Detective] Batch 2: fix + impact...");
            CompletableFuture<String> fixFuture = CompletableFuture.supplyAsync(() ->
                ollamaService.chat(buildFixPrompt(bugDescription, codeContext))
            );
            CompletableFuture<String> impactFuture = CompletableFuture.supplyAsync(() ->
                ollamaService.chat(buildImpactPrompt(report.callChain, filePaths))
            );

            CompletableFuture.allOf(fixFuture, impactFuture).join();
            System.out.println("[Detective] Batch 2 done (" + (System.currentTimeMillis() - t0) / 1000 + "s)");

            parseRootCause(rootCauseFuture.join(), report);
            report.reproductionSteps = parseNumberedList(reproFuture.join());
            parseFix(fixFuture.join(), report, filePaths);
            report.impactedComponents = parseNumberedList(impactFuture.join());

            System.out.println("[Detective] Investigation complete.");
            report.durationMs = System.currentTimeMillis() - start;
            return report;

        } catch (Exception e) {
            report.error = "Investigation failed: " + e.getMessage();
            report.durationMs = System.currentTimeMillis() - start;
            return report;
        }
    }

    private String buildCodeContext(List<CodeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (CodeChunk c : chunks) {
            String content = c.getContent();
            // Trim each chunk to MAX_CHUNK_CHARS
            if (content.length() > MAX_CHUNK_CHARS) {
                content = content.substring(0, MAX_CHUNK_CHARS) + "\n... [truncated]";
            }
            String block = "=== FILE: " + c.getFilePath() + " ===\n" + content + "\n\n";
            if (total + block.length() > MAX_CONTEXT_CHARS) break;
            sb.append(block);
            total += block.length();
        }
        return sb.toString();
    }

    private String buildRootCausePrompt(String bug, String code, List<String> chain) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior software engineer investigating a bug.\n\n");
        sb.append("BUG REPORT:\n").append(bug).append("\n\n");
        sb.append("RELEVANT CODE:\n").append(code).append("\n");
        if (!chain.isEmpty()) {
            sb.append("CALL CHAIN:\n");
            for (String line : chain) sb.append(line).append("\n");
            sb.append("\n");
        }
        sb.append("TASK: Identify the most probable ROOT CAUSE. Be concise (max 4 sentences).\n");
        sb.append("Answer in this EXACT format:\n");
        sb.append("ROOT CAUSE: <one or two sentences>\n");
        sb.append("CONFIDENCE: <low|medium|high>\n");
        sb.append("EVIDENCE: <2-3 short bullet points>\n");
        return sb.toString();
    }

    private String buildReproPrompt(String bug, String code) {
        return "You are a QA engineer. Write REPRODUCTION STEPS for this bug.\n\n"
             + "BUG:\n" + bug + "\n\n"
             + "RELEVANT CODE:\n" + code + "\n\n"
             + "TASK: List 4-6 numbered steps to reproduce the bug.\n"
             + "Output ONLY the numbered list. No preamble.\n";
    }

    private String buildFixPrompt(String bug, String code) {
        return "You are a senior engineer. Propose a MINIMAL fix for this bug.\n\n"
             + "BUG:\n" + bug + "\n\n"
             + "RELEVANT CODE:\n" + code + "\n\n"
             + "TASK: Propose the smallest possible code change that fixes the bug. Be specific.\n"
             + "Answer in this EXACT format:\n"
             + "FILE: <path of file to change>\n"
             + "FIX:\n<3-8 lines of the corrected code>\n"
             + "EXPLANATION: <one sentence>\n";
    }

    private String buildImpactPrompt(List<String> chain, Set<String> filePaths) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze the impact of changing code related to a bug fix.\n\n");
        sb.append("FILES INVOLVED:\n");
        for (String f : filePaths) sb.append("- ").append(f).append("\n");
        if (!chain.isEmpty()) {
            sb.append("\nCALL CHAIN:\n");
            for (String line : chain) sb.append(line).append("\n");
        }
        sb.append("\nTASK: List 3-5 components (classes or modules) that MIGHT be affected.\n");
        sb.append("Only reference classes that ACTUALLY APPEAR in the call chain or files above.\n");
        sb.append("If unsure, list fewer. Do NOT invent class names.\n");
        sb.append("Output ONLY a numbered list.\n");
        return sb.toString();
    }

    private void parseRootCause(String raw, InvestigationReport report) {
        report.probableRootCause = raw.trim();
        report.confidence = "medium";
        report.evidence = "";

        String[] lines = raw.split("\n");
        StringBuilder cause = new StringBuilder();
        StringBuilder evidence = new StringBuilder();
        String section = "cause";

        for (String line : lines) {
            String upper = line.toUpperCase().trim();
            if (upper.startsWith("ROOT CAUSE:")) {
                cause.append(line.substring(line.indexOf(":") + 1).trim());
                section = "cause";
            } else if (upper.startsWith("CONFIDENCE:")) {
                String val = line.substring(line.indexOf(":") + 1).trim().toLowerCase();
                if (val.contains("high")) report.confidence = "high";
                else if (val.contains("low")) report.confidence = "low";
                else report.confidence = "medium";
            } else if (upper.startsWith("EVIDENCE:")) {
                evidence.append(line.substring(line.indexOf(":") + 1).trim());
                section = "evidence";
            } else if (!upper.isEmpty()) {
                if (section.equals("cause")) cause.append(" ").append(line.trim());
                else evidence.append("\n").append(line.trim());
            }
        }

        if (cause.length() > 0) report.probableRootCause = cause.toString().trim();
        if (evidence.length() > 0) report.evidence = evidence.toString().trim();
    }

    private List<String> parseNumberedList(String raw) {
        List<String> out = new ArrayList<>();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.matches("^\\d+[\\.\\)]\\s+.*") || t.startsWith("- ")) {
                String clean = t.replaceFirst("^\\d+[\\.\\)]\\s+", "").replaceFirst("^-\\s+", "").trim();
                if (!clean.isEmpty()) out.add(clean);
            }
        }
        if (out.isEmpty() && !raw.isBlank()) out.add(raw.trim());
        return out;
    }

    private void parseFix(String raw, InvestigationReport report, Set<String> knownFiles) {
        report.suggestedFix = raw.trim();
        report.suggestedFixFile = "";

        String[] lines = raw.split("\n");
        StringBuilder fix = new StringBuilder();
        boolean inFix = false;

        for (String line : lines) {
            String upper = line.toUpperCase().trim();
            if (upper.startsWith("FILE:")) {
                report.suggestedFixFile = line.substring(line.indexOf(":") + 1).trim();
            } else if (upper.startsWith("FIX:")) {
                inFix = true;
                fix.append(line.substring(line.indexOf(":") + 1).trim()).append("\n");
            } else if (upper.startsWith("EXPLANATION:")) {
                inFix = false;
                fix.append("\n").append(line.substring(line.indexOf(":") + 1).trim());
            } else if (inFix) {
                fix.append(line).append("\n");
            }
        }

        if (fix.length() > 0) report.suggestedFix = fix.toString().trim();
        if (report.suggestedFixFile.isEmpty() && !knownFiles.isEmpty()) {
            report.suggestedFixFile = knownFiles.iterator().next();
        }
    }
}