package com.S_aman.RepoLens.Agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * Senior Architect chat agent.
 * Previously the ContentRetriever bean was defined but never wired in,
 * so the agent answered from its own knowledge (hallucination risk).
 * Now the retrievalAugmentor (RAG) is attached so answers are grounded
 * in the ingested repo embeddings.
 */
@AiService(retrievalAugmentor = "retrievalAugmentor")
public interface RepolensAgent {

    @SystemMessage({
        "You are RepoLens, a Senior Software Architect for ANY GitHub repo (Java, Python, JavaScript/TypeScript, Go, Rust, C/C++, C#, Ruby, PHP, Swift, HTML/CSS, Kotlin, shell, config/docs, etc.).",
        "Answer ONLY using the provided code context. Do not guess.",
        "Always mention the file path from the metadata.",
        "If the answer isn't in the code context, say you don't know."
    })
    String chat(String query);
}
