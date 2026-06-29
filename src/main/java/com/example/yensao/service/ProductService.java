package com.example.yensao.service;

import com.example.yensao.entity.ProductEntity;
import com.example.yensao.repository.ProductRepository;
import com.example.yensao.util.ProductTypeUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageService productImageService;

    public ProductService(ProductRepository productRepository, ProductImageService productImageService) {
        this.productRepository = productRepository;
        this.productImageService = productImageService;
    }

    public List<Map<String, Object>> getAllProductsForListing() {
        return productRepository.findAll().stream()
                .map(this::toListingMap)
                .toList();
    }

    public Optional<Map<String, Object>> getProductDetail(String idOrSlug) {
        Optional<ProductEntity> product = findProduct(idOrSlug);
        return product.map(this::toDetailMap);
    }

    private Optional<ProductEntity> findProduct(String idOrSlug) {
        try {
            long id = Long.parseLong(idOrSlug);
            return productRepository.findById(id);
        } catch (NumberFormatException ignored) {
            return productRepository.findBySlug(idOrSlug);
        }
    }

    private Map<String, Object> toListingMap(ProductEntity entity) {
        ProductImageService.ProductImages images = resolveImages(entity);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("slug", entity.getSlug());
        map.put("title", entity.getTitle());
        map.put("desc", entity.getShortDesc());
        map.put("price", entity.getPrice());
        map.put("image", images.listImage());
        map.put("type", ProductTypeUtil.normalize(entity.getProductType()));
        map.put("need", entity.getNeed());
        map.put("status", entity.getStatus());
        map.put("badge", entity.getBadge());
        return map;
    }

    private Map<String, Object> toDetailMap(ProductEntity entity) {
        ProductImageService.ProductImages images = resolveImages(entity);
        Map<String, Object> map = toListingMap(entity);
        map.put("category", entity.getCategory());
        map.put("gallery", images.gallery());
        map.put("benefits", entity.getBenefits());
        map.put("usage", entity.getUsage());
        map.put("specs", entity.getSpecs());
        map.put("description", entity.getDescription());
        map.put("highlights", entity.getHighlights());
        map.put("contentHtml", entity.getContentHtml());
        return map;
    }

    private ProductImageService.ProductImages resolveImages(ProductEntity entity) {
        return productImageService.resolve(entity.getSlug())
                .orElseGet(() -> new ProductImageService.ProductImages(
                        entity.getImage(),
                        entity.getGallery() != null && !entity.getGallery().isEmpty()
                                ? entity.getGallery()
                                : List.of(entity.getImage())
                ));
    }
}
