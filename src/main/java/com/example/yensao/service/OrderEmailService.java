package com.example.yensao.service;

import com.example.yensao.entity.OrderEntity;
import com.example.yensao.entity.PaymentMethod;
import com.example.yensao.repository.OrderRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrderEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailService.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mailSender;
    private final OrderRepository orderRepository;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.notify-to:}")
    private String notifyTo;

    @Value("${app.mail.from-name:Kingnest Đơn Hàng}")
    private String fromName;

    public OrderEmailService(JavaMailSender mailSender, OrderRepository orderRepository) {
        this.mailSender = mailSender;
        this.orderRepository = orderRepository;
    }

    public void sendOrderNotification(OrderEntity order, List<Map<String, Object>> lineItems) {
        if (!isConfigured()) {
            log.warn("Chưa cấu hình Gmail (MAIL_PASSWORD). Đơn {} được lưu nhưng chưa gửi email.", order.getOrderCode());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailUsername, fromName);
            helper.setTo(notifyTo);
            helper.setSubject(buildSubject(order));
            helper.setText(buildBody(order, lineItems), false);
            mailSender.send(message);
            order.setEmailSent(true);
            orderRepository.save(order);
            log.info("Đã gửi email đơn hàng {} tới {}", order.getOrderCode(), notifyTo);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Không gửi được email đơn hàng {}", order.getOrderCode(), e);
        }
    }

    private boolean isConfigured() {
        return mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank()
                && notifyTo != null && !notifyTo.isBlank();
    }

    private String buildSubject(OrderEntity order) {
        String paymentLabel = switch (order.getPaymentMethod()) {
            case COD -> "COD";
            case MOMO -> "MoMo";
            case BANK_TRANSFER -> "Đã chuyển khoản";
        };
        return "[Kingnest] Đơn mới " + order.getOrderCode() + " - " + paymentLabel;
    }

    private String buildBody(OrderEntity order, List<Map<String, Object>> lineItems) {
        NumberFormat currency = NumberFormat.getInstance(new Locale("vi", "VN"));
        StringBuilder body = new StringBuilder();
        body.append("Có đơn hàng mới từ website Kingnest\n");
        body.append("=====================================\n\n");
        body.append("Mã đơn: ").append(order.getOrderCode()).append('\n');
        body.append("Thời gian: ").append(order.getCreatedAt().format(DATE_TIME_FORMAT)).append('\n');
        body.append("Thanh toán: ").append(paymentLabel(order.getPaymentMethod())).append('\n');
        body.append("Trạng thái: ").append(order.getStatus().name()).append("\n\n");

        body.append("--- Khách hàng ---\n");
        body.append("Họ tên: ").append(order.getCustomerName()).append('\n');
        body.append("Email: ").append(order.getCustomerEmail()).append('\n');
        body.append("SĐT: ").append(order.getCustomerPhone()).append('\n');
        body.append("Địa chỉ: ").append(order.getCustomerAddress()).append('\n');
        if (order.getCustomerNote() != null && !order.getCustomerNote().isBlank()) {
            body.append("Ghi chú: ").append(order.getCustomerNote()).append('\n');
        }
        body.append('\n');

        body.append("--- Sản phẩm ---\n");
        for (Map<String, Object> item : lineItems) {
            String title = String.valueOf(item.getOrDefault("title", "Sản phẩm"));
            int quantity = item.get("quantity") instanceof Number number ? number.intValue() : 1;
            long lineTotal = item.get("lineTotal") instanceof Number number ? number.longValue() : 0L;
            body.append("- ").append(title)
                    .append(" x").append(quantity)
                    .append(" = ").append(currency.format(lineTotal)).append(" đ\n");
        }
        body.append('\n');

        body.append("Thành tiền: ").append(currency.format(order.getSubtotal())).append(" đ\n");
        if (order.getDiscount() != null && order.getDiscount() > 0) {
            body.append("Giảm giá");
            if (order.getCoupon() != null && !order.getCoupon().isBlank()) {
                body.append(" (").append(order.getCoupon()).append(')');
            }
            body.append(": -").append(currency.format(order.getDiscount())).append(" đ\n");
        }
        body.append("TỔNG THANH TOÁN: ").append(currency.format(order.getTotal())).append(" đ\n");
        return body.toString();
    }

    private String paymentLabel(PaymentMethod method) {
        return switch (method) {
            case COD -> "Thanh toán khi nhận hàng (COD)";
            case MOMO -> "MoMo";
            case BANK_TRANSFER -> "Khách xác nhận đã chuyển khoản";
        };
    }
}
