package com.detective;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final OllamaService ollamaService;

    public TestController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @GetMapping("/test")
    public String test() {
        return "Backend is working!";
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return ollamaService.chat(question);
    }

    @GetMapping("/embed")
    public String embed(@RequestParam String text) {
        double[] vector = ollamaService.embed(text);
        return "Embedding dimension: " + vector.length
                + " | First 3 values: " + vector[0] + ", " + vector[1] + ", " + vector[2];
    }
}