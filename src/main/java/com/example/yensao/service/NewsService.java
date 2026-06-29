package com.example.yensao.service;

import com.example.yensao.entity.NewsArticleEntity;
import com.example.yensao.repository.NewsArticleRepository;
import com.example.yensao.util.NewsCategoryUtil;
import com.example.yensao.util.NewsImageUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NewsService {

    private final NewsArticleRepository newsArticleRepository;

    public NewsService(NewsArticleRepository newsArticleRepository) {
        this.newsArticleRepository = newsArticleRepository;
    }

    public List<Map<String, Object>> getAllArticles() {
        return newsArticleRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toSummaryMap)
                .toList();
    }

    public Optional<Map<String, Object>> getArticleBySlug(String slug) {
        return newsArticleRepository.findBySlug(slug).map(this::toDetailMap);
    }

    private Map<String, Object> toSummaryMap(NewsArticleEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slug", entity.getSlug());
        map.put("title", entity.getTitle());
        map.put("category", NewsCategoryUtil.normalize(entity.getSlug(), entity.getCategory()));
        map.put("desc", entity.getExcerpt());
        map.put("image", NewsImageUtil.listImage(entity.getSlug()));
        map.put("date", entity.getPublishedYear());
        return map;
    }

    private Map<String, Object> toDetailMap(NewsArticleEntity entity) {
        Map<String, Object> map = toSummaryMap(entity);
        map.put("image", NewsImageUtil.detailImage(entity.getSlug()));
        map.put("success", true);
        map.put("contentHtml", entity.getContentHtml());
        return map;
    }
}
