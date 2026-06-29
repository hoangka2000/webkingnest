package com.example.yensao.util;

import java.util.Map;

public final class NewsImageUtil {

    private static final Map<String, String> SLUG_TO_KEY = Map.ofEntries(
            Map.entry("cach-che-bien-yen-sao-dung-cach", "che_bien"),
            Map.entry("tam-nhin-thuong-hieu-yen-sao-an-thinh-nhan", "tam_nhin"),
            Map.entry("y-nghia-thuong-hieu-kingnest-an-thinh-nhan", "y_nghia"),
            Map.entry("su-menh-cua-yen-sao-an-thinh-nhan", "su_menh"),
            Map.entry("vi-sao-nen-lua-chon-yen-sao-kingnest-an-thinh-nhan", "chon_Kingnest"),
            Map.entry("thong-diep-thuong-hieu-yen-sao-an-thinh-nhan", "thong_diep"),
            Map.entry("tac-dung-lam-dep-da-tu-yen-sao", "lam_dep"),
            Map.entry("cach-bao-quan-yen-sao-sau-khi-chung", "bao_quan"),
            Map.entry("loi-ich-cua-yen-cho-tre-nho", "tre_em"),
            Map.entry("cach-phan-biet-yen-sao-that-gia", "phan_biet"),
            Map.entry("cong-dung-tuyet-voi-cua-yen-sao", "cong_dung")
    );

    private NewsImageUtil() {
    }

    public static String imageKey(String slug) {
        return SLUG_TO_KEY.getOrDefault(slug, slug);
    }

    public static String listImage(String slug) {
        return "/images/tin_tuc/ds_tintuc/" + imageKey(slug) + ".png";
    }

    public static String detailImage(String slug) {
        return "/images/tin_tuc/chi_tiet/" + imageKey(slug) + ".png";
    }
}
