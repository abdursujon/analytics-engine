package com.sujon.analytics_engine.dto;

import java.util.List;
import java.util.Set;

public record ParsedDataset(
        String[] columnHeaders,
        int totalRowCount,
        long totalPayloadSize,
        int[] nullCountPerColumn,
        Set<String>[] uniqueValuesPerColumn,
        List<Double>[] numericValuesPerColumn,
        boolean[] isNumericColumnFlags
) {
}
