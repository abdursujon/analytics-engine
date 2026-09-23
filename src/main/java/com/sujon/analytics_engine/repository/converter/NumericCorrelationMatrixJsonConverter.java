package com.sujon.analytics_engine.repository.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujon.analytics_engine.dto.NumericCorrelationMatrixDto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NumericCorrelationMatrixJsonConverter implements AttributeConverter<NumericCorrelationMatrixDto, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(NumericCorrelationMatrixDto matrix) {
        if (matrix == null) return null;
        try {
            return objectMapper.writeValueAsString(matrix);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise correlation matrix", e);
        }
    }

    @Override
    public NumericCorrelationMatrixDto convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, NumericCorrelationMatrixDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise correlation matrix", e);
        }
    }
}