package com.sujon.analytics_engine.service.parser;

import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CsvParser {

    private static final long MAX_CELL_COUNT = 1_000_000;

    public ParsedDatasetDto parseCsvBodyToDataset(String csvBody) {
        String[] allLines = csvBody.split("\\R", -1);

        if (allLines.length == 0 || allLines[0].isBlank()) {
            throw new BadRequestException("Invalid CSV");
        }

        String[] columnHeaders = splitCsvLineIntoCells(allLines[0]);
        int columnCount = columnHeaders.length;

        long estimatedCellCount = (long) (allLines.length - 1) * columnCount;
        if (estimatedCellCount > MAX_CELL_COUNT) {
            throw new BadRequestException("CSV exceeds maximum allowed cell count of one million cells");
        }

        return buildDatasetFromCsvRows(allLines, columnHeaders, csvBody.length());
    }

    private ParsedDatasetDto buildDatasetFromCsvRows(
            String[] allLines,
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
        for (int lineIndex = 1; lineIndex < allLines.length; lineIndex++) {
            if (allLines[lineIndex].isBlank()) continue;

            String[] cellValues = splitCsvLineIntoCells(allLines[lineIndex]);
            if (cellValues.length != columnCount) {
                throw new BadRequestException("Invalid CSV");
            }

            rowCount++;

            for (int c = 0; c < columnCount; c++) {
                String cellValue = cellValues[c];

                if (cellValue.isBlank()) {
                    nullCountPerColumn[c]++;
                    continue;
                }

                uniqueValuesPerColumn[c].add(cellValue.trim());

                Double numericCellValue = tryParseStringToDouble(cellValue);
                if (numericCellValue != null) {
                    numericValuesPerColumn[c].add(numericCellValue);
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

    private String[] splitCsvLineIntoCells(String line) {
        return line.split(",", -1);
    }

    private Double tryParseStringToDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
