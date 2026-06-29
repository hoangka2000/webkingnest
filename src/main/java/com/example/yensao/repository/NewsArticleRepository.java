package com.example.yensao.repository;

import com.example.yensao.entity.NewsArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, Long> {

    Optional<NewsArticleEntity> findBySlug(String slug);

    List<NewsArticleEntity> findAllByOrderBySortOrderAsc();
}
