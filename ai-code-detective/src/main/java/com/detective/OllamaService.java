package com.detective;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaService {

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.chat.model}")
    private String chatModel;

    @Value("${ollama.embedding.model}")
    private String embeddingModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Generate a chat completion from Ollama.
     */
    public String chat(String prompt) {
        try {
            ObjectNode request = mapper.createObjectNode();
            request.put("model", chatModel);
            request.put("prompt", prompt);
            request.put("stream", false);

            String response = restTemplate.postForObject(
                    baseUrl + "/api/generate",
                    mapper.writeValueAsString(request),
                    String.class
            );

            JsonNode json = mapper.readTree(response);
            return json.get("response").asText();

        } catch (Exception e) {
            return "ERROR calling Ollama chat: " + e.getMessage();
        }
    }

    /**
     * Generate an embedding vector for a piece of text.
     */
    public double[] embed(String text) {
        try {
            ObjectNode request = mapper.createObjectNode();
            request.put("model", embeddingModel);
            request.put("prompt", text);

            String response = restTemplate.postForObject(
                    baseUrl + "/api/embeddings",
                    mapper.writeValueAsString(request),
                    String.class
            );

            JsonNode json = mapper.readTree(response);
            JsonNode embeddingArray = json.get("embedding");

            List<Double> list = new ArrayList<>();
            for (JsonNode node : embeddingArray) {
                list.add(node.asDouble());
            }

            double[] result = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = list.get(i);
            }
            return result;

        } catch (Exception e) {
            throw new RuntimeException("ERROR calling Ollama embeddings: " + e.getMessage(), e);
        }
    }
}