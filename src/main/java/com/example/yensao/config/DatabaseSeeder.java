package com.example.yensao.config;

import com.example.yensao.entity.NewsArticleEntity;
import com.example.yensao.entity.ProductEntity;
import com.example.yensao.repository.NewsArticleRepository;
import com.example.yensao.repository.ProductRepository;
import com.example.yensao.service.NewsContentLoader;
import com.example.yensao.service.ProductContentLoader;
import com.example.yensao.service.ProductDocxData;
import com.example.yensao.service.ProductImageService;
import com.example.yensao.util.NewsCategoryUtil;
import com.example.yensao.util.NewsImageUtil;
import com.example.yensao.util.ProductTypeUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsContentLoader newsContentLoader;
    private final ProductContentLoader productContentLoader;
    private final ProductImageService productImageService;

    public DatabaseSeeder(ProductRepository productRepository,
                          NewsArticleRepository newsArticleRepository,
                          NewsContentLoader newsContentLoader,
                          ProductContentLoader productContentLoader,
                          ProductImageService productImageService) {
        this.productRepository = productRepository;
        this.newsArticleRepository = newsArticleRepository;
        this.newsContentLoader = newsContentLoader;
        this.productContentLoader = productContentLoader;
        this.productImageService = productImageService;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            productRepository.saveAll(buildProducts());
        }
        if (newsArticleRepository.count() == 0) {
            newsArticleRepository.saveAll(buildNewsArticles());
        }
        normalizeProductTypes();
        syncProductsFromDocx();
        syncYenChungFromDocx();
        syncProductImages();
        normalizeNewsArticles();
    }

    private void syncProductImages() {
        productRepository.findAll().forEach(product -> {
            productImageService.resolve(product.getSlug()).ifPresent(images -> {
                boolean changed = false;

                if (images.listImage() != null && !images.listImage().equals(product.getImage())) {
                    product.setImage(images.listImage());
                    changed = true;
                }

                if (!images.gallery().equals(nullSafeList(product.getGallery()))) {
                    product.setGallery(images.gallery());
                    changed = true;
                }

                if (changed) {
                    productRepository.save(product);
                }
            });
        });
    }

    private void normalizeNewsArticles() {
        newsArticleRepository.findAll().forEach(article -> {
            boolean changed = false;

            String normalizedCategory = NewsCategoryUtil.normalize(article.getSlug(), article.getCategory());
            if (!normalizedCategory.equals(article.getCategory())) {
                article.setCategory(normalizedCategory);
                changed = true;
            }

            String listImage = NewsImageUtil.listImage(article.getSlug());
            if (!listImage.equals(article.getImage())) {
                article.setImage(listImage);
                changed = true;
            }

            Optional<String> contentHtml = newsContentLoader.loadContentHtml(article.getSlug());
            if (contentHtml.isPresent() && !contentHtml.get().equals(article.getContentHtml())) {
                article.setContentHtml(contentHtml.get());
                changed = true;
            }

            if (changed) {
                newsArticleRepository.save(article);
            }
        });
    }

    private void normalizeProductTypes() {
        productRepository.findAll().forEach(product -> {
            String normalizedType = ProductTypeUtil.normalize(product.getProductType());
            if (!normalizedType.equals(product.getProductType())) {
                product.setProductType(normalizedType);
                productRepository.save(product);
            }
        });
    }

    private void syncYenChungFromDocx() {
        for (Map.Entry<String, ProductDocxData> entry : productContentLoader.loadAllYenChungParsed().entrySet()) {
            String slug = entry.getKey();
            ProductDocxData data = entry.getValue();

            if (data.getPrice() == null) {
                Long defaultPrice = docxDefaults(slug).price();
                if (defaultPrice != null) {
                    data.setPrice(defaultPrice);
                }
            }

            ProductEntity product = productRepository.findBySlug(slug)
                    .orElseGet(() -> createProductFromDocx(slug, data));

            boolean changed = applyDocxData(product, data);
            if (changed || product.getId() == null) {
                productRepository.save(product);
            }
        }
    }

    private void syncProductsFromDocx() {
        for (String slug : productContentLoader.getDocxSlugs()) {
            Optional<ProductDocxData> dataOptional = productContentLoader.loadParsed(slug);
            if (dataOptional.isEmpty()) {
                continue;
            }

            ProductDocxData data = dataOptional.get();
            ProductEntity product = productRepository.findBySlug(slug)
                    .orElseGet(() -> createProductFromDocx(slug, data));

            boolean changed = applyDocxData(product, data);
            if (changed || product.getId() == null) {
                productRepository.save(product);
            }
        }
    }

    private ProductEntity createProductFromDocx(String slug, ProductDocxData data) {
        DocxProductDefaults defaults = docxDefaults(slug);
        ProductEntity entity = new ProductEntity();
        entity.setSlug(slug);
        entity.setCategory(defaults.category());
        entity.setProductType(defaults.productType());
        entity.setImage(defaults.image());
        entity.setGallery(List.of(defaults.image()));
        entity.setNeed(defaults.need());
        entity.setStatus(defaults.status());
        entity.setBadge(defaults.badge());
        entity.setPrice(data.getPrice() != null ? data.getPrice() : defaults.price());
        return entity;
    }

    private boolean applyDocxData(ProductEntity product, ProductDocxData data) {
        boolean changed = false;

        if (data.getTitle() != null && !data.getTitle().equals(product.getTitle())) {
            product.setTitle(data.getTitle());
            changed = true;
        }

        String shortDesc = data.getShortDesc();
        if (shortDesc != null && !shortDesc.equals(product.getShortDesc())) {
            product.setShortDesc(shortDesc);
            changed = true;
        }

        if (data.getPrice() != null && !data.getPrice().equals(product.getPrice())) {
            product.setPrice(data.getPrice());
            changed = true;
        }

        if (!data.benefitsOrEmpty().equals(nullSafeList(product.getBenefits()))) {
            product.setBenefits(data.benefitsOrEmpty());
            changed = true;
        }

        if (!data.usageOrEmpty().equals(nullSafeList(product.getUsage()))) {
            product.setUsage(data.usageOrEmpty());
            changed = true;
        }

        if (!data.specsOrEmpty().equals(nullSafeMap(product.getSpecs()))) {
            product.setSpecs(data.specsOrEmpty());
            changed = true;
        }

        if (!data.descriptionOrEmpty().equals(nullSafeList(product.getDescription()))) {
            product.setDescription(data.descriptionOrEmpty());
            changed = true;
        }

        if (!data.highlightsOrEmpty().equals(nullSafeList(product.getHighlights()))) {
            product.setHighlights(data.highlightsOrEmpty());
            changed = true;
        }

        if (data.getContentHtml() != null && !data.getContentHtml().equals(product.getContentHtml())) {
            product.setContentHtml(data.getContentHtml());
            changed = true;
        }

        return changed;
    }

    private List<String> nullSafeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, String> nullSafeMap(Map<String, String> values) {
        return values == null ? Map.of() : values;
    }

    private DocxProductDefaults docxDefaults(String slug) {
        return switch (slug) {
            case "chan-yen-ria-sach" -> new DocxProductDefaults(
                    "Chân rìa tinh", "yen-tinh-che",
                    "/images/yentinh/chan_ria_yen.png",
                    List.of("daily", "family", "elder"), List.of("best", "premium"), null, null);
            case "chan-yen-sach-nho" -> new DocxProductDefaults(
                    "Chân rìa tinh", "yen-tinh-che",
                    "/images/yentinh/chan_tinh_nho.png",
                    List.of("daily", "family"), List.of("best"), null, null);
            case "yen-tinh-che-gan-tuyet" -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/tinh_che_L1.png",
                    List.of("gift", "premium"), List.of("premium"), "Cao cấp", null);
            case "yen-tinh-che-hoa-hong" -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/hoa_hong.png",
                    List.of("gift", "family"), List.of("premium"), null, null);
            case "hong-yen-tinh-che" -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/hoa_hong.png",
                    List.of("gift", "premium"), List.of("premium"), "Cao cấp", null);
            case "yen-tinh-che-xo-roi-dap-chan" -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/keo_soi.png",
                    List.of("daily", "family"), List.of("best"), null, null);
            case "yen-tinh-che-soi-ngan-20-to" -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/keo_soi.png",
                    List.of("gift", "family"), List.of("best"), null, null);
            case "yen-tho-vip" -> new DocxProductDefaults(
                    "Yến thô", "yen-tho",
                    "/images/sanpham/yen-tho-nguyen-to.jpg",
                    List.of("family", "premium"), List.of("premium"), null, null);
            case "yen-tinh-che-loai-1" -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/tinh_che_L1.png",
                    List.of("gift", "premium"), List.of("premium"), "Cao cấp nhất", null);
            case "yen-tinh-che-loai-2" -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/tinh_che_L1.png",
                    List.of("daily", "family"), List.of("best"), null, null);
            case "yen-vien-xu-baby" -> new DocxProductDefaults(
                    "Yến viên", "yen-tinh-che",
                    "/images/yentinh/keo_soi.png",
                    List.of("daily", "family"), List.of("new"), null, null);
            case "yen-chung-dong-trung-ha-thao-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/dong_trung_ha_thao.png",
                    List.of("gift", "elder", "tired"), List.of("premium"), null, 35000L);
            case "yen-chung-duong-phen-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/duong_phen.png",
                    List.of("daily", "family"), List.of("new"), null, 35000L);
            case "yen-chung-nhan-sam-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/nhan_sam.png",
                    List.of("daily", "family", "tired"), List.of("best", "new"), "Bán chạy", 35000L);
            case "yen-chung-tao-do-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/tao_do.png",
                    List.of("daily", "family", "elder"), List.of("best"), null, 35000L);
            case "yen-chung-long-nhan-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/long_nhan.png",
                    List.of("daily", "family"), List.of("new"), null, 35000L);
            case "yen-chung-hat-chia-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/hat_chia.png",
                    List.of("daily", "diet"), List.of("best"), null, 35000L);
            case "yen-chung-hat-sen-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/hat_sen.png",
                    List.of("daily", "elder"), List.of("new"), null, 35000L);
            case "yen-chung-saffron-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/saffron.png",
                    List.of("gift", "daily"), List.of("premium", "new"), null, 35000L);
            case "yen-chung-vi-gung-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/vi_gung.png",
                    List.of("daily", "elder"), List.of("best"), null, 35000L);
            case "yen-chung-cho-tre-em-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/cho_tre_em.png",
                    List.of("daily", "family"), List.of("new"), null, 35000L);
            case "yen-chung-tam-vi-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/tam_vi.png",
                    List.of("daily", "family"), List.of("new"), null, 35000L);
            case "yen-chung-an-kieng-dong-trung-ha-thao-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/an_kieng_dtht.png",
                    List.of("daily", "diet"), List.of("premium"), null, 35000L);
            case "yen-chung-duong-an-kieng-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/duong_an_kieng.png",
                    List.of("daily", "diet"), List.of("new"), null, 35000L);
            case "yen-chung-nhung-huou-6-hu-70ml" -> new DocxProductDefaults(
                    "Yến chưng", "yen-chung",
                    "/images/yenchung/nhung_huou.png",
                    List.of("gift", "elder"), List.of("premium"), null, 35000L);
            default -> new DocxProductDefaults(
                    "Yến tinh chế", "yen-tinh-che",
                    "/images/yentinh/tinh_che_L1.png",
                    List.of("daily", "family"), List.of("best"), null, null);
        };
    }

    private record DocxProductDefaults(
            String category,
            String productType,
            String image,
            List<String> need,
            List<String> status,
            String badge,
            Long price
    ) {
    }

    private List<ProductEntity> buildProducts() {
        return List.of(
                product("hop-qua-yen-chung-thuong-hang-kingnest-6-hu",
                        "Hộp quà yến chưng Thượng Hạng Kingnest (6 hũ)",
                        "Bộ quà tặng yến chưng cao cấp, phù hợp biếu tặng gia đình, đối tác và người thân.",
                        "Hộp quà", 2880000L, "/images/sanpham/hop-qua-thuong-hang.jpg",
                        List.of("/images/sanpham/hop-qua-thuong-hang.jpg", "/images/yenchung/dong_trung_ha_thao.png", "/images/yenchung/nhan_sam.png"),
                        List.of("Phù hợp biếu tặng gia đình, đối tác và người thân.", "Thiết kế hộp quà sang trọng, chỉn chu.", "Tiện dùng, dễ bảo quản, phù hợp nhiều dịp."),
                        List.of("Dùng trực tiếp theo hướng dẫn trên bao bì.", "Bảo quản nơi khô ráo, thoáng mát hoặc theo khuyến nghị của sản phẩm."),
                        Map.of("Tên sản phẩm", "Hộp quà yến chưng Thượng Hạng Kingnest", "Quy cách", "6 hũ", "Thương hiệu", "Kingnest", "Phù hợp", "Biếu tặng, chăm sóc sức khỏe gia đình"),
                        List.of("Bộ quà tặng yến chưng cao cấp, phù hợp biếu tặng gia đình, đối tác và người thân."),
                        List.of("Đóng gói sang trọng", "Dễ tặng, dễ sử dụng", "Phù hợp nhiều nhóm khách hàng"),
                        "hop-qua", List.of("gift", "family", "elder"), List.of("best", "premium", "new"), "Bán chạy"),

                product("hop-qua-yen-chung-dong-trung-ha-thao-6-hu",
                        "Hộp quà yến chưng Đông Trùng Hạ Thảo (6 hũ)",
                        "Hộp quà sang trọng kết hợp yến chưng và đông trùng hạ thảo, phù hợp chăm sóc sức khỏe.",
                        "Hộp quà", 2450000L, "/images/sanpham/hop-qua-dong-trung.jpg",
                        List.of("/images/sanpham/hop-qua-dong-trung.jpg", "/images/yenchung/dong_trung_ha_thao.png"),
                        List.of("Kết hợp yến chưng và đông trùng hạ thảo.", "Phù hợp làm quà chăm sóc sức khỏe.", "Hương vị sang trọng, dễ dùng."),
                        List.of("Dùng trực tiếp theo hướng dẫn trên bao bì.", "Lắc nhẹ trước khi dùng nếu cần."),
                        Map.of("Tên sản phẩm", "Hộp quà yến chưng Đông Trùng Hạ Thảo", "Quy cách", "6 hũ", "Thương hiệu", "Kingnest"),
                        List.of("Hộp quà sang trọng kết hợp yến chưng và đông trùng hạ thảo, phù hợp chăm sóc sức khỏe."),
                        List.of("Thiết kế đẹp", "Phù hợp biếu tặng", "Dòng cao cấp"),
                        "hop-qua", List.of("gift", "elder", "tired"), List.of("best", "premium"), "Bán chạy"),

                product("hop-qua-yen-chung-nhan-sam-6-hu",
                        "Hộp quà yến chưng Nhân Sâm (6 hũ)",
                        "Hộp quà yến chưng nhân sâm thanh nhẹ, thiết kế đẹp, phù hợp làm quà biếu.",
                        "Hộp quà", 2150000L, "/images/sanpham/hop-qua-nhan-sam.jpg",
                        List.of("/images/sanpham/hop-qua-nhan-sam.jpg", "/images/yenchung/nhan_sam.png"),
                        List.of("Hương nhân sâm ấm nhẹ.", "Phù hợp làm quà biếu.", "Thiết kế đẹp, dễ chọn."),
                        List.of("Dùng trực tiếp theo hướng dẫn trên bao bì."),
                        Map.of("Tên sản phẩm", "Hộp quà yến chưng Nhân Sâm", "Quy cách", "6 hũ", "Thương hiệu", "Kingnest"),
                        List.of("Hộp quà yến chưng nhân sâm thanh nhẹ, thiết kế đẹp, phù hợp làm quà biếu."),
                        List.of("Dễ dùng", "Mẫu hộp sang", "Phù hợp gia đình"),
                        "hop-qua", List.of("gift", "elder", "family"), List.of("best"), "Bán chạy"),

                product("yen-chung-nhan-sam-6-hu-70ml",
                        "Yến chưng Nhân Sâm (6 hũ x 70ml)",
                        "Dòng yến chưng tiện dùng mỗi ngày, kết hợp hương nhân sâm ấm nhẹ.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-nhan-sam.jpg",
                        List.of("/images/sanpham/yen-chung-nhan-sam.jpg", "/images/yenchung/nhan_sam.png"),
                        List.of("Tiện dùng mỗi ngày.", "Hương nhân sâm ấm nhẹ.", "Phù hợp gia đình."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Nhân Sâm", "Quy cách", "6 hũ x 70ml", "Thương hiệu", "Kingnest"),
                        List.of("Dòng yến chưng tiện dùng mỗi ngày, kết hợp hương nhân sâm ấm nhẹ."),
                        List.of("Tiện lợi", "Dễ dùng", "Phù hợp dùng hằng ngày"),
                        "yen-chung", List.of("daily", "family", "tired"), List.of("best", "new"), "Bán chạy"),

                product("yen-tinh-che-cao-cap-100g",
                        "Yến tinh chế cao cấp (100g)",
                        "Yến tinh chế sạch, tiện chế biến, phù hợp cho gia đình sử dụng lâu dài.",
                        "Yến tinh chế", 2750000L, "/images/sanpham/yen-tinh-che-cao-cap.jpg",
                        List.of("/images/sanpham/yen-tinh-che-cao-cap.jpg", "/images/yentinh/tinh_che_L1.png"),
                        List.of("Yến tinh chế sạch, tiện chế biến.", "Phù hợp cho gia đình sử dụng lâu dài.", "Dễ chưng và dễ kết hợp nguyên liệu."),
                        List.of("Ngâm yến trong nước sạch đến khi nở.", "Chưng cách thủy cùng đường phèn hoặc nguyên liệu yêu thích."),
                        Map.of("Tên sản phẩm", "Yến tinh chế cao cấp", "Quy cách", "100g", "Thành phần", "Yến sào tinh chế"),
                        List.of("Yến tinh chế sạch, tiện chế biến, phù hợp cho gia đình sử dụng lâu dài."),
                        List.of("Tiện chế biến", "Sợi yến sạch", "Phù hợp chưng tại nhà"),
                        "yen-tinh-che", List.of("gift", "family", "premium"), List.of("premium"), "Cao cấp nhất"),

                product("yen-tho-nguyen-to-100g",
                        "Yến thô nguyên tổ (100g)",
                        "Yến thô nguyên tổ giữ trọn kết cấu tự nhiên, phù hợp người thích tự sơ chế.",
                        "Yến thô", 2250000L, "/images/sanpham/yen-tho-nguyen-to.jpg",
                        List.of("/images/sanpham/yen-tho-nguyen-to.jpg", "/images/yentinh/hoa_hong.png"),
                        List.of("Giữ trọn kết cấu tự nhiên.", "Phù hợp người thích tự sơ chế.", "Có thể dùng chưng yến tại nhà."),
                        List.of("Làm sạch lông và tạp chất trước khi chưng.", "Ngâm nở, sau đó chưng cách thủy."),
                        Map.of("Tên sản phẩm", "Yến thô nguyên tổ", "Quy cách", "100g", "Thành phần", "Tổ yến thô"),
                        List.of("Yến thô nguyên tổ giữ trọn kết cấu tự nhiên, phù hợp người thích tự sơ chế."),
                        List.of("Nguyên bản", "Tự sơ chế theo nhu cầu", "Phù hợp người sành yến"),
                        "yen-tho", List.of("family", "daily"), List.of("best"), "Bán chạy"),

                product("yen-chung-duong-phen-6-hu-70ml",
                        "Yến chưng Đường Phèn (6 hũ x 70ml)",
                        "Vị truyền thống dễ dùng, thanh nhẹ, phù hợp nhiều thành viên trong gia đình.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-duong-phen.jpg",
                        List.of("/images/sanpham/yen-chung-duong-phen.jpg", "/images/yenchung/duong_phen.png"),
                        List.of("Vị truyền thống dễ dùng.", "Thanh nhẹ, phù hợp nhiều thành viên.", "Tiện dùng hằng ngày."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Đường Phèn", "Quy cách", "6 hũ x 70ml", "Thương hiệu", "Kingnest"),
                        List.of("Vị truyền thống dễ dùng, thanh nhẹ, phù hợp nhiều thành viên trong gia đình."),
                        List.of("Vị truyền thống", "Dễ dùng", "Tiện lợi"),
                        "yen-chung", List.of("daily", "family"), List.of("new"), null),

                product("yen-chung-tao-do-6-hu-70ml",
                        "Yến chưng Táo Đỏ (6 hũ x 70ml)",
                        "Yến chưng táo đỏ vị ngọt dịu, dễ uống, phù hợp dùng hằng ngày.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-tao-do.jpg",
                        List.of("/images/sanpham/yen-chung-tao-do.jpg", "/images/yenchung/tao_do.png"),
                        List.of("Vị ngọt dịu.", "Dễ uống, phù hợp dùng hằng ngày.", "Phù hợp gia đình."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Táo Đỏ", "Quy cách", "6 hũ x 70ml"),
                        List.of("Yến chưng táo đỏ vị ngọt dịu, dễ uống, phù hợp dùng hằng ngày."),
                        List.of("Vị dễ uống", "Tiện dùng", "Phù hợp gia đình"),
                        "yen-chung", List.of("daily", "family", "elder"), List.of("best"), null),

                product("yen-chung-hat-sen-6-hu-70ml",
                        "Yến chưng Hạt Sen (6 hũ x 70ml)",
                        "Hương hạt sen thanh mát, phù hợp dùng buổi tối hoặc làm quà chăm sóc.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-hat-sen.jpg",
                        List.of("/images/sanpham/yen-chung-hat-sen.jpg", "/images/yenchung/hat_sen.png"),
                        List.of("Hương hạt sen thanh mát.", "Phù hợp dùng buổi tối hoặc làm quà chăm sóc.", "Dễ dùng."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Hạt Sen", "Quy cách", "6 hũ x 70ml"),
                        List.of("Hương hạt sen thanh mát, phù hợp dùng buổi tối hoặc làm quà chăm sóc."),
                        List.of("Thanh mát", "Dễ dùng", "Phù hợp biếu tặng"),
                        "yen-chung", List.of("daily", "elder"), List.of("new"), null),

                product("yen-chung-hat-chia-6-hu-70ml",
                        "Yến chưng Hạt Chia (6 hũ x 70ml)",
                        "Kết hợp hạt chia tiện dùng, phù hợp người thích sản phẩm thanh nhẹ.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-hat-chia.jpg",
                        List.of("/images/sanpham/yen-chung-hat-chia.jpg", "/images/yenchung/hat_chia.png"),
                        List.of("Kết hợp hạt chia tiện dùng.", "Phù hợp người thích sản phẩm thanh nhẹ.", "Dùng hằng ngày tiện lợi."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Hạt Chia", "Quy cách", "6 hũ x 70ml"),
                        List.of("Kết hợp hạt chia tiện dùng, phù hợp người thích sản phẩm thanh nhẹ."),
                        List.of("Thanh nhẹ", "Tiện lợi", "Dễ dùng"),
                        "yen-chung", List.of("daily", "diet"), List.of("best"), null),

                product("yen-chung-saffron-6-hu-70ml",
                        "Yến chưng Saffron (6 hũ x 70ml)",
                        "Dòng yến chưng cao cấp kết hợp saffron, thích hợp biếu tặng sang trọng.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-saffron.jpg",
                        List.of("/images/sanpham/yen-chung-saffron.jpg", "/images/yenchung/saffron.png"),
                        List.of("Kết hợp saffron tinh tế.", "Thích hợp biếu tặng sang trọng.", "Dễ dùng."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Saffron", "Quy cách", "6 hũ x 70ml"),
                        List.of("Dòng yến chưng cao cấp kết hợp saffron, thích hợp biếu tặng sang trọng."),
                        List.of("Sang trọng", "Phù hợp biếu tặng", "Hương vị tinh tế"),
                        "yen-chung", List.of("gift", "daily"), List.of("premium", "new"), null),

                product("yen-chung-long-nhan-6-hu-70ml",
                        "Yến chưng Long Nhãn (6 hũ x 70ml)",
                        "Hương long nhãn dịu ngọt, phù hợp chăm sóc sức khỏe gia đình.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-long-nhan.jpg",
                        List.of("/images/sanpham/yen-chung-long-nhan.jpg", "/images/yenchung/long_nhan.png"),
                        List.of("Hương long nhãn dịu ngọt.", "Phù hợp chăm sóc sức khỏe gia đình.", "Dễ thưởng thức."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Long Nhãn", "Quy cách", "6 hũ x 70ml"),
                        List.of("Hương long nhãn dịu ngọt, phù hợp chăm sóc sức khỏe gia đình."),
                        List.of("Dịu ngọt", "Dễ dùng", "Phù hợp gia đình"),
                        "yen-chung", List.of("daily", "family"), List.of("new"), null),

                product("yen-chung-vi-gung-6-hu-70ml",
                        "Yến chưng Vị Gừng (6 hũ x 70ml)",
                        "Vị gừng ấm nhẹ, phù hợp người thích hương vị truyền thống.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-vi-gung.jpg",
                        List.of("/images/sanpham/yen-chung-vi-gung.jpg", "/images/yenchung/vi_gung.png"),
                        List.of("Vị gừng ấm nhẹ.", "Phù hợp người thích hương vị truyền thống.", "Dễ dùng."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Vị Gừng", "Quy cách", "6 hũ x 70ml"),
                        List.of("Vị gừng ấm nhẹ, phù hợp người thích hương vị truyền thống."),
                        List.of("Vị ấm nhẹ", "Truyền thống", "Dễ sử dụng"),
                        "yen-chung", List.of("daily", "elder"), List.of("best"), null),

                product("yen-chung-duong-an-kieng-6-hu-70ml",
                        "Yến chưng Đường Ăn Kiêng (6 hũ x 70ml)",
                        "Phiên bản ít đường, phù hợp người cần kiểm soát độ ngọt.",
                        "Yến chưng", 35000L, "/images/sanpham/yen-chung-duong-an-kieng.jpg",
                        List.of("/images/sanpham/yen-chung-duong-an-kieng.jpg", "/images/yenchung/duong_an_kieng.png"),
                        List.of("Phiên bản ít đường.", "Phù hợp người cần kiểm soát độ ngọt.", "Tiện dùng hằng ngày."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Đường Ăn Kiêng", "Quy cách", "6 hũ x 70ml"),
                        List.of("Phiên bản ít đường, phù hợp người cần kiểm soát độ ngọt."),
                        List.of("Ít đường", "Dễ dùng", "Phù hợp ăn kiêng"),
                        "yen-chung", List.of("daily", "diet"), List.of("new"), null),

                product("yen-chung-an-kieng-dong-trung-ha-thao-6-hu-70ml",
                        "Yến chưng Ăn Kiêng Đông Trùng Hạ Thảo (6 hũ x 70ml)",
                        "Dòng ăn kiêng kết hợp đông trùng hạ thảo cao cấp, thanh nhẹ, phù hợp người hạn chế đường.",
                        "Yến chưng", 35000L, "/images/yenchung/an_kieng_dtht.png",
                        List.of("/images/yenchung/an_kieng_dtht.png"),
                        List.of("Kết hợp đông trùng hạ thảo cao cấp.", "Thanh nhẹ, phù hợp người hạn chế đường.", "Tiện dùng hằng ngày."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Ăn Kiêng Đông Trùng Hạ Thảo", "Quy cách", "6 hũ x 70ml", "Thương hiệu", "Kingnest"),
                        List.of("Dòng ăn kiêng kết hợp đông trùng hạ thảo cao cấp, thanh nhẹ, phù hợp người hạn chế đường."),
                        List.of("Cao cấp", "Ít đường", "Dễ dùng"),
                        "yen-chung", List.of("daily", "diet"), List.of("premium"), null),

                product("yen-rut-long-cao-cap-100g",
                        "Yến rút lông cao cấp (100g)",
                        "Tổ yến rút lông sạch, giữ dáng đẹp, phù hợp biếu tặng và sử dụng cao cấp.",
                        "Yến rút lông", 3200000L, "/images/sanpham/yen-rut-long-cao-cap.jpg",
                        List.of("/images/sanpham/yen-rut-long-cao-cap.jpg", "/images/yentinh/hoa_hong.png"),
                        List.of("Tổ yến rút lông sạch, giữ dáng đẹp.", "Phù hợp biếu tặng và sử dụng cao cấp.", "Tiện chưng tại nhà."),
                        List.of("Ngâm nở trước khi chưng.", "Chưng cách thủy 20–25 phút tùy lượng yến."),
                        Map.of("Tên sản phẩm", "Yến rút lông cao cấp", "Quy cách", "100g"),
                        List.of("Tổ yến rút lông sạch, giữ dáng đẹp, phù hợp biếu tặng và sử dụng cao cấp."),
                        List.of("Giữ dáng tổ", "Sạch lông", "Dòng cao cấp"),
                        "yen-tinh-che", List.of("gift", "family"), List.of("premium"), null),

                product("yen-vien-ruby-cao-cap",
                        "Yến viên Ruby cao cấp",
                        "Dòng yến viên tiện lợi, đóng gói đẹp, phù hợp làm quà tặng.",
                        "Yến viên", 1650000L, "/images/sanpham/yen-vien-ruby.jpg",
                        List.of("/images/sanpham/yen-vien-ruby.jpg", "/images/yentinh/keo_soi.png"),
                        List.of("Dòng yến viên tiện lợi.", "Đóng gói đẹp, phù hợp làm quà tặng.", "Dễ bảo quản."),
                        List.of("Sử dụng theo hướng dẫn trên bao bì."),
                        Map.of("Tên sản phẩm", "Yến viên Ruby cao cấp", "Thương hiệu", "Kingnest"),
                        List.of("Dòng yến viên tiện lợi, đóng gói đẹp, phù hợp làm quà tặng."),
                        List.of("Tiện lợi", "Đóng gói đẹp", "Phù hợp quà tặng"),
                        "yen-tinh-che", List.of("gift", "family"), List.of("new", "premium"), null),

                product("chan-yen-ria-sach",
                        "Chân yến rìa sạch",
                        "Yến chân rìa sạch là phần chân và rìa của tổ yến, sợi dày, dai, giàu dưỡng chất và phù hợp chưng yến hằng ngày.",
                        "Chân rìa tinh", 2800000L, "https://yensaoanthinhnhan.com/uploads/Categories/IMG_3947-%281%29.JPG",
                        List.of(
                                "https://yensaoanthinhnhan.com/uploads/Categories/IMG_3947-%281%29.JPG",
                                "https://yensaoanthinhnhan.com/uploads/AttachmentsProduct/jz27JiiUJbs%3D/2e7db819-742d-408a-9c66-fede4c0c44a5.jpg",
                                "https://yensaoanthinhnhan.com/uploads/AttachmentsProduct/jz27JiiUJbs%3D/ec8568c9-1432-4cc4-8db8-58a504cdff1b.jpg"
                        ),
                        List.of(
                                "Bổ sung protein, khoáng chất và 18 loại axit amin quý hiếm.",
                                "Tăng sức đề kháng, phục hồi sức khỏe sau ốm hoặc sau sinh.",
                                "Hỗ trợ làm đẹp da, cải thiện giấc ngủ, giảm căng thẳng.",
                                "Phù hợp cho nhiều đối tượng, đặc biệt là người lớn tuổi, phụ nữ, trẻ em và người bận rộn."
                        ),
                        List.of(
                                "Ngâm yến trong nước sạch 15–30 phút cho yến nở đều.",
                                "Chưng cách thủy 20–25 phút cùng đường phèn, táo đỏ, đông trùng hoặc nhung hươu tùy sở thích.",
                                "Bảo quản nơi khô ráo. Sau khi ngâm, nếu chưa sử dụng, nên bảo quản ngăn mát và dùng trong 24 giờ."
                        ),
                        Map.of(
                                "Tên sản phẩm", "Yến chân rìa sạch",
                                "Thành phần", "100% tổ yến sào thiên nhiên nguyên chất",
                                "Quy cách", "50g - 100g",
                                "Xuất xứ", "TDP Nghĩa Lộc, Phường Bắc Cam Ranh, Khánh Hòa, Việt Nam",
                                "Bảo quản", "Nơi khô ráo, thoáng mát, tránh ánh nắng trực tiếp",
                                "Hạn sử dụng", "12 tháng kể từ ngày sản xuất"
                        ),
                        List.of(
                                "Yến chân rìa sạch là phần chân và rìa của tổ yến – nơi sợi yến bám chắc vào vách tổ, có hàm lượng dưỡng chất cao, sợi dày và dai hơn so với phần thân tổ.",
                                "Sản phẩm được làm sạch hoàn toàn thủ công, giữ nguyên sợi yến tự nhiên, không tẩy trắng, không pha trộn, đảm bảo chất lượng và độ tinh khiết tuyệt đối.",
                                "Đây là lựa chọn kinh tế, bổ dưỡng và tiện lợi, phù hợp dùng chưng yến hằng ngày cho cả gia đình mà vẫn đảm bảo đầy đủ giá trị dinh dưỡng như tổ yến nguyên."
                        ),
                        List.of(
                                "100% yến thật nguyên chất, sạch kỹ, không lẫn tạp chất.",
                                "Sợi yến dày, dai, giàu dinh dưỡng.",
                                "Giá thành hợp lý hơn so với tổ yến nguyên nhưng dưỡng chất tương đương.",
                                "Dễ chưng nở, tiết kiệm thời gian chế biến."
                        ),
                        "yen-tinh-che", List.of("daily", "family", "elder"), List.of("best", "premium"), null),

                product("yen-chung-dong-trung-ha-thao-6-hu-70ml",
                        "Yến chưng Đông Trùng Hạ Thảo (6 hũ x 70ml)",
                        "Dòng yến chưng cao cấp kết hợp đông trùng hạ thảo, phù hợp chăm sóc sức khỏe và biếu tặng.",
                        "Yến chưng", 35000L, "/images/yenchung/dong_trung_ha_thao.png",
                        List.of("/images/yenchung/dong_trung_ha_thao.png"),
                        List.of("Kết hợp yến chưng và đông trùng hạ thảo.", "Phù hợp biếu tặng và chăm sóc sức khỏe.", "Thiết kế sang trọng."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng.", "Lắc nhẹ trước khi mở nắp."),
                        Map.of("Tên sản phẩm", "Yến chưng Đông Trùng Hạ Thảo", "Quy cách", "6 hũ x 70ml", "Thương hiệu", "Kingnest"),
                        List.of("Dòng yến chưng cao cấp kết hợp đông trùng hạ thảo, phù hợp chăm sóc sức khỏe và biếu tặng."),
                        List.of("Cao cấp", "Dễ dùng", "Phù hợp làm quà"),
                        "yen-chung", List.of("gift", "elder", "tired"), List.of("premium"), null),

                product("yen-chung-cho-tre-em-6-hu-70ml",
                        "Yến chưng dành cho trẻ em (6 hũ x 70ml)",
                        "Yến chưng vị thanh nhẹ, phù hợp cho gia đình có trẻ em.",
                        "Yến chưng", 35000L, "/images/yenchung/cho_tre_em.png",
                        List.of("/images/yenchung/cho_tre_em.png"),
                        List.of("Vị thanh nhẹ, dễ dùng.", "Phù hợp chăm sóc bé yêu.", "Tiện lợi cho gia đình."),
                        List.of("Dùng lượng phù hợp theo nhu cầu.", "Có thể làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng dành cho trẻ em", "Quy cách", "6 hũ x 70ml", "Thương hiệu", "Kingnest"),
                        List.of("Yến chưng vị thanh nhẹ, phù hợp cho gia đình có trẻ em."),
                        List.of("Vị nhẹ", "Dễ dùng", "Tiện lợi"),
                        "yen-chung", List.of("daily", "family"), List.of("new"), null),

                product("yen-chung-tam-vi-6-hu-70ml",
                        "Yến chưng Tam Vị (6 hũ x 70ml)",
                        "Yến chưng Tam Vị kết hợp nhiều nguyên liệu quen thuộc, dễ thưởng thức.",
                        "Yến chưng", 35000L, "/images/yenchung/tam_vi.png",
                        List.of("/images/yenchung/tam_vi.png"),
                        List.of("Kết hợp táo đỏ, hạt sen, kỷ tử.", "Vị thanh ngọt dễ dùng.", "Phù hợp dùng hằng ngày."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Tam Vị", "Quy cách", "6 hũ x 70ml", "Thương hiệu", "Kingnest"),
                        List.of("Yến chưng Tam Vị kết hợp nhiều nguyên liệu quen thuộc, dễ thưởng thức."),
                        List.of("Nhiều vị", "Dễ dùng", "Phù hợp gia đình"),
                        "yen-chung", List.of("daily", "family"), List.of("new"), null),

                product("yen-chung-nhung-huou-6-hu-70ml",
                        "Yến chưng Nhung Hươu (6 hũ x 70ml)",
                        "Yến chưng Nhung Hươu là dòng cao cấp, phù hợp biếu tặng và chăm sóc sức khỏe.",
                        "Yến chưng", 35000L, "/images/yenchung/nhung_huou.png",
                        List.of("/images/yenchung/nhung_huou.png"),
                        List.of("Dòng yến cao cấp.", "Phù hợp làm quà sức khỏe.", "Hương vị sang trọng."),
                        List.of("Dùng trực tiếp hoặc làm mát trước khi dùng."),
                        Map.of("Tên sản phẩm", "Yến chưng Nhung Hươu", "Quy cách", "6 hũ x 70ml", "Thương hiệu", "Kingnest"),
                        List.of("Yến chưng Nhung Hươu là dòng cao cấp, phù hợp biếu tặng và chăm sóc sức khỏe."),
                        List.of("Cao cấp", "Sang trọng", "Quà sức khỏe"),
                        "yen-chung", List.of("gift", "elder"), List.of("premium"), null)
        );
    }

    private ProductEntity product(
            String slug, String title, String shortDesc, String category, Long price, String image,
            List<String> gallery, List<String> benefits, List<String> usage, Map<String, String> specs,
            List<String> description, List<String> highlights, String productType,
            List<String> need, List<String> status, String badge) {
        ProductEntity entity = new ProductEntity();
        entity.setSlug(slug);
        entity.setTitle(title);
        entity.setShortDesc(shortDesc);
        entity.setCategory(category);
        entity.setPrice(price);
        entity.setImage(image);
        entity.setGallery(gallery);
        entity.setBenefits(benefits);
        entity.setUsage(usage);
        entity.setSpecs(specs);
        entity.setDescription(description);
        entity.setHighlights(highlights);
        entity.setProductType(productType);
        entity.setNeed(need);
        entity.setStatus(status);
        entity.setBadge(badge);
        return entity;
    }

    private List<NewsArticleEntity> buildNewsArticles() {
        return List.of(
                article(1, "cach-che-bien-yen-sao-dung-cach",
                        "Cách Chế Biến Yến Sào Đúng Cách",                         "Hướng dẫn",
                        "Hướng dẫn chi tiết cách làm sạch, chế biến yến sào để giữ nguyên dinh dưỡng.",
                        "/images/tin_tuc/ds_tintuc/che_bien.png",
                        """
                        <h3>Chuẩn bị nguyên liệu</h3>
                        <p>Yến sào tinh chế, nước sạch, đường phèn và dụng cụ chưng cách thủy.</p>
                        <h3>Các bước chế biến</h3>
                        <ul>
                            <li>Ngâm yến trong nước sạch 30–45 phút cho yến nở đều.</li>
                            <li>Làm sạch tạp chất, giữ nguyên sợi yến.</li>
                            <li>Chưng cách thủy 20–30 phút tùy lượng yến.</li>
                            <li>Dùng ngay hoặc bảo quản ngăn mát trong 24 giờ.</li>
                        </ul>
                        <p>Chế biến đúng cách giúp giữ trọn giá trị dinh dưỡng và hương vị tự nhiên của yến sào.</p>
                        """),

                article(2, "tam-nhin-thuong-hieu-yen-sao-an-thinh-nhan",
                        "Tầm nhìn thương hiệu - Yến Sào An Thịnh Nhân",                         "Thương hiệu",
                        "Định hướng trở thành thương hiệu yến sào uy tín, chuẩn chất lượng và được tin dùng.",
                        "/images/tin_tuc/ds_tintuc/tam_nhin.png",
                        """
                        <p>KINGNest An Thịnh Nhân hướng tới trở thành thương hiệu yến sào được tin dùng tại Việt Nam.</p>
                        <h3>Định hướng phát triển</h3>
                        <ul>
                            <li>Chuẩn hóa quy trình tinh chế và đóng gói.</li>
                            <li>Mở rộng dòng sản phẩm phù hợp nhiều nhu cầu khách hàng.</li>
                            <li>Đồng hành cùng cộng đồng trong chăm sóc sức khỏe chủ động.</li>
                        </ul>
                        """),

                article(3, "y-nghia-thuong-hieu-kingnest-an-thinh-nhan",
                        "Ý nghĩa thương hiệu KINGNest An Thịnh Nhân",                         "Thương hiệu",
                        "“KING” thể hiện khát vọng mang đến sản phẩm yến chất lượng cao, xứng tầm thương hiệu dẫn đầu.",
                        "/images/tin_tuc/ds_tintuc/y_nghia.png",
                        """
                        <p>Thương hiệu KINGNest kết hợp giữa khát vọng chất lượng cao và giá trị truyền thống của yến sào Việt.</p>
                        <p>Chúng tôi cam kết mang đến sản phẩm nguyên chất, minh bạch nguồn gốc và dịch vụ tận tâm.</p>
                        """),

                article(4, "su-menh-cua-yen-sao-an-thinh-nhan",
                        "Sứ mệnh của Yến Sào An Thịnh Nhân",                         "Thương hiệu",
                        "Mang đến cộng đồng sản phẩm yến sào nguyên chất, an toàn, chuẩn chất lượng.",
                        "/images/tin_tuc/ds_tintuc/su_menh.png",
                        """
                        <p>Sứ mệnh của chúng tôi là đưa yến sào chất lượng đến gần hơn với mọi gia đình Việt.</p>
                        <ul>
                            <li>Chọn lọc nguyên liệu kỹ lưỡng.</li>
                            <li>Kiểm soát chất lượng ở từng khâu sản xuất.</li>
                            <li>Đặt sức khỏe khách hàng lên hàng đầu.</li>
                        </ul>
                        """),

                article(5, "vi-sao-nen-lua-chon-yen-sao-kingnest-an-thinh-nhan",
                        "Vì sao nên lựa chọn Yến sào KINGNest An Thịnh Nhân?", "Thương hiệu",
                        "Cam kết sản phẩm nguyên chất, không pha trộn, không chất tẩy, nguồn gốc rõ ràng.",
                        "/images/tin_tuc/ds_tintuc/chon_Kingnest.png",
                        """
                        <h3>Lý do nên chọn KINGNest</h3>
                        <ul>
                            <li>Nguồn gốc yến rõ ràng, minh bạch.</li>
                            <li>Quy trình tinh chế sạch, chuẩn chất lượng.</li>
                            <li>Đa dạng sản phẩm từ yến thô đến yến chưng tiện dùng.</li>
                            <li>Dịch vụ tư vấn và hỗ trợ tận tâm.</li>
                        </ul>
                        """),

                article(6, "thong-diep-thuong-hieu-yen-sao-an-thinh-nhan",
                        "Thông Điệp Thương Hiệu – Yến Sào An Thịnh Nhân", "Thương hiệu",
                        "An Thịnh Nhân trân quý sức khỏe khách hàng như chính sức khỏe của bản thân.",
                        "/images/tin_tuc/ds_tintuc/thong_diep.png",
                        """
                        <p>Chúng tôi tin rằng sức khỏe là tài sản quý giá nhất. Mỗi sản phẩm yến sào đều được tạo ra với tinh thần trách nhiệm và tận tâm.</p>
                        <p>KINGNest An Thịnh Nhân – Gửi trọn tâm trong từng sản phẩm.</p>
                        """),

                article(7, "tac-dung-lam-dep-da-tu-yen-sao",
                        "Tác Dụng Làm Đẹp Da Từ Yến Sào", "Sức khỏe",
                        "Yến sào được xem là bí quyết làm đẹp từ bên trong nhờ hàm lượng dinh dưỡng cao.",
                        "/images/tin_tuc/ds_tintuc/lam_dep.png",
                        """
                        <p>Yến sào chứa nhiều axit amin và dưỡng chất hỗ trợ tái tạo da, giúp da mịn màng và khỏe mạnh hơn.</p>
                        <h3>Lợi ích làm đẹp</h3>
                        <ul>
                            <li>Hỗ trợ cải thiện độ đàn hồi của da.</li>
                            <li>Bổ sung dưỡng chất từ bên trong.</li>
                            <li>Phù hợp chăm sóc da lâu dài khi dùng đều đặn.</li>
                        </ul>
                        """),

                article(8, "cach-bao-quan-yen-sao-sau-khi-chung",
                        "Cách Bảo Quản Yến Sào Sau Khi Chưng",                         "Hướng dẫn",
                        "Bảo quản đúng cách giúp duy trì giá trị dinh dưỡng, hương vị và độ an toàn của yến.",
                        "/images/tin_tuc/ds_tintuc/bao_quan.png",
                        """
                        <h3>Bảo quản yến đã chưng</h3>
                        <ul>
                            <li>Để nguội trước khi bảo quản.</li>
                            <li>Bảo quản ngăn mát từ 2–5°C.</li>
                            <li>Dùng trong vòng 24–48 giờ để đảm bảo chất lượng.</li>
                        </ul>
                        <p>Không để yến đã chưng ở nhiệt độ phòng quá lâu để tránh hư hỏng.</p>
                        """),

                article(9, "loi-ich-cua-yen-cho-tre-nho",
                        "Lợi Ích Của Yến Cho Trẻ Nhỏ",                         "Sức khỏe",
                        "Yến sào hỗ trợ chăm sóc sức khỏe, tăng cường đề kháng và phát triển cho trẻ em.",
                        "/images/tin_tuc/ds_tintuc/tre_em.png",
                        """
                        <p>Yến sào là nguồn dinh dưỡng quý, phù hợp bổ sung cho trẻ em khi sử dụng đúng liều lượng.</p>
                        <h3>Lưu ý khi cho trẻ dùng yến</h3>
                        <ul>
                            <li>Chọn sản phẩm yến chưng dành riêng cho trẻ em.</li>
                            <li>Dùng lượng vừa phải theo độ tuổi.</li>
                            <li>Tham khảo ý kiến chuyên gia nếu trẻ có tiền sử dị ứng.</li>
                        </ul>
                        """),

                article(10, "cach-phan-biet-yen-sao-that-gia",
                        "Cách Phân Biệt Yến Sào Thật Giả", "Hướng dẫn",
                        "Những dấu hiệu nhận biết yến sào thật và giả để tránh mua phải hàng kém chất lượng.",
                        "/images/tin_tuc/ds_tintuc/phan_biet.png",
                        """
                        <h3>Dấu hiệu yến sào thật</h3>
                        <ul>
                            <li>Sợi yến mềm, dai, không dễ vỡ khi ngâm.</li>
                            <li>Mùi hương tự nhiên, không mùi hóa chất.</li>
                            <li>Khi ngâm nở đều, không tan thành bột.</li>
                            <li>Nguồn gốc rõ ràng, có kiểm định chất lượng.</li>
                        </ul>
                        <p>Nên mua yến tại thương hiệu uy tín để đảm bảo chất lượng và an toàn.</p>
                        """),

                article(11, "cong-dung-tuyet-voi-cua-yen-sao",
                        "Công Dụng Tuyệt Vời Của Yến Sào", "Sức khỏe",
                        "Khám phá những lợi ích sức khỏe tuyệt vời mà yến sào mang lại cho cơ thể.",
                        "/images/tin_tuc/ds_tintuc/cong_dung.png",
                        """
                        <p>Yến sào được biết đến với nhiều công dụng bổ dưỡng, hỗ trợ sức khỏe toàn diện.</p>
                        <h3>Công dụng nổi bật</h3>
                        <ul>
                            <li>Bổ sung dinh dưỡng, hỗ trợ phục hồi sức khỏe.</li>
                            <li>Tăng cường đề kháng, cải thiện giấc ngủ.</li>
                            <li>Hỗ trợ làm đẹp da, chăm sóc sức khỏe lâu dài.</li>
                            <li>Phù hợp nhiều đối tượng: trẻ em, người lớn, người cao tuổi.</li>
                        </ul>
                        """)
        );
    }

    private NewsArticleEntity article(int sortOrder, String slug, String title, String category,
                                      String excerpt, String image, String contentHtml) {
        NewsArticleEntity entity = new NewsArticleEntity();
        entity.setSortOrder(sortOrder);
        entity.setSlug(slug);
        entity.setTitle(title);
        entity.setCategory(category);
        entity.setExcerpt(excerpt);
        entity.setImage(image);
        entity.setPublishedYear("2026");
        entity.setContentHtml(contentHtml);
        return entity;
    }
}
