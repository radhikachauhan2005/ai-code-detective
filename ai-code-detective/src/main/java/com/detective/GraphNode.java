package com.detective;

public class GraphNode {

    public enum NodeType { CLASS, INTERFACE, ENUM, METHOD, FIELD }

    private final String id;          // unique id (e.g. "CheckoutService.processCheckout")
    private final String label;       // short display name (e.g. "processCheckout")
    private final String filePath;    // relative path in repo
    private final NodeType type;
    private final int lineCount;
    private final String parentId;    // id of the containing class (null for top-level classes)

    public GraphNode(String id, String label, String filePath, NodeType type,
                     int lineCount, String parentId) {
        this.id = id;
        this.label = label;
        this.filePath = filePath;
        this.type = type;
        this.lineCount = lineCount;
        this.parentId = parentId;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getFilePath() { return filePath; }
    public NodeType getType() { return type; }
    public int getLineCount() { return lineCount; }
    public String getParentId() { return parentId; }
}