package com.detective;

public class GraphEdge {

    public enum EdgeType {
        IMPORTS,
        EXTENDS,
        IMPLEMENTS,
        CALLS,
        CONTAINS,
        METHOD_CALLS,
        FIELD_TYPE,
        PARAM_TYPE,
        RETURN_TYPE
    }

    private final String source;
    private final String target;
    private final EdgeType type;

    public GraphEdge(String source, String target, EdgeType type) {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    public String getSource() { return source; }
    public String getTarget() { return target; }
    public EdgeType getType() { return type; }
}