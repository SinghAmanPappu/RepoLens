package com.S_aman.RepoLens.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

@Configuration
public class GeminiConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiConfig.class);

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.chat-model:gemini-2.5-flash-lite}")
    private String modelName;

    @Bean
    ChatModel geminiModel() {
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("dummy")) {
            log.warn("GEMINI_API_KEY is missing or dummy — chat will fail until a real key is set. Set GEMINI_API_KEY env var (see .env.example).");
        }
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName == null || modelName.isBlank() ? "gemini-2.5-flash-lite" : modelName)
                .logRequestsAndResponses(true)
                .build();
    }
}
