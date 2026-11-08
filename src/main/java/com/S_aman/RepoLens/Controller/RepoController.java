package com.S_aman.RepoLens.Controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.S_aman.RepoLens.service.CrawlerService;

// NOTE: package is "Controller" (capital C) to match the existing layout.
// Convention would be lowercase "controller"; kept as-is to avoid breaking imports.
@Controller
@RequestMapping("/api")
public class RepoController {

    private static final Logger log = LoggerFactory.getLogger(RepoController.class);

    @Autowired
    private CrawlerService crawler;

    /** Root of the app — fixes previous bug where "/" 404'd (controller was mapped to /api/*). */
    @GetMapping({ "/", "" })
    public String root() {
        return "index";
    }

    @PostMapping("/Repository")
    public String fetchRepo(@RequestParam("url") String url, RedirectAttributes flash) {
        String normalized;
        try {
            normalized = CrawlerService.normalizeRepoName(url);
            int count = crawler.fetchRepositoryData(normalized);
            flash.addFlashAttribute("success", "Indexed " + count + " file(s) from " + normalized + ". You can now ask questions.");
            flash.addFlashAttribute("repo", normalized);
            log.info("Indexed {} files from {}", count, normalized);
        } catch (Exception e) {
            log.warn("Failed to index {}: {}", url, e.getMessage());
            flash.addFlashAttribute("error", "Could not index '" + url + "': " + e.getMessage());
        }
        return "redirect:/api/ask";
    }

    @GetMapping("/ask")
    public String showChat(Model model) {
        return "chat";
    }

    @PostMapping("/chat")
    public String askAgent(@RequestParam("userPrompt") String userPrompt, Model model) {
        try {
            String answer = crawler.askQuestion(userPrompt);
            model.addAttribute("response", answer);
            model.addAttribute("question", userPrompt);
        } catch (Exception e) {
            log.error("Chat failed: {}", e.getMessage(), e);
            model.addAttribute("error", "Something went wrong answering: " + e.getMessage());
            model.addAttribute("question", userPrompt);
        }
        return "chat";
    }

    // ---- Interactive JSON endpoints (no page reload) ----

    /** POST /api/analyze  params: url  -> {ok, repo, files, message} */
    @PostMapping("/analyze")
    @ResponseBody
    public Map<String, Object> analyzeJson(@RequestParam("url") String url) {
        Map<String, Object> out = new HashMap<>();
        try {
            String normalized = CrawlerService.normalizeRepoName(url);
            int count = crawler.fetchRepositoryData(normalized);
            out.put("ok", true);
            out.put("repo", normalized);
            out.put("files", count);
            out.put("message", "Indexed " + count + " file(s) from " + normalized + ".");
            log.info("Indexed {} files from {} (JSON)", count, normalized);
            return out;
        } catch (Exception e) {
            log.warn("Failed to index {} (JSON): {}", url, e.getMessage());
            out.put("ok", false);
            out.put("message", "Could not index '" + url + "': " + e.getMessage());
            return out;
        }
    }

    /** POST /api/chat-json  params: userPrompt  -> {ok, question, answer} */
    @PostMapping({ "/chat-json", "/ask-json" })
    @ResponseBody
    public Map<String, Object> chatJson(@RequestParam("userPrompt") String userPrompt) {
        Map<String, Object> out = new HashMap<>();
        out.put("question", userPrompt);
        try {
            String answer = crawler.askQuestion(userPrompt);
            out.put("ok", true);
            out.put("answer", answer);
            return out;
        } catch (Exception e) {
            log.error("Chat failed (JSON): {}", e.getMessage(), e);
            out.put("ok", false);
            out.put("answer", "Something went wrong answering: " + e.getMessage());
            return out;
        }
    }

    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        Map<String, String> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "RepoLens");
        return health;
    }

    @ExceptionHandler(Exception.class)
    public String handleError(Exception ex, Model model) {
        log.error("Unhandled error: {}", ex.getMessage(), ex);
        // If this was a JSON fetch, the front-end expects JSON, not a view — detect via header? Keep view fallback.
        model.addAttribute("error", "Unexpected error: " + ex.getMessage());
        return "error";
    }
}
