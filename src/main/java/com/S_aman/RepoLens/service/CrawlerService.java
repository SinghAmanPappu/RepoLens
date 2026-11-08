package com.S_aman.RepoLens.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.S_aman.RepoLens.Agent.RepolensAgent;

@Service
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    @Autowired
    private RepolensAgent agent;

    @Autowired
    private IngestionService ingestionService;

    private final GitHub github;

    @Value("${ingestion.extensions:.java,.xml,.properties,.md}")
    private String extensionsProperty;

    @Value("${ingestion.max-file-bytes:300000}")
    private long maxFileBytes;

    @Value("${ingestion.max-files-per-repo:300}")
    private int maxFilesPerRepo;

    /** Vendored / build / generated dirs to skip (case-insensitive path contains). */
    private static final String[] SKIP_DIRS = {
        "node_modules/", "/.git/", "/dist/", "/build/", "/target/", "__pycache__/", "/.venv/",
        "/.next/", "/vendor/", "/.gradle/", "/.idea/", "/.vscode/"
    };

    /** Binary / lock / minified files to skip (suffix match, lower-case). */
    private static final String[] SKIP_SUFFIXES = {
        ".min.js", ".min.css", ".lock", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
        "poetry.lock", "Cargo.lock", "Gemfile.lock", "composer.lock", "go.sum",
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".ico", ".pdf", ".zip",
        ".tar", ".gz", ".mp4", ".mp3", ".wav", ".ogg", ".woff", ".woff2",
        ".ttf", ".eot", ".otf", ".class", ".jar", ".war", ".pyc", ".o", ".so", ".dll", ".exe"
    };

    /** Extension-less files that ARE source (handled by name). */
    private static final String[] INCLUDE_NAMES = {
        "dockerfile", "makefile", "containerfile", "gemfile", "rakefile", "cmakelists.txt",
        "readme", "license", "changelog", "contributing"
    };

    public CrawlerService(GitHub github) {
        this.github = github;
    }

    /**
     * Accepts "owner/repo", "https://github.com/owner/repo", with or without .git / trailing slash.
     * Returns normalized "owner/repo".
     */
    public static String normalizeRepoName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Repository is required.");
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Repository is required.");
        }
        // Strip URL prefix
        s = s.replaceFirst("(?i)^https?://(www\\.)?github\\.com/", "");
        s = s.replaceFirst("(?i)^git@github\\.com:", "");
        // Strip trailing slash and .git
        s = s.replaceAll("/+$", "").replaceAll("(?i)\\.git$", "");
        s = s.trim();
        // Keep only owner/repo (drop extra path like /tree/main)
        String[] parts = s.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid repository '" + raw + "'. Use owner/repo, e.g. SinghAmanPappu/RepoLens");
        }
        String owner = parts[0].trim();
        String repo = parts[1].trim();
        if (owner.isEmpty() || repo.isEmpty()) {
            throw new IllegalArgumentException("Invalid repository '" + raw + "'. Use owner/repo.");
        }
        return owner + "/" + repo;
    }

    /** Index a repo: clear old embeddings, fetch text files, embed them. Returns number of files ingested. */
    public int fetchRepositoryData(String repoNameRaw) throws IOException {
        String repoName = normalizeRepoName(repoNameRaw);
        log.info("Fetching repository {}", repoName);
        GHRepository repo;
        try {
            repo = github.getRepository(repoName);
        } catch (IOException e) {
            throw new IOException("Could not find repository '" + repoName + "': " + e.getMessage(), e);
        }
        log.info("Connected to repository {}", repo.getFullName());

        ingestionService.clearDatabase();

        GHTree tree = repo.getTreeRecursive(repo.getDefaultBranch(), 0);
        List<String> exts = Arrays.stream(extensionsProperty.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toList());

        // UNIVERSAL filter: broad extensions + extension-less source names,
        // skip vendored dirs + binaries/locks. Any GitHub repo/language works.
        List<GHTreeEntry> candidates = tree.getTree().stream()
                .filter(p -> "blob".equals(p.getType()))
                .filter(p -> {
                    String lower = p.getPath().toLowerCase(Locale.ROOT);
                    for (String d : SKIP_DIRS) if (lower.contains(d)) return false;
                    for (String s : SKIP_SUFFIXES) if (lower.endsWith(s)) return false;
                    if (exts.stream().anyMatch(lower::endsWith)) return true;
                    String name = lower.substring(lower.lastIndexOf('/') + 1);
                    for (String n : INCLUDE_NAMES) if (name.equals(n) || name.startsWith(n + ".")) return true;
                    return false;
                })
                .collect(Collectors.toList());

        log.info("Found {} candidate files (extensions {})", candidates.size(), exts);
        if (candidates.isEmpty()) {
            log.warn("No matching files in {} — nothing to ingest. Extensions {}", repoName, exts);
            return 0;
        }
        if (candidates.size() > maxFilesPerRepo) {
            log.warn("Repo has {} matching files — ingesting first {} only", candidates.size(), maxFilesPerRepo);
            candidates = candidates.subList(0, maxFilesPerRepo);
        }

        int ingested = 0;
        for (GHTreeEntry entry : candidates) {
            try {
                GHContent content = repo.getFileContent(entry.getPath());
                long size = content.getSize();
                if (size > maxFileBytes) {
                    log.warn("Skipping {} ({} bytes > {} limit)", entry.getPath(), size, maxFileBytes);
                    continue;
                }
                String rawCode;
                try (var is = content.read()) {
                    rawCode = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (rawCode.trim().isEmpty()) {
                    log.info("Skipping empty file {}", entry.getPath());
                    continue;
                }
                ingestionService.ingest(rawCode, entry.getPath());
                ingested++;
            } catch (IOException e) {
                log.error("Failed to ingest {}: {}", entry.getPath(), e.getMessage());
            }
        }
        log.info("Ingested {} files from {}", ingested, repoName);
        return ingested;
    }

    public String askQuestion(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "Please ask a question about the ingested code.";
        }
        // Chat quota is tiny (RPM) — retry 429/RESOURCE_EXHAUSTED once with backoff.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return agent.chat(userPrompt);
            } catch (RuntimeException e) {
                String all = String.valueOf(e.getMessage()) + " " + (e.getCause() != null ? e.getCause().getMessage() : "");
                boolean quota = all.contains("429") || all.toUpperCase().contains("RESOURCE_EXHAUSTED")
                        || all.toLowerCase().contains("rate limit") || all.toLowerCase().contains("quota");
                if (!quota || attempt == 3) throw e;
                long delaySec = IngestionService.parseRetryDelaySeconds(all, attempt * 5L);
                log.warn("Chat quota/429 — retrying in {}s (attempt {})", delaySec, attempt);
                try { Thread.sleep(delaySec * 1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); throw e;
                }
            }
        }
        throw new RuntimeException("Chat failed after retries");
    }
}
