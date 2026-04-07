package com.S_aman.RepoLens.Controller;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.ResponseBody;
//import org.springframework.web.bind.annotation.RestController;

import com.S_aman.RepoLens.service.CrawlerService;

@Controller
@RequestMapping("/api")
public class RepoController {
	
	@Autowired
	CrawlerService crawler;
	
	@GetMapping("/")
    public String index() {
        return "index"; // Shows index.html
    }
	
	@PostMapping("/Repository")
	public String fetchRepo(@RequestParam("url") String url) throws IOException {
		crawler.fetchRepositoryData(url);
		
		return "redirect:/api/ask";	
	}
	
	
	@GetMapping("/ask")
    public String showChat() {
        return "chat"; // Shows empty chat.html
    }
	
	@PostMapping("/chat")
    public String askAgent(@RequestParam("userPrompt") String userPrompt, Model model) {
        String answer = crawler.askQuestion(userPrompt);
        model.addAttribute("response", answer);
        model.addAttribute("question", userPrompt);
        return "chat"; // Returns chat.html WITH the answer
    }


}