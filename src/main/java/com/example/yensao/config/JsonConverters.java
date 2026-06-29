package com.example.yensao.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonConverters() {
    }

    @Converter
    public static class StringListConverter implements AttributeConverter<List<String>, String> {

        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            if (attribute == null || attribute.isEmpty()) {
                return "[]";
            }
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Không thể chuyển danh sách sang JSON", e);
            }
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return Collections.emptyList();
            }
            try {
                return MAPPER.readValue(dbData, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                return Collections.emptyList();
            }
        }
    }

    @Converter
    public static class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

        @Override
        public String convertToDatabaseColumn(Map<String, String> attribute) {
            if (attribute == null || attribute.isEmpty()) {
                return "{}";
            }
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Không thể chuyển map sang JSON", e);
            }
        }

        @Override
        public Map<String, String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return Collections.emptyMap();
            }
            try {
                return MAPPER.readValue(dbData, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                return Collections.emptyMap();
            }
        }
    }
}
