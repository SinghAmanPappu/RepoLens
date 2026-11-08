package com.S_aman.RepoLens.config;

import java.io.IOException;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GithubConfig {

    private static final Logger log = LoggerFactory.getLogger(GithubConfig.class);

    @Value("${github.token:}")
    private String githubPatToken;

    @Bean
    GitHub gitHubClient() throws IOException {
        if (githubPatToken == null || githubPatToken.isBlank() || githubPatToken.startsWith("dummy")) {
            log.warn("GITHUB_TOKEN is missing or dummy — using unauthenticated GitHub API (public repos only, rate-limited). Set GITHUB_TOKEN for private repos/higher limits.");
            return GitHub.connectAnonymously();
        }
        return new GitHubBuilder()
                .withOAuthToken(githubPatToken)
                .build();
    }
}
