package com.example.yensao.util;

public final class NewsCategoryUtil {

    private NewsCategoryUtil() {
    }

    public static String normalize(String slug, String category) {
        if (slug == null || slug.isBlank()) {
            return category == null ? "" : category;
        }

        return switch (slug) {
            case "cach-che-bien-yen-sao-dung-cach",
                 "cach-bao-quan-yen-sao-sau-khi-chung",
                 "cach-phan-biet-yen-sao-that-gia" -> "Hướng dẫn";
            case "tam-nhin-thuong-hieu-yen-sao-an-thinh-nhan",
                 "y-nghia-thuong-hieu-kingnest-an-thinh-nhan",
                 "su-menh-cua-yen-sao-an-thinh-nhan",
                 "thong-diep-thuong-hieu-yen-sao-an-thinh-nhan",
                 "vi-sao-nen-lua-chon-yen-sao-kingnest-an-thinh-nhan" -> "Thương hiệu";
            case "tac-dung-lam-dep-da-tu-yen-sao",
                 "loi-ich-cua-yen-cho-tre-nho",
                 "cong-dung-tuyet-voi-cua-yen-sao" -> "Sức khỏe";
            default -> category == null ? "" : category;
        };
    }
}
