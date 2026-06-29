package com.example.yensao.util;

public final class ProductTypeUtil {

    private ProductTypeUtil() {
    }

    public static String normalize(String productType) {
        if (productType == null || productType.isBlank()) {
            return "";
        }

        return switch (productType) {
            case "yen-vien", "yen-rut-long" -> "yen-tinh-che";
            default -> productType;
        };
    }
}
