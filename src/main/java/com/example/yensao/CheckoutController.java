package com.example.yensao;

import com.example.yensao.service.CheckoutService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping(value = "/checkout", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> checkout(
            @RequestParam String products,
            @RequestParam(required = false) String coupon) {
        return checkoutService.buildCheckout(products, coupon);
    }

    @GetMapping("/checkout/redirect")
    public ResponseEntity<Void> checkoutRedirect(
            @RequestParam String products,
            @RequestParam(required = false) String coupon) {

        return ResponseEntity
                .status(302)
                .location(checkoutService.buildCheckoutPageUri(products, coupon))
                .build();
    }
}
