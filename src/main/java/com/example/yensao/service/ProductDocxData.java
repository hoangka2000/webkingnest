package com.example.yensao.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductDocxData {

    private String title;
    private Long price;
    private List<String> benefits;
    private List<String> usage;
    private Map<String, String> specs;
    private List<String> description;
    private List<String> highlights;
    private String contentHtml;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public List<String> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<String> benefits) {
        this.benefits = benefits;
    }

    public List<String> getUsage() {
        return usage;
    }

    public void setUsage(List<String> usage) {
        this.usage = usage;
    }

    public Map<String, String> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, String> specs) {
        this.specs = specs;
    }

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public String getShortDesc() {
        if (description != null && !description.isEmpty()) {
            return description.get(0);
        }
        if (benefits != null && !benefits.isEmpty()) {
            return benefits.get(0);
        }
        return title;
    }

    public Map<String, String> specsOrEmpty() {
        return specs == null ? Map.of() : specs;
    }

    public List<String> benefitsOrEmpty() {
        return benefits == null ? List.of() : benefits;
    }

    public List<String> usageOrEmpty() {
        return usage == null ? List.of() : usage;
    }

    public List<String> descriptionOrEmpty() {
        return description == null ? List.of() : description;
    }

    public List<String> highlightsOrEmpty() {
        return highlights == null ? List.of() : highlights;
    }

    public Map<String, String> mutableSpecs() {
        if (specs == null) {
            specs = new LinkedHashMap<>();
        }
        return specs;
    }
}
