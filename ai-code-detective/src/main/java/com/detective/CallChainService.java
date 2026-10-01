package com.detective;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CallChainService {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_CHAIN_NODES = 40;

    /**
     * Build a readable call chain starting from a set of seed node ids.
     * Uses BFS over outgoing edges (calls / method_calls / extends / implements).
     */
    public List<String> buildCallChain(DependencyGraph graph, Set<String> seedNodeIds) {
        List<String> result = new ArrayList<>();
        if (graph == null || graph.isEmpty()) return result;

        // Build an adjacency map: source → list of (target, edgeType)
        Map<String, List<EdgeRef>> adjacency = new HashMap<>();
        for (GraphEdge e : graph.getEdges()) {
            adjacency.computeIfAbsent(e.getSource(), k -> new ArrayList<>())
                     .add(new EdgeRef(e.getTarget(), e.getType()));
        }

        Set<String> visited = new HashSet<>();
        Deque<ChainFrame> queue = new ArrayDeque<>();

        // Seed the queue
        for (String seed : seedNodeIds) {
            if (graph.getNodes().containsKey(seed) && visited.add(seed)) {
                queue.add(new ChainFrame(seed, 0));
            }
        }

        int count = 0;

        while (!queue.isEmpty() && count < MAX_CHAIN_NODES) {
            ChainFrame frame = queue.poll();
            GraphNode node = graph.getNodes().get(frame.nodeId);
            if (node == null) continue;

            // Only include method/class/interface nodes with meaningful relationships
            if (node.getType() == GraphNode.NodeType.METHOD
                || node.getType() == GraphNode.NodeType.CLASS
                || node.getType() == GraphNode.NodeType.INTERFACE) {

                String indent = "  ".repeat(frame.depth);
                String prefix = frame.depth == 0 ? "" : "└─ ";

                String label = node.getLabel();
                if (node.getType() == GraphNode.NodeType.METHOD) {
                    label = label + "()";
                }
                result.add(indent + prefix + label + "  [" + node.getFilePath() + "]");
                count++;
            }

            if (frame.depth >= MAX_DEPTH) continue;

            // Follow outgoing CALLS / METHOD_CALLS / EXTENDS / IMPLEMENTS edges
            List<EdgeRef> outEdges = adjacency.getOrDefault(frame.nodeId, Collections.emptyList());
            for (EdgeRef er : outEdges) {
                if (er.type == GraphEdge.EdgeType.METHOD_CALLS
                    || er.type == GraphEdge.EdgeType.CALLS
                    || er.type == GraphEdge.EdgeType.EXTENDS
                    || er.type == GraphEdge.EdgeType.IMPLEMENTS) {

                    if (visited.add(er.target)) {
                        queue.add(new ChainFrame(er.target, frame.depth + 1));
                    }
                }
            }
        }

        return result;
    }

    /**
     * Given chunk file paths from RAG, find matching class/interface/enum nodes in the graph.
     */
    public Set<String> findSeedNodes(DependencyGraph graph, List<String> filePaths) {
        Set<String> seeds = new LinkedHashSet<>();
        if (graph == null || graph.isEmpty()) return seeds;

        Set<String> targetPaths = new HashSet<>(filePaths);
        for (GraphNode n : graph.getNodes().values()) {
            if (targetPaths.contains(n.getFilePath())) {
                if (n.getType() == GraphNode.NodeType.CLASS
                    || n.getType() == GraphNode.NodeType.INTERFACE
                    || n.getType() == GraphNode.NodeType.ENUM) {
                    seeds.add(n.getId());
                }
            }
        }
        return seeds;
    }

    // ---- internal helpers ----

    private static class ChainFrame {
        final String nodeId;
        final int depth;

        ChainFrame(String nodeId, int depth) {
            this.nodeId = nodeId;
            this.depth = depth;
        }
    }

    private static class EdgeRef {
        final String target;
        final GraphEdge.EdgeType type;

        EdgeRef(String target, GraphEdge.EdgeType type) {
            this.target = target;
            this.type = type;
        }
    }
}