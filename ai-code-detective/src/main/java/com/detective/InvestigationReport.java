package com.detective;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InvestigationReport {

    public String bugDescription = "";
    public long durationMs = 0;

    // Phase 1: what we found
    public List<String> relevantFiles = new ArrayList<>();
    public List<String> relevantChunkSummaries = new ArrayList<>();

    // Phase 2: graph-based call chain
    public List<String> callChain = new ArrayList<>();

    // Phase 3: LLM reasoning
    public String probableRootCause = "";
    public String confidence = "low";         // low | medium | high
    public String evidence = "";

    // Phase 4: reproduction
    public List<String> reproductionSteps = new ArrayList<>();

    // Phase 5: fix
    public String suggestedFix = "";
    public String suggestedFixFile = "";

    // Phase 6: impact analysis
    public List<String> impactedComponents = new ArrayList<>();

    // Any error that occurred mid-investigation
    public String error = null;

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bugDescription", bugDescription);
        m.put("durationMs", durationMs);
        m.put("relevantFiles", relevantFiles);
        m.put("relevantChunkSummaries", relevantChunkSummaries);
        m.put("callChain", callChain);
        m.put("probableRootCause", probableRootCause);
        m.put("confidence", confidence);
        m.put("evidence", evidence);
        m.put("reproductionSteps", reproductionSteps);
        m.put("suggestedFix", suggestedFix);
        m.put("suggestedFixFile", suggestedFixFile);
        m.put("impactedComponents", impactedComponents);
        if (error != null) m.put("error", error);
        return m;
    }
}