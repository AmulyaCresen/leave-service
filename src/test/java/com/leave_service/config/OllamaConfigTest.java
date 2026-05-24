package com.leave_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class OllamaConfigTest {

    @Test
    void ollamaChatModel_bean_isCreated() {
        OllamaConfig config = new OllamaConfig();
        ReflectionTestUtils.setField(config, "baseUrl", "http://localhost:11434");
        ReflectionTestUtils.setField(config, "model", "llama3.2");

        OllamaChatModel chatModel = config.ollamaChatModel();

        assertNotNull(chatModel);
    }
}
