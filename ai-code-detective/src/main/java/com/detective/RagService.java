package com.detective;

import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@Service
public class RagService {

    private final RepositoryService repositoryService;
    private final OllamaService ollamaService;
    private final EmbeddingStore embeddingStore;
    private final CodeGraphService codeGraphService;

    // Chunk size limit (chars) — keep chunks small enough for the LLM/embedding context
    private static final int MAX_CHUNK_CHARS = 1500;

    // The most recently analyzed dependency graph
    private DependencyGraph currentGraph;

    public RagService(RepositoryService repositoryService,
                      OllamaService ollamaService,
                      EmbeddingStore embeddingStore,
                      CodeGraphService codeGraphService) {
        this.repositoryService = repositoryService;
        this.ollamaService = ollamaService;
        this.embeddingStore = embeddingStore;
        this.codeGraphService = codeGraphService;
    }

    /**
     * Clone a repo, chunk the code, generate embeddings, and store them.
     * Also builds the dependency graph.
     * Returns the number of chunks stored.
     */
    public int analyzeRepository(String repoUrl) throws Exception {
        embeddingStore.clear();

        File repoDir = repositoryService.cloneRepository(repoUrl);

        List<File> sourceFiles = repositoryService.findSourceFiles(repoDir);
        System.out.println("Found " + sourceFiles.size() + " source files");

        this.currentGraph = codeGraphService.buildGraph(repoDir, sourceFiles);
        System.out.println("Built graph with "
                + currentGraph.getNodes().size() + " nodes and "
                + currentGraph.getEdges().size() + " edges");

        int chunkCount = 0;

        for (File file : sourceFiles) {
            String content = Files.readString(file.toPath());
            if (content.length() < 50) continue;

            String relativePath = repoDir.toURI()
                    .relativize(file.toURI())
                    .getPath();

            List<String> chunks = splitIntoChunks(content);

            for (String chunk : chunks) {
                String chunkWithContext = "File: " + relativePath + "\n\n" + chunk;

                try {
                    double[] embedding = ollamaService.embed(chunkWithContext);
                    CodeChunk codeChunk = new CodeChunk(relativePath, chunkWithContext, embedding);
                    embeddingStore.add(codeChunk);
                    chunkCount++;

                    if (chunkCount % 10 == 0) {
                        System.out.println("Embedded " + chunkCount + " chunks so far...");
                    }
                } catch (Exception e) {
                    System.out.println("Skipping chunk from " + relativePath
                            + " (embedding failed: " + e.getMessage() + ")");
                }
            }
        }

        System.out.println("Done. Total chunks embedded: " + chunkCount);
        return chunkCount;
    }

    /**
     * Ask a question about the analyzed repository.
     */
    public String ask(String question) {
        if (embeddingStore.size() == 0) {
            return "No repository analyzed yet. Please call /api/analyze first.";
        }

        double[] questionEmbedding = ollamaService.embed(question);

        List<CodeChunk> topChunks = embeddingStore.search(questionEmbedding, 5);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a code assistant. Answer the question using ONLY the code below.\n");
        prompt.append("If the answer is not in the code, say 'I don't know based on the code provided.'\n\n");
        prompt.append("=== CODE CONTEXT ===\n");

        for (CodeChunk chunk : topChunks) {
            prompt.append(chunk.getContent()).append("\n\n---\n\n");
        }

        prompt.append("=== QUESTION ===\n");
        prompt.append(question).append("\n\n");
        prompt.append("=== ANSWER ===\n");

        return ollamaService.chat(prompt.toString());
    }

    /**
     * Retrieve the top-K most relevant code chunks for a question.
     * Used by the Bug Investigation Agent to get raw context without an LLM call.
     */
    public List<CodeChunk> retrieveForQuestion(String question, int topK) {
        if (embeddingStore.size() == 0) {
            return new java.util.ArrayList<>();
        }
        double[] questionEmbedding = ollamaService.embed(question);
        return embeddingStore.search(questionEmbedding, topK);
    }

    public DependencyGraph getCurrentGraph() {
        return currentGraph;
    }

    /**
     * Split a file into chunks of ~MAX_CHUNK_CHARS characters.
     * Hard-caps each chunk so it never exceeds the embedding model's context.
     */
    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new java.util.ArrayList<>();

        if (content.length() <= MAX_CHUNK_CHARS) {
            chunks.add(content);
            return chunks;
        }

        String[] parts = content.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            // If a single part is bigger than the limit, hard-split it
            if (part.length() > MAX_CHUNK_CHARS) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                for (int i = 0; i < part.length(); i += MAX_CHUNK_CHARS) {
                    int end = Math.min(i + MAX_CHUNK_CHARS, part.length());
                    chunks.add(part.substring(i, end));
                }
                continue;
            }

            if (current.length() + part.length() + 2 > MAX_CHUNK_CHARS && current.length() > 0) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(part).append("\n\n");
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}