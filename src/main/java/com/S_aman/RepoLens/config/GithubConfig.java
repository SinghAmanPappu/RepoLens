package com.S_aman.RepoLens.config;

import java.io.IOException;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GithubConfig {
	@Value("${github.token}")
	private String githubPatToken;
	
	@Bean
	GitHub GitHubClient() throws IOException{
		return new GitHubBuilder()
				.withOAuthToken(githubPatToken)
				.build();
	}

}
