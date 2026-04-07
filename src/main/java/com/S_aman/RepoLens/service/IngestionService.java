package com.S_aman.RepoLens.service;

import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.googleai.GoogleAiGeminiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;



@Service
public class IngestionService {

	private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
	
	
	@Value("${GEMINI_API_KEY}")
    private String apiKey;

	// Inject both the Store and the Model
    public IngestionService(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }
	
			public void Ingest(String content,String path)
			{
				System.out.println("=== Starting Ingestion for: " + path + " ===");
				Document document=Document.from(content, Metadata.from("file_path",path));
				
				
//				TokenCountEstimator estimator = GoogleAiGeminiTokenCountEstimator.builder()
//					    .apiKey(apiKey)
//					    .modelName("gemini-2.5-flash-lite")
//					    .build();
				
				TokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4o");
		        
		       
				
				DocumentSplitter splitter= DocumentSplitters.recursive(500, 50, estimator);
				//splitting the document and storing the chunks
				List<TextSegment> chunks=splitter.split(document);
				System.out.println("1. Document split into " + chunks.size() + " chunks.");
				// embedding every chunks the content is like jsut getting the content from the response which embdedding creates
				System.out.println("2. Sending chunks to Gemini for embedding... (This may take a minute)");
				List<Embedding> embeddings = embeddingModel.embedAll(chunks).content();
				System.out.println("3. Successfully received embeddings from Google AI.");
				//storing the embedding 
				System.out.println("4. Saving to Postgres Docker container...");
				embeddingStore.addAll(embeddings, chunks);
				
				System.out.println("=== SUCCESS: Ingestion Completed for " + path + " ===");
				
				System.out.println("emdding done");
				
			
				
			}

			public void clearDatabase() {
				System.out.println("Cleaning up old embeddings from the database...");
				embeddingStore.removeAll();
				// TODO Auto-generated method stub
				
			}
}
