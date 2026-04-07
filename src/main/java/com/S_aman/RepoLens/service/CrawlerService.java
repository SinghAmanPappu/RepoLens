package com.S_aman.RepoLens.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.S_aman.RepoLens.Agent.RepolensAgent;

import dev.langchain4j.data.segment.TextSegment;

@Service
public class CrawlerService {
	
	@Autowired
	RepolensAgent agent;
	
	@Autowired
	IngestionService ingestionService;

	private final GitHub github;
	
	List<TextSegment> allChunks = new ArrayList<>();
	
	

	public CrawlerService(GitHub github) {
	    this.github = github;
	}
	
	public void fetchRepositoryFileByName(String repoName) throws IOException
	{
		GHRepository repo=github.getRepository(repoName);
		GHTree tree=repo.getTreeRecursive(repo.getDefaultBranch(),0);
		List<GHTreeEntry> listOfTree=tree.getTree();
//		System.out.println("connected to :"+ tree);
		for(GHTreeEntry t:listOfTree)
		{
			// Instead of parts[parts.length - 1]
			String fileName = java.nio.file.Paths.get(t.getPath()).getFileName().toString();
			    System.out.println(fileName);
			}
		}
	
	
	public void fetchRepositoryData(String repoName) throws IOException
	{
		GHRepository repo=github.getRepository(repoName);
		
		
		
		ingestionService.clearDatabase();
//		System.out.println("successfully connected to github!");
//		System.out.println("Repository :"+repo.getFullName());
		
		GHTree tree=repo.getTreeRecursive(repo.getDefaultBranch(),0);
		
		List<GHTreeEntry> javaFiles = tree.getTree().stream() 
				.filter(p -> p.getType().equals("blob")) 
				.filter(p -> p.getPath().endsWith(".java")) 
				.collect(Collectors.toList());
		
			for (GHTreeEntry entry : javaFiles) {
			    GHContent content = repo.getFileContent(entry.getPath());

			    // Use try-with-resources to read the stream safely
			    try (InputStream is = content.read();
			         Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
			        
			        StringBuilder fileBuilder = new StringBuilder();
			        while (scanner.hasNextLine()) {
			            fileBuilder.append(scanner.nextLine()).append("\n");
			        }

			        String rawCode = fileBuilder.toString();
			        
			        if (rawCode.trim().isEmpty()) {
		                System.out.println("Skipping empty file: " + entry.getPath());
		                continue; 
		            }
			        
			        ingestionService.Ingest(rawCode, entry.getPath());
//			        
//			        allChunks.addAll(chunks);
			        
			        
		
			        
			       
			        
			    } catch (IOException e) {
			        System.err.println("Failed to read file: " + entry.getPath());
			       
			    }
			}
//		       System.out.println("total size of number of chunks :"+allChunks.size());
//		       System.out.println("get the first chunk :"+chunks.getFirst());
			
	}

	public String askQuestion(String userPrompt) {
		return agent.chat(userPrompt);
		
	}	
	
}
