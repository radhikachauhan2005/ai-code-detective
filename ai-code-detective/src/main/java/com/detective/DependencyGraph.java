package com.detective;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DependencyGraph {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    public void addNode(GraphNode node) {
        nodes.putIfAbsent(node.getId(), node);
    }

    public void addEdge(GraphEdge edge) {
        for (GraphEdge e : edges) {
            if (e.getSource().equals(edge.getSource())
                    && e.getTarget().equals(edge.getTarget())
                    && e.getType() == edge.getType()) {
                return;
            }
        }
        edges.add(edge);
    }

    public Map<String, GraphNode> getNodes() { return nodes; }
    public List<GraphEdge> getEdges() { return edges; }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public Map<String, Object> toMap() {
        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (GraphNode n : nodes.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("label", n.getLabel());
            m.put("filePath", n.getFilePath());
            m.put("type", n.getType().name().toLowerCase());
            m.put("lineCount", n.getLineCount());
            if (n.getParentId() != null) {
                m.put("parentId", n.getParentId());
            }
            nodeList.add(m);
        }

        List<Map<String, Object>> edgeList = new ArrayList<>();
        for (GraphEdge e : edges) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", e.getSource());
            m.put("target", e.getTarget());
            m.put("type", e.getType().name().toLowerCase());
            edgeList.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodeList);
        result.put("edges", edgeList);
        return result;
    }
}