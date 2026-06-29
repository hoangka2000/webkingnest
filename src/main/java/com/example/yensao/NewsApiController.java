package com.example.yensao;

import com.example.yensao.service.NewsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NewsApiController {

    private final NewsService newsService;

    public NewsApiController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/news")
    public ResponseEntity<Map<String, Object>> listNews() {
        List<Map<String, Object>> articles = newsService.getAllArticles();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("articles", articles);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/news-content/{slug}")
    public ResponseEntity<Map<String, Object>> getNewsContent(@PathVariable String slug) {
        return newsService.getArticleBySlug(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(error("Không tìm thấy bài viết với slug: " + slug)));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
