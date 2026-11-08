package com.S_aman.RepoLens.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

@Configuration
public class VectorConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorConfig.class);

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.embedding-model:gemini-embedding-001}")
    private String embeddingModelName;

    @Value("${embedding.provider:ollama}")
    private String embeddingProvider;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:nomic-embed-text}")
    private String ollamaModel;

    @Value("${db.host:localhost}")
    private String dbHost;

    @Value("${db.port:5432}")
    private int dbPort;

    @Value("${db.database:repolens}")
    private String dbDatabase;

    @Value("${db.user:repolens}")
    private String dbUser;

    @Value("${db.password:repolens}")
    private String dbPassword;

    @Value("${db.table:test_segments}")
    private String dbTable;

    @Value("${db.dimension:768}")
    private int dbDimension;

    @Value("${retrieval.min-score:0.6}")
    private double retrievalMinScore;

    @Value("${retrieval.max-results:3}")
    private int retrievalMaxResults;

    @Bean
    EmbeddingModel embeddingModel() {
        String provider = (embeddingProvider == null ? "ollama" : embeddingProvider.trim().toLowerCase());
        if ("gemini".equals(provider)) {
            if (geminiApiKey == null || geminiApiKey.isBlank() || geminiApiKey.startsWith("dummy")) {
                log.warn("GEMINI_API_KEY missing/dummy — cloud embeddings will fail. Prefer EMBEDDING_PROVIDER=ollama (free).");
            }
            String model = (embeddingModelName == null || embeddingModelName.isBlank()) ? "gemini-embedding-001" : embeddingModelName;
            log.info("Using Gemini embeddings (quota-limited free tier).");
            return GoogleAiEmbeddingModel.builder()
                    .apiKey(geminiApiKey)
                    .modelName(model)
                    .outputDimensionality(dbDimension)
                    .build();
        }
        // Default: local Ollama — permanently free, no quota, no key.
        String url = (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) ? "http://localhost:11434" : ollamaBaseUrl;
        String model = (ollamaModel == null || ollamaModel.isBlank()) ? "nomic-embed-text" : ollamaModel;
        log.info("Using local Ollama embeddings (model={}, baseUrl={}) — free, unlimited local.", model, url);
        return OllamaEmbeddingModel.builder()
                .baseUrl(url)
                .modelName(model)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(dbHost)
                .port(dbPort)
                .database(dbDatabase)
                .user(dbUser)
                .password(dbPassword)
                .table(dbTable)
                .dimension(dbDimension)
                .build();
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .minScore(retrievalMinScore)
                .maxResults(retrievalMaxResults)
                .build();
    }

    @Bean
    RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();
    }
}
