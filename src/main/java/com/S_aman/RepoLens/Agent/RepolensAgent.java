package com.S_aman.RepoLens.Agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface RepolensAgent {
	
	@SystemMessage({
        "You are RepoLens, a Senior Java Architect.",
        "Use the provided code context to answer questions.",
        "Always mention the file path from the metadata.",
        "If the answer isn't in the code, say you don't know."
    })
	String chat(String query);
}
