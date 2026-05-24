package com.leave_service.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String model;

    @Bean
    public OllamaChatModel ollamaChatModel() {
        OllamaApi ollamaApi = new OllamaApi(baseUrl);
        
        OllamaOptions options = OllamaOptions.builder()
            .withModel(model)
            .withTemperature(0.0)
            .withNumCtx(8192)
            .withNumPredict(512)
            .build();
        
        OllamaChatModel chatModel = new OllamaChatModel(ollamaApi, options);
        
        System.out.println("[Ollama] Model: " + model);
        System.out.println("[Ollama] Base URL: " + baseUrl);
        System.out.println("[Ollama] Function Calling: ENABLED");
        
        return chatModel;
    }
}
