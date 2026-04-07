package com.S_aman.RepoLens.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;

@Configuration
public class VectorConfig {


    @Bean
    EmbeddingModel embeddingModel(@Value("${GEMINI_API_KEY}") String apiKey) {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-embedding-001")
                .outputDimensionality(768)
                .build();
    }
	
	@Bean
	 EmbeddingStore<TextSegment> embeddingStore(){
		return PgVectorEmbeddingStore.builder()
				.host("localhost")
				.port(5432)
				.database("repolens")
				.user("aman")
				.password("aj906443")
				.table("test_segments")
				.dimension(768)
				.build();
	}
	@Bean
	ContentRetriever contentRetriever(EmbeddingModel embeddingModel,EmbeddingStore<TextSegment> embeddingStore) {
		return EmbeddingStoreContentRetriever.builder()
				.embeddingModel(embeddingModel)
				.embeddingStore(embeddingStore)
				.minScore(0.6)
				.maxResults(3)
				.build();
	}

}
