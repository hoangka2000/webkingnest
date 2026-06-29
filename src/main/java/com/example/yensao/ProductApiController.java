package com.example.yensao;

import com.example.yensao.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService productService;

    public ProductApiController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listProducts() {
        List<Map<String, Object>> products = productService.getAllProductsForListing();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("products", products);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable String idOrSlug) {
        return productService.getProductDetail(idOrSlug)
                .map(product -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("success", true);
                    body.put("product", product);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(404).body(error("Không tìm thấy sản phẩm: " + idOrSlug)));
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }
}
