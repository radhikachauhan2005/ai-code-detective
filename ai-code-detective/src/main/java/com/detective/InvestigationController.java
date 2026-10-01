package com.detective;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InvestigationController {

    private final BugInvestigationService bugInvestigationService;

    public InvestigationController(BugInvestigationService bugInvestigationService) {
        this.bugInvestigationService = bugInvestigationService;
    }

    /**
     * Run a full investigation for a bug description.
     * Example: POST http://localhost:8080/api/investigate?bug=Checkout%20crashes%20when%20cart%20is%20empty
     */
    @PostMapping("/investigate")
    public ResponseEntity<Map<String, Object>> investigate(@RequestParam String bug) {
        Map<String, Object> response = new HashMap<>();
        try {
            InvestigationReport report = bugInvestigationService.investigate(bug);
            response.put("status", report.error == null ? "success" : "error");
            response.put("report", report.toMap());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}