package com.sujon.analytics_engine.service.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class JsonParser {

    private static final long MAX_CELL_COUNT = 1_000_000;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParsedDatasetDto parseJsonBodyToDataset(String jsonBody, boolean isNdjson) {
        List<Map<String, Object>> records = parseRecordsFromJsonBody(jsonBody, isNdjson);

        if (records.isEmpty()) {
            throw new BadRequestException("JSON contains no records");
        }

        String[] columnHeaders = collectColumnHeadersFromRecords(records);
        if (columnHeaders.length == 0) {
            throw new BadRequestException("JSON records have no fields");
        }

        long estimatedCellCount = (long) records.size() * columnHeaders.length;
        if (estimatedCellCount > MAX_CELL_COUNT) {
            throw new BadRequestException("JSON exceeds maximum allowed cell count of one million cells");
        }

        return buildDatasetFromRecords(records, columnHeaders, jsonBody.length());
    }

    private List<Map<String, Object>> parseRecordsFromJsonBody(String jsonBody, boolean isNdjson) {
        try {
            if (isNdjson) {
                List<Map<String, Object>> records = new ArrayList<>();
                for (String line : jsonBody.split("\\R")) {
                    if (line.isBlank()) continue;
                    records.add(objectMapper.readValue(line, new TypeReference<>() {}));
                }
                return records;
            }
            return objectMapper.readValue(jsonBody, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse JSON: " + e.getMessage());
        }
    }

    private String[] collectColumnHeadersFromRecords(List<Map<String, Object>> records) {
        LinkedHashSet<String> orderedHeaders = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            orderedHeaders.addAll(record.keySet());
        }
        return orderedHeaders.toArray(new String[0]);
    }

    private ParsedDatasetDto buildDatasetFromRecords(
            List<Map<String, Object>> records,
            String[] columnHeaders,
            long payloadSize
    ) {
        int columnCount = columnHeaders.length;

        int[] nullCountPerColumn = new int[columnCount];
        @SuppressWarnings("unchecked")
        Set<String>[] uniqueValuesPerColumn = new Set[columnCount];
        @SuppressWarnings("unchecked")
        List<Double>[] numericValuesPerColumn = new ArrayList[columnCount];
        boolean[] isNumericColumnFlags = new boolean[columnCount];

        for (int c = 0; c < columnCount; c++) {
            uniqueValuesPerColumn[c] = new HashSet<>();
            numericValuesPerColumn[c] = new ArrayList<>();
        }
        Arrays.fill(isNumericColumnFlags, true);

        int rowCount = 0;
        for (Map<String, Object> record : records) {
            rowCount++;
            for (int c = 0; c < columnCount; c++) {
                Object cellValue = record.get(columnHeaders[c]);

                if (cellValue == null) {
                    nullCountPerColumn[c]++;
                    continue;
                }

                if (cellValue instanceof Map || cellValue instanceof List) {
                    throw new BadRequestException(
                            "Nested objects/arrays not supported in column: " + columnHeaders[c]);
                }

                uniqueValuesPerColumn[c].add(cellValue.toString().trim());

                if (cellValue instanceof Number numericCell) {
                    numericValuesPerColumn[c].add(numericCell.doubleValue());
                } else {
                    isNumericColumnFlags[c] = false;
                }
            }
        }

        return new ParsedDatasetDto(
                columnHeaders,
                rowCount,
                payloadSize,
                nullCountPerColumn,
                uniqueValuesPerColumn,
                numericValuesPerColumn,
                isNumericColumnFlags
        );
    }
}
