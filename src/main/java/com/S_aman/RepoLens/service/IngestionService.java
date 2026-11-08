package com.S_aman.RepoLens.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int MAX_RETRIES = 4;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // Local token counter only (no API key, no network).
    private final TokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4o");

    public IngestionService(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    public void ingest(String content, String path) {
        log.info("Starting ingestion for {}", path);
        Document document = Document.from(content, Metadata.from("file_path", path));
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50, estimator);
        List<TextSegment> chunks = splitter.split(document);
        log.info("Document {} split into {} chunks", path, chunks.size());
        if (chunks.isEmpty()) return;
        List<Embedding> embeddings = embedWithRetry(chunks, path);
        embeddingStore.addAll(embeddings, chunks);
        log.info("Ingestion complete for {}", path);
    }

    /** Free-tier safe embedding: retries 429/RESOURCE_EXHAUSTED with backoff. With local Ollama this never triggers. */
    private List<Embedding> embedWithRetry(List<TextSegment> chunks, String path) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Embedding {} chunk(s) for {} (attempt {})", chunks.size(), path, attempt);
                return embeddingModel.embedAll(chunks).content();
            } catch (RuntimeException e) {
                last = e;
                String msg = String.valueOf(e.getMessage());
                String cause = e.getCause() != null ? String.valueOf(e.getCause().getMessage()) : "";
                String all = msg + " " + cause;
                boolean quota = all.contains("429") || all.toUpperCase().contains("RESOURCE_EXHAUSTED")
                        || all.toLowerCase().contains("rate limit") || all.toLowerCase().contains("quota");
                if (!quota || attempt == MAX_RETRIES) throw e;
                // Respect provider RetryInfo when present, else exponential backoff.
                long delaySec = parseRetryDelaySeconds(all, Math.min(60L, (1L << attempt) * 5)); // 10,20,40,60s
                log.warn("Embedding quota/429 for {} — retrying in {}s (attempt {}/{})", path, delaySec, attempt, MAX_RETRIES);
                try { Thread.sleep(delaySec * 1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); throw e;
                }
            }
        }
        throw last;
    }

    static long parseRetryDelaySeconds(String text, long fallback) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)retry(?: in|Delay|Info)[^0-9]{0,30}(\\d+)\\s*(s|sec)").matcher(text);
            if (m.find()) return Long.parseLong(m.group(1));
            m = java.util.regex.Pattern.compile("(?i)(\\d+)\\.?\\d*s\\b").matcher(text);
            if (m.find()) return Math.min(60L, Long.parseLong(m.group(1)));
        } catch (Exception ignored) {}
        return fallback;
    }

    public void clearDatabase() {
        log.info("Clearing old embeddings...");
        embeddingStore.removeAll();
    }
}
