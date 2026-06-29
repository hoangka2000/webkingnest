package com.example.yensao.entity;

import com.example.yensao.config.JsonConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String shortDesc;

    private String category;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private String image;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringListConverter.class)
    private List<String> gallery;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringListConverter.class)
    private List<String> benefits;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringListConverter.class)
    private List<String> usage;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringMapConverter.class)
    private Map<String, String> specs;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringListConverter.class)
    private List<String> description;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringListConverter.class)
    private List<String> highlights;

    private String productType;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringListConverter.class)
    private List<String> need;

    @Column(columnDefinition = "CLOB")
    @Convert(converter = JsonConverters.StringListConverter.class)
    private List<String> status;

    private String badge;

    @Column(columnDefinition = "CLOB")
    private String contentHtml;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getShortDesc() {
        return shortDesc;
    }

    public void setShortDesc(String shortDesc) {
        this.shortDesc = shortDesc;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<String> getGallery() {
        return gallery;
    }

    public void setGallery(List<String> gallery) {
        this.gallery = gallery;
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

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public List<String> getNeed() {
        return need;
    }

    public void setNeed(List<String> need) {
        this.need = need;
    }

    public List<String> getStatus() {
        return status;
    }

    public void setStatus(List<String> status) {
        this.status = status;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }
}
