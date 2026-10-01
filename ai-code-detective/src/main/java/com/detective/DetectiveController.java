package com.detective;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DetectiveController {

    private final RagService ragService;

    public DetectiveController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * Analyze a GitHub repository: clone, chunk, embed, store, build graph.
     * Example: POST http://localhost:8080/api/analyze?repoUrl=https://github.com/user/repo
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestParam String repoUrl) {
        Map<String, Object> response = new HashMap<>();
        try {
            long start = System.currentTimeMillis();
            int chunkCount = ragService.analyzeRepository(repoUrl);
            long duration = System.currentTimeMillis() - start;

            DependencyGraph graph = ragService.getCurrentGraph();

            response.put("status", "success");
            response.put("repoUrl", repoUrl);
            response.put("chunksIndexed", chunkCount);
            response.put("durationMs", duration);
            if (graph != null) {
                response.put("graphNodes", graph.getNodes().size());
                response.put("graphEdges", graph.getEdges().size());
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Ask a question about the analyzed repo.
     * Example: GET http://localhost:8080/api/chat?question=Where is the main method?
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestParam String question) {
        Map<String, Object> response = new HashMap<>();
        try {
            String answer = ragService.ask(question);
            response.put("status", "success");
            response.put("question", question);
            response.put("answer", answer);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get the dependency graph for the most recently analyzed repo.
     * Example: GET http://localhost:8080/api/graph
     */
    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> graph() {
        Map<String, Object> response = new HashMap<>();
        DependencyGraph graph = ragService.getCurrentGraph();

        if (graph == null || graph.isEmpty()) {
            response.put("status", "error");
            response.put("message", "No repository analyzed yet. Please call /api/analyze first.");
            return ResponseEntity.status(404).body(response);
        }

        response.put("status", "success");
        response.put("graph", graph.toMap());
        return ResponseEntity.ok(response);
    }
}