package com.example.yensao.service;

import com.example.yensao.util.NewsImageUtil;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NewsContentLoader {

    private static final String DOCX_DIR = "static/docs/news/";

    public Optional<String> loadContentHtml(String slug) {
        String fileName = docxFileName(slug);
        ClassPathResource resource = new ClassPathResource(DOCX_DIR + fileName);

        if (!resource.exists()) {
            return Optional.empty();
        }

        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                return Optional.empty();
            }

            if (isZipArchive(bytes)) {
                return Optional.of(convertDocxToHtml(bytes));
            }

            return Optional.of(convertPlainTextToHtml(new String(bytes, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public String docxFileName(String slug) {
        String key = NewsImageUtil.imageKey(slug);
        if ("y_nghia".equals(key)) {
            return "Y_nghia.docx";
        }
        return key + ".docx";
    }

    private boolean isZipArchive(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private String convertDocxToHtml(byte[] bytes) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            List<String> paragraphs = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
                if (!text.isEmpty()) {
                    paragraphs.add(text);
                }
            }
            return paragraphsToHtml(paragraphs);
        }
    }

    private String convertPlainTextToHtml(String text) {
        List<String> paragraphs = new ArrayList<>();
        String[] lines = text.split("\\R");

        StringBuilder current = new StringBuilder();
        for (String rawLine : lines) {
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

        if (paragraphs.isEmpty()) {
            paragraphs.add(text.trim());
        }

        return paragraphsToHtml(paragraphs);
    }

    private String paragraphsToHtml(List<String> paragraphs) {
        StringBuilder html = new StringBuilder();
        boolean inList = false;

        for (int index = 0; index < paragraphs.size(); index++) {
            String text = paragraphs.get(index);
            if (text.isBlank()) {
                continue;
            }

            if (isListItem(text)) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(escapeHtml(stripListMarker(text))).append("</li>");
                continue;
            }

            if (inList) {
                html.append("</ul>");
                inList = false;
            }

            if (isHeading(text, index)) {
                html.append("<h3>").append(escapeHtml(text)).append("</h3>");
            } else if (isQuote(text)) {
                html.append("<blockquote><p>").append(escapeHtml(text)).append("</p></blockquote>");
            } else {
                html.append("<p>").append(escapeHtml(text)).append("</p>");
            }
        }

        if (inList) {
            html.append("</ul>");
        }

        return html.toString();
    }

    private boolean isListItem(String text) {
        return text.startsWith("•")
                || text.startsWith("- ")
                || text.startsWith("– ")
                || text.startsWith("* ");
    }

    private String stripListMarker(String text) {
        return text.replaceFirst("^[•\\-*–]\\s*", "").trim();
    }

    private boolean isHeading(String text, int index) {
        if (index == 0) {
            return false;
        }
        if (text.matches("^\\d+\\..*")) {
            return true;
        }
        if (text.contains("🔹")) {
            return true;
        }
        if (text.length() <= 80 && !text.endsWith(".") && !text.endsWith("。")) {
            return text.chars().filter(ch -> ch == '.').count() <= 1;
        }
        return false;
    }

    private boolean isQuote(String text) {
        return text.startsWith("\"") || text.startsWith("'") || text.startsWith("“");
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
