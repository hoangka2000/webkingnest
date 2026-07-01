package com.example.yensao.service;

import com.example.yensao.dto.CreateOrderRequest;
import com.example.yensao.entity.OrderEntity;
import com.example.yensao.entity.OrderStatus;
import com.example.yensao.entity.PaymentMethod;
import com.example.yensao.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final Map<String, Double> COUPON_DISCOUNTS = Map.of(
            "KINGNEST10", 0.10,
            "FACEBOOK5", 0.05
    );

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final OrderEmailService orderEmailService;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        ProductService productService,
                        OrderEmailService orderEmailService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
        this.orderEmailService = orderEmailService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public OrderEntity createOrder(CreateOrderRequest request) {
        validateCustomer(request);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng trống");
        }

        PaymentMethod paymentMethod = parsePaymentMethod(request.getPaymentMethod());
        List<Map<String, Object>> lineItems = new ArrayList<>();
        long subtotal = 0L;

        for (CreateOrderRequest.OrderItemRequest item : request.getItems()) {
            if (item.getId() == null || item.getId().isBlank() || item.getQuantity() <= 0) {
                continue;
            }

            Optional<Map<String, Object>> productOptional = productService.getProductDetail(item.getId());
            if (productOptional.isEmpty()) {
                throw new IllegalArgumentException("Sản phẩm không tồn tại: " + item.getId());
            }

            Map<String, Object> product = productOptional.get();
            int quantity = item.getQuantity();
            long price = product.get("price") instanceof Number number ? number.longValue() : 0L;
            long lineTotal = price * quantity;
            subtotal += lineTotal;

            Map<String, Object> lineItem = new LinkedHashMap<>();
            lineItem.put("id", product.get("id"));
            lineItem.put("slug", product.get("slug"));
            lineItem.put("title", product.get("title"));
            lineItem.put("price", price);
            lineItem.put("quantity", quantity);
            lineItem.put("lineTotal", lineTotal);
            lineItems.add(lineItem);
        }

        if (lineItems.isEmpty()) {
            throw new IllegalArgumentException("Không có sản phẩm hợp lệ trong đơn hàng");
        }

        String coupon = normalizeCoupon(request.getCoupon());
        long discount = calculateDiscount(subtotal, coupon);
        long total = Math.max(0L, subtotal - discount);

        OrderEntity order = new OrderEntity();
        order.setOrderCode(resolveOrderCode(request.getOrderCode()));
        order.setCustomerName(request.getCustomerName().trim());
        order.setCustomerEmail(request.getCustomerEmail().trim());
        order.setCustomerPhone(request.getCustomerPhone().trim());
        order.setCustomerAddress(request.getCustomerAddress().trim());
        order.setCustomerNote(trimToNull(request.getCustomerNote()));
        order.setCoupon(coupon);
        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setTotal(total);
        order.setPaymentMethod(paymentMethod);
        order.setStatus(paymentMethod == PaymentMethod.COD ? OrderStatus.COD_PENDING : OrderStatus.CONFIRMED);
        order.setEmailSent(false);
        order.setCreatedAt(LocalDateTime.now());
        order.setItemsJson(writeItems(lineItems));

        OrderEntity saved = orderRepository.save(order);
        orderEmailService.sendOrderNotification(saved, lineItems);
        return saved;
    }

    public Map<String, Object> toResponse(OrderEntity order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("orderCode", order.getOrderCode());
        result.put("status", order.getStatus().name());
        result.put("paymentMethod", order.getPaymentMethod().name());
        result.put("emailSent", order.isEmailSent());
        result.put("subtotal", order.getSubtotal());
        result.put("discount", order.getDiscount());
        result.put("total", order.getTotal());
        result.put("coupon", order.getCoupon() == null ? "" : order.getCoupon());
        return result;
    }

    private void validateCustomer(CreateOrderRequest request) {
        if (isBlank(request.getCustomerName())
                || isBlank(request.getCustomerEmail())
                || isBlank(request.getCustomerPhone())
                || isBlank(request.getCustomerAddress())) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ thông tin khách hàng");
        }
    }

    private PaymentMethod parsePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return PaymentMethod.BANK_TRANSFER;
        }
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
        }
    }

    private String resolveOrderCode(String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String code = requestedCode.trim();
            if (orderRepository.findByOrderCode(code).isEmpty()) {
                return code;
            }
        }
        return generateOrderCode();
    }

    private String generateOrderCode() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        for (int attempt = 0; attempt < 10; attempt++) {
            int suffix = ThreadLocalRandom.current().nextInt(1000, 10000);
            String code = "ORC-" + datePart + "-" + suffix;
            if (orderRepository.findByOrderCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Không thể tạo mã đơn hàng");
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

    private String writeItems(List<Map<String, Object>> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể lưu sản phẩm đơn hàng", e);
        }
    }

    List<Map<String, Object>> readItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
