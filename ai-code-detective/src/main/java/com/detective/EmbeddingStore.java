package com.detective;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingStore {

    private final List<CodeChunk> chunks = new ArrayList<>();

    /**
     * Clear all stored chunks (used before analyzing a new repo).
     */
    public void clear() {
        chunks.clear();
    }

    /**
     * Add a new chunk with its embedding.
     */
    public void add(CodeChunk chunk) {
        chunks.add(chunk);
    }

    /**
     * How many chunks are stored?
     */
    public int size() {
        return chunks.size();
    }

    /**
     * Find the top K chunks most similar to the query embedding.
     * Uses cosine similarity.
     */
    public List<CodeChunk> search(double[] queryEmbedding, int topK) {
        List<ScoredChunk> scored = new ArrayList<>();

        for (CodeChunk chunk : chunks) {
            double score = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
            scored.add(new ScoredChunk(chunk, score));
        }

        // Sort descending by score
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        // Take top K
        List<CodeChunk> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(scored.get(i).chunk);
        }
        return results;
    }

    /**
     * Cosine similarity between two vectors.
     */
    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Internal helper class for scoring.
     */
    private static class ScoredChunk {
        final CodeChunk chunk;
        final double score;

        ScoredChunk(CodeChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}