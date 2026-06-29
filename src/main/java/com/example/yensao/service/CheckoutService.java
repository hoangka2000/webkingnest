package com.example.yensao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CheckoutService {

    private static final Map<String, Double> COUPON_DISCOUNTS = Map.of(
            "KINGNEST10", 0.10,
            "FACEBOOK5", 0.05
    );

    private final ProductService productService;

    @Value("${app.checkout.base-url:http://localhost:8080}")
    private String baseUrl;

    public CheckoutService(ProductService productService) {
        this.productService = productService;
    }

    public Map<String, Object> buildCheckout(String productsParam, String coupon) {
        Map<String, Integer> requestedQuantities = parseProductQuantities(productsParam);
        List<Map<String, Object>> lineItems = new ArrayList<>();
        List<String> missingProducts = new ArrayList<>();
        long subtotal = 0L;

        for (Map.Entry<String, Integer> entry : requestedQuantities.entrySet()) {
            Optional<Map<String, Object>> productOptional = productService.getProductDetail(entry.getKey());
            if (productOptional.isEmpty()) {
                missingProducts.add(entry.getKey());
                continue;
            }

            Map<String, Object> product = productOptional.get();
            int quantity = entry.getValue();
            long price = product.get("price") instanceof Number number ? number.longValue() : 0L;
            long lineTotal = price * quantity;
            subtotal += lineTotal;

            Map<String, Object> lineItem = new LinkedHashMap<>();
            lineItem.put("id", product.get("id"));
            lineItem.put("slug", product.get("slug"));
            lineItem.put("title", product.get("title"));
            lineItem.put("desc", product.get("desc"));
            lineItem.put("price", price);
            lineItem.put("image", product.get("image"));
            lineItem.put("quantity", quantity);
            lineItem.put("lineTotal", lineTotal);
            lineItems.add(lineItem);
        }

        String normalizedCoupon = normalizeCoupon(coupon);
        long discount = calculateDiscount(subtotal, normalizedCoupon);
        long total = Math.max(0L, subtotal - discount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", lineItems.isEmpty() ? false : missingProducts.isEmpty());
        result.put("products", lineItems);
        result.put("productQuantities", requestedQuantities);
        result.put("coupon", normalizedCoupon == null ? "No coupon applied" : normalizedCoupon);
        result.put("couponApplied", normalizedCoupon != null && discount > 0);
        result.put("subtotal", subtotal);
        result.put("discount", discount);
        result.put("shipping", 0L);
        result.put("total", total);
        result.put("currency", "VND");
        result.put("missingProducts", missingProducts);
        result.put("checkoutUrl", buildCheckoutPageUrl(productsParam, coupon));
        return result;
    }

    public URI buildCheckoutPageUri(String productsParam, String coupon) {
        return URI.create(buildCheckoutPageUrl(productsParam, coupon));
    }

    private String buildCheckoutPageUrl(String productsParam, String coupon) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(trimTrailingSlash(baseUrl) + "/gio-hang")
                .queryParam("step", "2")
                .queryParam("products", productsParam);

        if (coupon != null && !coupon.isBlank()) {
            builder.queryParam("coupon", coupon.trim());
        }

        return builder.build(true).toUriString();
    }

    private Map<String, Integer> parseProductQuantities(String productsParam) {
        Map<String, Integer> productQuantities = new LinkedHashMap<>();
        if (productsParam == null || productsParam.isBlank()) {
            return productQuantities;
        }

        for (String productEntry : productsParam.split(",")) {
            String trimmed = productEntry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split(":");
            if (parts.length != 2) {
                continue;
            }

            String productId = parts[0].trim();
            int quantity = Integer.parseInt(parts[1].trim());
            if (productId.isEmpty() || quantity <= 0) {
                continue;
            }

            productQuantities.merge(productId, quantity, Integer::sum);
        }

        return productQuantities;
    }

    private String normalizeCoupon(String coupon) {
        if (coupon == null || coupon.isBlank()) {
            return null;
        }
        return coupon.trim().toUpperCase(Locale.ROOT);
    }

    private long calculateDiscount(long subtotal, String coupon) {
        if (coupon == null || subtotal <= 0) {
            return 0L;
        }

        Double rate = COUPON_DISCOUNTS.get(coupon);
        if (rate == null) {
            return 0L;
        }

        return Math.round(subtotal * rate);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
