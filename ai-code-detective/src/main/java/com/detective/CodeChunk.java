package com.detective;

public class CodeChunk {

    private final String filePath;
    private final String content;
    private final double[] embedding;

    public CodeChunk(String filePath, String content, double[] embedding) {
        this.filePath = filePath;
        this.content = content;
        this.embedding = embedding;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getContent() {
        return content;
    }

    public double[] getEmbedding() {
        return embedding;
    }
}