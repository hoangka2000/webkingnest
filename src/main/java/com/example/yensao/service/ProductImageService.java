package com.example.yensao.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ProductImageService {

    private static final Pattern IMAGE_EXT = Pattern.compile(".*\\.(png|jpe?g|webp)$", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> SLUG_TO_FOLDER = Map.ofEntries(
            Map.entry("yen-chung-dong-trung-ha-thao-6-hu-70ml", "yenchung/dongtrunghathao"),
            Map.entry("yen-chung-duong-phen-6-hu-70ml", "yenchung/duongphen"),
            Map.entry("yen-chung-nhan-sam-6-hu-70ml", "yenchung/nhansam"),
            Map.entry("yen-chung-tao-do-6-hu-70ml", "yenchung/taodo"),
            Map.entry("yen-chung-long-nhan-6-hu-70ml", "yenchung/longnhan"),
            Map.entry("yen-chung-hat-chia-6-hu-70ml", "yenchung/hatchia"),
            Map.entry("yen-chung-hat-sen-6-hu-70ml", "yenchung/hatsen"),
            Map.entry("yen-chung-saffron-6-hu-70ml", "yenchung/saffron"),
            Map.entry("yen-chung-vi-gung-6-hu-70ml", "yenchung/vigung"),
            Map.entry("yen-chung-cho-tre-em-6-hu-70ml", "yenchung/tre_em"),
            Map.entry("yen-chung-tam-vi-6-hu-70ml", "yenchung/tamvi"),
            Map.entry("yen-chung-an-kieng-dong-trung-ha-thao-6-hu-70ml", "yenchung/ankieng"),
            Map.entry("yen-chung-duong-an-kieng-6-hu-70ml", "yenchung/ankieng"),
            Map.entry("yen-chung-nhung-huou-6-hu-70ml", "yenchung/nhunghuou"),
            Map.entry("chan-yen-ria-sach", "yentinh/chanria"),
            Map.entry("chan-yen-sach-nho", "yentinh/chantinhnho"),
            Map.entry("yen-tinh-che-hoa-hong", "yentinh/hoahong"),
            Map.entry("hong-yen-tinh-che", "yentinh/hoahong"),
            Map.entry("yen-tinh-che-xo-roi-dap-chan", "yentinh/keosoi"),
            Map.entry("yen-tinh-che-soi-ngan-20-to", "yentinh/keosoi"),
            Map.entry("yen-vien-xu-baby", "yentinh/keosoi"),
            Map.entry("yen-tinh-che-gan-tuyet", "yentinh/tinhche1"),
            Map.entry("yen-tinh-che-loai-1", "yentinh/tinhche1"),
            Map.entry("yen-tinh-che-loai-2", "yentinh/tinhche1")
    );

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public Optional<ProductImages> resolve(String slug) {
        String folder = SLUG_TO_FOLDER.get(slug);
        if (folder == null) {
            return Optional.empty();
        }

        try {
            Resource[] resources = resolver.getResources("classpath:static/images/" + folder + "/*");
            List<String> filenames = new ArrayList<>();

            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String filename = resource.getFilename();
                if (filename != null && IMAGE_EXT.matcher(filename).matches()) {
                    filenames.add(filename);
                }
            }

            if (filenames.isEmpty()) {
                return Optional.empty();
            }

            filenames.sort(this::compareFilenames);

            String listImage = filenames.stream()
                    .filter(name -> name.toLowerCase().startsWith("trungbay"))
                    .findFirst()
                    .map(name -> toUrl(folder, name))
                    .orElseGet(() -> toUrl(folder, filenames.get(0)));

            List<String> gallery = new ArrayList<>();
            filenames.stream()
                    .filter(name -> name.toLowerCase().startsWith("trungbay"))
                    .map(name -> toUrl(folder, name))
                    .forEach(gallery::add);
            filenames.stream()
                    .filter(name -> !name.toLowerCase().startsWith("trungbay"))
                    .map(name -> toUrl(folder, name))
                    .forEach(gallery::add);

            if (gallery.isEmpty()) {
                gallery.add(listImage);
            }

            return Optional.of(new ProductImages(listImage, List.copyOf(gallery)));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private int compareFilenames(String left, String right) {
        String leftLower = left.toLowerCase();
        String rightLower = right.toLowerCase();
        if (leftLower.startsWith("trungbay")) {
            return -1;
        }
        if (rightLower.startsWith("trungbay")) {
            return 1;
        }
        return Comparator.comparingInt(this::leadingNumber)
                .thenComparing(String::compareToIgnoreCase)
                .compare(left, right);
    }

    private int leadingNumber(String filename) {
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < filename.length(); index++) {
            char character = filename.charAt(index);
            if (Character.isDigit(character)) {
                digits.append(character);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private String toUrl(String folder, String filename) {
        return "/images/" + folder + "/" + encodeFilename(filename);
    }

    private String encodeFilename(String filename) {
        return URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record ProductImages(String listImage, List<String> gallery) {
    }
}
