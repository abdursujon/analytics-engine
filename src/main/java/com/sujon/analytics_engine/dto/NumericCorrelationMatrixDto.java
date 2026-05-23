package com.sujon.analytics_engine.dto;

import java.util.List;

public record NumericCorrelationMatrix(
        List<String> numericColumnNames,
        List<List<Double>> correlationValues,
        List<CorrelationPair> strongPairs,
        List<String> warnings
) {
    public record CorrelationPair(String columnA, String columnB, double correlationCoefficient) {}
}
