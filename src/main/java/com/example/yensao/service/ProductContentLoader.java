package com.example.yensao.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProductContentLoader {

    private static final String YEN_CHUNG_DOCX = "static/docs/yen_chung/noi_dung_14_vi_yen_chung_Kingnest.docx";

    /** slug → thư mục ảnh (static/images/{folder}) */
    private static final Map<String, String> SLUG_TO_IMAGE_FOLDER = Map.ofEntries(
            Map.entry("chan-yen-ria-sach", "yentinh/chanria"),
            Map.entry("chan-yen-sach-nho", "yentinh/chantinhnho"),
            Map.entry("yen-tinh-che-hoa-hong", "yentinh/hoahong"),
            Map.entry("hong-yen-tinh-che", "yentinh/hong_yen"),
            Map.entry("yen-tinh-che-gan-tuyet", "yentinh/gantuyet"),
            Map.entry("yen-tinh-che-loai-1", "yentinh/tinhche1"),
            Map.entry("yen-tinh-che-loai-2", "yentinh/tinh_L2"),
            Map.entry("yen-tinh-che-soi-ngan-20-to", "yentinh/keosoi"),
            Map.entry("yen-vien-xu-baby", "yentinh/xu_baby"),
            Map.entry("yen-rut-long-xuong-cao-cap", "yentinh/long_xuong"),
            Map.entry("yen-rut-long-kho-cao-cap", "yentinh/long_kho"),
            Map.entry("yen-tho-vip", "yentho/tho_vip"),
            Map.entry("yen-tho-gan-gia", "yentho/gan_gia"),
            Map.entry("hop-qua-yen-chung-6-hu", "hopqua/hop6hu"),
            Map.entry("hop-qua-yen-chung-10-hu", "hopqua/hop10hu"),
            Map.entry("thung-yen-gia-si", "hopqua/thungyen")
    );

    private static final Map<String, String> SLUG_BY_YEN_CHUNG_TITLE = Map.ofEntries(
            Map.entry("YẾN CHƯNG ĐÔNG TRÙNG HẠ THẢO", "yen-chung-dong-trung-ha-thao-6-hu-70ml"),
            Map.entry("YẾN CHƯNG ĐƯỜNG PHÈN", "yen-chung-duong-phen-6-hu-70ml"),
            Map.entry("YẾN CHƯNG NHÂN SÂM", "yen-chung-nhan-sam-6-hu-70ml"),
            Map.entry("YẾN CHƯNG TÁO ĐỎ", "yen-chung-tao-do-6-hu-70ml"),
            Map.entry("YẾN CHƯNG LONG NHÃN", "yen-chung-long-nhan-6-hu-70ml"),
            Map.entry("YẾN CHƯNG HẠT CHIA", "yen-chung-hat-chia-6-hu-70ml"),
            Map.entry("YẾN CHƯNG HẠT SEN", "yen-chung-hat-sen-6-hu-70ml"),
            Map.entry("YẾN CHƯNG SAFFRON", "yen-chung-saffron-6-hu-70ml"),
            Map.entry("YẾN CHƯNG VỊ GỪNG", "yen-chung-vi-gung-6-hu-70ml"),
            Map.entry("YẾN CHƯNG DÀNH CHO TRẺ EM", "yen-chung-cho-tre-em-6-hu-70ml"),
            Map.entry("YẾN CHƯNG TAM VỊ", "yen-chung-tam-vi-6-hu-70ml"),
            Map.entry("YẾN CHƯNG ĂN KIÊNG ĐÔNG TRÙNG HẠ THẢO", "yen-chung-an-kieng-dong-trung-ha-thao-6-hu-70ml"),
            Map.entry("YẾN CHƯNG ĐƯỜNG ĂN KIÊNG", "yen-chung-duong-an-kieng-6-hu-70ml"),
            Map.entry("YẾN CHƯNG NHUNG HƯƠU", "yen-chung-nhung-huou-6-hu-70ml")
    );

    private static final Pattern PRICE_PATTERN = Pattern.compile("([\\d.,]+)\\s*đ", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_GIA_PATTERN = Pattern.compile("(?i)giá\\s*:?\\s*([\\d.,]+)");
    private static final Pattern SECTION_TWO = Pattern.compile("^2\\.\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SECTION_THREE = Pattern.compile("^3\\.\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SECTION_FOUR = Pattern.compile(
            "^4[.,]?\\s*(Thông tin chi tiết|Chi tiết sản phẩm).*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DETAIL_INFO = Pattern.compile("^(Thông tin chi tiết|Chi tiết sản phẩm).*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SPEC_LINE = Pattern.compile("^•\\s*([^:]+):\\s*(.+)$");
    private static final Pattern DASH_SPEC_LINE = Pattern.compile("^-\\s*([^:]+):\\s*(.+)$");
    private static final Pattern DIAMOND_SPEC_LINE = Pattern.compile("^[♦]\\uFE0F?\\s*([^:]+):\\s*(.+)$");
    private static final Pattern YEN_CHUNG_PRODUCT_START = Pattern.compile(
            "^YẾN CHƯNG [A-ZÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ\\s]+$"
    );

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public List<String> getDocxSlugs() {
        return List.copyOf(SLUG_TO_IMAGE_FOLDER.keySet());
    }

    public List<String> getYenChungDocxSlugs() {
        return List.copyOf(SLUG_BY_YEN_CHUNG_TITLE.values());
    }

    public Optional<String> imageFolderForSlug(String slug) {
        return Optional.ofNullable(SLUG_TO_IMAGE_FOLDER.get(slug));
    }

    public Optional<ProductDocxData> loadYenChungParsed(String slug) {
        return Optional.ofNullable(loadAllYenChungParsed().get(slug));
    }

    public Map<String, ProductDocxData> loadAllYenChungParsed() {
        Map<String, ProductDocxData> parsed = new LinkedHashMap<>();
        Optional<List<String>> paragraphsOptional = loadParagraphsFromResource(YEN_CHUNG_DOCX);
        if (paragraphsOptional.isEmpty()) {
            return parsed;
        }

        for (List<String> section : splitYenChungSections(paragraphsOptional.get())) {
            if (section.isEmpty()) {
                continue;
            }

            String slug = SLUG_BY_YEN_CHUNG_TITLE.get(section.get(0));
            if (slug == null) {
                continue;
            }

            parsed.put(slug, parseParagraphs(section));
        }

        return parsed;
    }

    public Optional<ProductDocxData> loadParsed(String slug) {
        String folder = SLUG_TO_IMAGE_FOLDER.get(slug);
        if (folder == null) {
            return Optional.empty();
        }

        return loadDocxFromImageFolder(folder).map(this::parseParagraphs);
    }

    public Optional<String> loadContentHtml(String slug) {
        return loadParsed(slug).map(ProductDocxData::getContentHtml);
    }

    private Optional<List<String>> loadDocxFromImageFolder(String folder) {
        try {
            Resource[] resources = resolver.getResources("classpath:static/images/" + folder + "/*.docx");
            Resource selected = null;
            long newest = -1L;

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.startsWith("~$")) {
                    continue;
                }
                long modified = resource.lastModified();
                if (modified >= newest) {
                    newest = modified;
                    selected = resource;
                }
            }

            if (selected == null) {
                return Optional.empty();
            }

            String filename = selected.getFilename();
            return loadParagraphsFromResource("static/images/" + folder + "/" + filename);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<List<String>> loadParagraphsFromResource(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return Optional.empty();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                return Optional.empty();
            }

            if (isZipArchive(bytes)) {
                return Optional.of(readDocxParagraphs(bytes));
            }

            return Optional.of(readPlainParagraphs(new String(bytes, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private List<List<String>> splitYenChungSections(List<String> paragraphs) {
        List<List<String>> sections = new ArrayList<>();
        List<String> current = null;

        for (String paragraph : paragraphs) {
            if (YEN_CHUNG_PRODUCT_START.matcher(paragraph).matches()) {
                if (current != null) {
                    sections.add(current);
                }
                current = new ArrayList<>();
                current.add(paragraph);
                continue;
            }

            if (current != null) {
                current.add(paragraph);
            }
        }

        if (current != null) {
            sections.add(current);
        }

        return sections;
    }

    private List<String> readDocxParagraphs(byte[] bytes) throws IOException {
        List<String> paragraphs = new ArrayList<>();
        try (InputStream inputStream = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
                if (!text.isEmpty()) {
                    paragraphs.add(text);
                }
            }
        }
        return paragraphs;
    }

    private List<String> readPlainParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (current.length() > 0) {
                    paragraphs.add(current.toString().trim());
                    current.setLength(0);
                }
                continue;
            }

            if (current.length() > 0) {
                current.append(' ').append(line);
            } else {
                current.append(line);
            }
        }

        if (current.length() > 0) {
            paragraphs.add(current.toString().trim());
        }

        return paragraphs;
    }

    private ProductDocxData parseParagraphs(List<String> paragraphs) {
        ProductDocxData data = new ProductDocxData();
        if (paragraphs.isEmpty()) {
            return data;
        }

        int priceIndex = findPriceIndex(paragraphs);
        if (priceIndex > 0) {
            data.setTitle(paragraphs.get(0));
        } else if (!paragraphs.get(0).toLowerCase().contains("giá")) {
            data.setTitle(paragraphs.get(0));
        }

        parsePrice(paragraphs, data, priceIndex);

        int sectionTwo = findSectionIndex(paragraphs, SECTION_TWO, 0);
        int sectionThree = findSectionIndex(paragraphs, SECTION_THREE, sectionTwo + 1);
        int sectionFour = findDetailSectionIndex(paragraphs, sectionThree + 1);

        if (sectionTwo >= 0 && sectionThree > sectionTwo) {
            String sectionName = SECTION_TWO.matcher(paragraphs.get(sectionTwo)).replaceFirst("$1").trim().toLowerCase();
            List<String> items = collectSectionItems(paragraphs, sectionTwo + 1, sectionThree);
            if (sectionName.contains("đặc điểm") || sectionName.contains("nổi bật")) {
                data.setHighlights(items);
            } else if (sectionName.contains("thành phần") || sectionName.contains("công dụng")) {
                data.setBenefits(items);
            } else {
                data.setBenefits(items);
            }
        }

        if (sectionThree >= 0) {
            int usageEnd = sectionFour >= 0 ? sectionFour : paragraphs.size();
            data.setUsage(collectSectionItems(paragraphs, sectionThree + 1, usageEnd));
        }

        if (sectionFour >= 0) {
            parseDetailSection(paragraphs, sectionFour, data);
            data.setContentHtml(paragraphsToHtml(paragraphs.subList(sectionFour, paragraphs.size())));
        } else {
            data.setContentHtml(paragraphsToHtml(paragraphs));
        }

        if (data.getTitle() == null || data.getTitle().isBlank()) {
            data.setTitle(paragraphs.get(0));
        }

        if (looksLikePriceLine(data.getTitle())) {
            String specTitle = data.specsOrEmpty().get("Tên sản phẩm");
            if (specTitle != null && !specTitle.isBlank()) {
                data.setTitle(specTitle);
            }
        } else {
            String specTitle = data.specsOrEmpty().get("Tên sản phẩm");
            if (specTitle != null && !specTitle.isBlank()) {
                data.setTitle(specTitle);
            }
        }

        return data;
    }

    private boolean looksLikePriceLine(String text) {
        if (text == null) {
            return true;
        }
        String normalized = text.toLowerCase();
        return normalized.startsWith("1. giá")
                || normalized.startsWith("- giá")
                || normalized.contains("giá:")
                || PRICE_PATTERN.matcher(text).find();
    }

    private int findPriceIndex(List<String> paragraphs) {
        for (int index = 0; index < paragraphs.size(); index++) {
            if (containsPriceValue(paragraphs.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private void parsePrice(List<String> paragraphs, ProductDocxData data, int priceIndex) {
        int start = priceIndex >= 0 ? priceIndex : 0;
        int end = Math.min(paragraphs.size(), start + 3);
        for (int index = start; index < end; index++) {
            Long price = extractPrice(paragraphs.get(index));
            if (price != null) {
                data.setPrice(price);
                return;
            }
        }
    }

    private boolean containsPriceValue(String text) {
        return extractPrice(text) != null;
    }

    private Long extractPrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher giaMatcher = PRICE_GIA_PATTERN.matcher(text);
        if (giaMatcher.find()) {
            return parsePriceDigits(giaMatcher.group(1));
        }

        Matcher priceMatcher = PRICE_PATTERN.matcher(text);
        if (priceMatcher.find()) {
            return parsePriceDigits(priceMatcher.group(1));
        }

        return null;
    }

    private Long parsePriceDigits(String rawDigits) {
        if (rawDigits == null || rawDigits.isBlank()) {
            return null;
        }

        String digits = rawDigits.replace(".", "").replace(",", "").trim();
        if (digits.isEmpty()) {
            return null;
        }

        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int findSectionIndex(List<String> paragraphs, Pattern pattern, int fromIndex) {
        for (int index = Math.max(0, fromIndex); index < paragraphs.size(); index++) {
            if (pattern.matcher(paragraphs.get(index)).matches()) {
                return index;
            }
        }
        return -1;
    }

    private int findDetailSectionIndex(List<String> paragraphs, int fromIndex) {
        for (int index = Math.max(0, fromIndex); index < paragraphs.size(); index++) {
            String text = paragraphs.get(index);
            if (SECTION_FOUR.matcher(text).matches() || DETAIL_INFO.matcher(text).matches()) {
                return index;
            }
        }
        return -1;
    }

    private List<String> collectSectionItems(List<String> paragraphs, int start, int end) {
        List<String> items = new ArrayList<>();

        for (int index = start; index < end; index++) {
            String text = paragraphs.get(index).trim();
            if (text.isEmpty() || isSectionHeader(text) || isNumberedSectionStart(text)) {
                continue;
            }
            if (isBulletLine(text)) {
                items.add(stripBullet(text));
            } else {
                items.add(text);
            }
        }

        return items;
    }

    private boolean isNumberedSectionStart(String text) {
        return text.matches("^\\d+[.,]?\\s*.+");
    }

    private List<String> collectBulletItems(List<String> paragraphs, int start, int end) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int index = start; index < end; index++) {
            String text = paragraphs.get(index).trim();
            if (isBulletLine(text)) {
                if (current.length() > 0) {
                    items.add(current.toString().trim());
                    current.setLength(0);
                }
                current.append(stripBullet(text));
            } else if (current.length() > 0 && !isSectionHeader(text)) {
                current.append(' ').append(text);
            }
        }

        if (current.length() > 0) {
            items.add(current.toString().trim());
        }

        return items;
    }

    private void parseDetailSection(List<String> paragraphs, int startIndex, ProductDocxData data) {
        Map<String, String> specs = new LinkedHashMap<>();
        List<String> description = new ArrayList<>();
        List<String> highlights = new ArrayList<>();

        boolean inSpecs = false;
        boolean inDescription = false;
        boolean inHighlights = false;
        boolean inBenefits = false;
        List<String> ingredientBenefits = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();

        for (int index = startIndex + 1; index < paragraphs.size(); index++) {
            String text = paragraphs.get(index).trim();
            String normalized = text.replace("*", "").trim().toLowerCase();

            if (normalized.contains("thông tin sản phẩm")) {
                flushDescriptionParagraph(paragraph, description, inDescription);
                inSpecs = true;
                inDescription = false;
                inHighlights = false;
                inBenefits = false;
                continue;
            }

            if (normalized.contains("mô tả sản phẩm") || normalized.contains("giới thiệu sản phẩm")) {
                flushDescriptionParagraph(paragraph, description, inDescription);
                inSpecs = false;
                inDescription = true;
                inHighlights = false;
                inBenefits = false;
                continue;
            }

            if (normalized.contains("thành phần")) {
                flushDescriptionParagraph(paragraph, description, inDescription);
                inSpecs = false;
                inDescription = false;
                inHighlights = false;
                inBenefits = true;
                continue;
            }

            if (normalized.contains("đặc điểm nổi bật")) {
                flushDescriptionParagraph(paragraph, description, inDescription);
                inSpecs = false;
                inDescription = false;
                inHighlights = true;
                inBenefits = false;
                continue;
            }

            Matcher specMatcher = SPEC_LINE.matcher(text);
            if (inSpecs && specMatcher.matches()) {
                specs.put(specMatcher.group(1).trim(), specMatcher.group(2).trim());
                continue;
            }

            Matcher diamondSpecMatcher = DIAMOND_SPEC_LINE.matcher(text);
            if (diamondSpecMatcher.matches()) {
                flushDescriptionParagraph(paragraph, description, inDescription);
                specs.put(diamondSpecMatcher.group(1).trim(), diamondSpecMatcher.group(2).trim());
                continue;
            }

            Matcher dashSpecMatcher = DASH_SPEC_LINE.matcher(text);
            if (dashSpecMatcher.matches() && dashSpecMatcher.group(1).trim().length() <= 40) {
                flushDescriptionParagraph(paragraph, description, inDescription);
                specs.put(dashSpecMatcher.group(1).trim(), dashSpecMatcher.group(2).trim());
                continue;
            }

            if (text.startsWith("✨") || (text.startsWith("♦") && !text.contains(":"))) {
                flushDescriptionParagraph(paragraph, description, inDescription);
                inHighlights = true;
                inDescription = false;
                highlights.add(stripHighlightMarker(text));
                continue;
            }

            if (inHighlights) {
                if (isBulletLine(text)) {
                    highlights.add(stripBullet(text));
                } else if (!isSectionHeader(text)) {
                    highlights.add(text);
                }
                continue;
            }

            if (inBenefits) {
                if (isBulletLine(text)) {
                    ingredientBenefits.add(stripBullet(text));
                } else if (!isSectionHeader(text) && !text.isBlank()) {
                    ingredientBenefits.add(text);
                }
                continue;
            }

            if (inDescription) {
                if (isBulletLine(text)) {
                    flushDescriptionParagraph(paragraph, description, inDescription);
                    description.add(stripBullet(text));
                } else if (!isSectionHeader(text) && !DIAMOND_SPEC_LINE.matcher(text).matches()) {
                    if (paragraph.length() > 0) {
                        paragraph.append(' ');
                    }
                    paragraph.append(text);
                }
            }
        }

        flushDescriptionParagraph(paragraph, description, inDescription);

        data.setSpecs(specs);
        data.setDescription(description);
        if (data.getHighlights() == null || data.getHighlights().isEmpty()) {
            data.setHighlights(highlights);
        } else if (!highlights.isEmpty()) {
            List<String> merged = new ArrayList<>(data.getHighlights());
            merged.addAll(highlights);
            data.setHighlights(merged);
        }
        if (!ingredientBenefits.isEmpty()) {
            if (data.getBenefits() == null || data.getBenefits().isEmpty()) {
                data.setBenefits(ingredientBenefits);
            } else {
                List<String> mergedBenefits = new ArrayList<>(data.getBenefits());
                mergedBenefits.addAll(ingredientBenefits);
                data.setBenefits(mergedBenefits);
            }
        }
    }

    private void flushDescriptionParagraph(StringBuilder paragraph, List<String> description, boolean inDescription) {
        if (paragraph.length() > 0 && inDescription) {
            description.add(paragraph.toString().trim());
        }
        paragraph.setLength(0);
    }

    private boolean isSectionHeader(String text) {
        return SECTION_TWO.matcher(text).matches()
                || SECTION_THREE.matcher(text).matches()
                || SECTION_FOUR.matcher(text).matches()
                || DETAIL_INFO.matcher(text).matches();
    }

    private boolean isBulletLine(String text) {
        return text.startsWith("•") || text.startsWith("- ");
    }

    private String stripBullet(String text) {
        return text.replaceFirst("^[•\\-]\\s*", "").trim();
    }

    private String stripHighlightMarker(String text) {
        return text.replaceFirst("^[✨♦️♦]\\s*", "").trim();
    }

    private boolean isZipArchive(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private String paragraphsToHtml(List<String> paragraphs) {
        StringBuilder html = new StringBuilder();
        boolean inList = false;

        for (int index = 0; index < paragraphs.size(); index++) {
            String text = paragraphs.get(index);
            if (text.isBlank()) {
                continue;
            }

            if (SECTION_FOUR.matcher(text).matches() || DETAIL_INFO.matcher(text).matches()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                continue;
            }

            if (text.startsWith("•") || text.startsWith("- ") || text.startsWith("✨") || text.startsWith("♦")) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                String item;
                if (text.startsWith("•") || text.startsWith("- ")) {
                    item = stripBullet(text);
                } else {
                    item = stripHighlightMarker(text);
                }
                html.append("<li>").append(escapeHtml(item)).append("</li>");
                continue;
            }

            if (inList) {
                html.append("</ul>");
                inList = false;
            }

            Matcher specMatcher = SPEC_LINE.matcher(text);
            if (specMatcher.matches()) {
                html.append("<p><strong>")
                        .append(escapeHtml(specMatcher.group(1).trim()))
                        .append(":</strong> ")
                        .append(escapeHtml(specMatcher.group(2).trim()))
                        .append("</p>");
                continue;
            }

            Matcher diamondSpecMatcher = DIAMOND_SPEC_LINE.matcher(text);
            if (diamondSpecMatcher.matches()) {
                html.append("<p><strong>")
                        .append(escapeHtml(diamondSpecMatcher.group(1).trim()))
                        .append(":</strong> ")
                        .append(escapeHtml(diamondSpecMatcher.group(2).trim()))
                        .append("</p>");
                continue;
            }

            String normalized = text.replace("*", "").trim().toLowerCase();
            if (normalized.contains("thông tin sản phẩm")
                    || normalized.contains("mô tả sản phẩm")
                    || normalized.contains("giới thiệu sản phẩm")
                    || normalized.contains("đặc điểm nổi bật")) {
                html.append("<h3>").append(escapeHtml(text.replace("*", "").trim())).append("</h3>");
            } else if (isHeading(text, index)) {
                html.append("<h3>").append(escapeHtml(text)).append("</h3>");
            } else {
                html.append("<p>").append(escapeHtml(text)).append("</p>");
            }
        }

        if (inList) {
            html.append("</ul>");
        }

        return html.toString();
    }

    private boolean isHeading(String text, int index) {
        if (index == 0) {
            return false;
        }
        if (text.matches("^\\d+\\..*")) {
            return true;
        }
        if (text.length() <= 90 && text.equals(text.toUpperCase()) && text.chars().anyMatch(Character::isLetter)) {
            return true;
        }
        return text.length() <= 80
                && !text.endsWith(".")
                && text.chars().filter(ch -> ch == '.').count() <= 1;
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
