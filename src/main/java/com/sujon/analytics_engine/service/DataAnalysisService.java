package com.sujon.analytics_engine.service;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import com.sujon.analytics_engine.exception.NotFoundException;
import com.sujon.analytics_engine.model.ColumnStatistics;
import com.sujon.analytics_engine.repository.ColumnStatisticsRepository;
import com.sujon.analytics_engine.repository.DataAnalysisRepository;
import com.sujon.analytics_engine.repository.entity.ColumnStatisticsEntity;
import com.sujon.analytics_engine.repository.entity.DataAnalysisEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.Collections;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Service layer containing business logic for CSV data analysis.
 */
@Service
@RequiredArgsConstructor
public class DataAnalysisService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_CELL_COUNT = 1_000_000;
    private final DataAnalysisRepository dataAnalysisRepository;
    private final ColumnStatisticsRepository columnStatisticsRepository;

    /**
     * Generates a SHA-256 hash of the given input string.
     * @param input the raw CSV data as a String
     * @return a 64-character hexadecimal SHA-256 hash representing the input content
     * @throws RuntimeException if the SHA-256 algorithm is not available
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed");
        }
    }

    /**
     * Normalizes CSV content for consistent hashing.
     * @param data the raw CSV content
     * @return normalized CSV content suitable for hashing
     */
    private String normalizeForHash(String data) {
        return data
                .replace("\r\n", "\n")
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }


    private Double tryParseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private Double calculateMean(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        for (Double value : values) {
            sum += value;
        }
        return sum / values.size();
    }


    private Double calculateMedian(List<Double> sortedValues) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int size = sortedValues.size();
        if (size % 2 == 0) {
            return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
        } else {
            return sortedValues.get(size / 2);
        }
    }


    private Double calculateStandardDeviation(List<Double> values, Double mean) {
        if (values.isEmpty() || mean == null) {
            return null;
        }
        double sumSquaredDiff = 0.0;
        for (Double value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        double variance = sumSquaredDiff / values.size();
        return Math.sqrt(variance);
    }


    private Double calculatePercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double fraction = index - lower;
        return sortedValues.get(lower) + fraction * (sortedValues.get(upper) - sortedValues.get(lower));
    }


    public DataAnalysisResponseDto analyseCsvData(String data) {

        if (data == null || data.isBlank()) {
            throw new BadRequestException("Invalid CSV");
        }

        long fileSizeBytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum allowed size of 5MB");
        }

        String contentHash = sha256(normalizeForHash(data));

        return dataAnalysisRepository.findByContentHash(contentHash)
                .map(existing -> new DataAnalysisResponseDto(
                        existing.getId(),
                        existing.getNumberOfRows(),
                        existing.getNumberOfColumns(),
                        existing.getTotalCharacters(),
                        existing.getColumnStatistics().stream()
                                .map(s -> new ColumnStatistics(
                                        s.getColumnName(),
                                        s.getNullCount(),
                                        s.getUniqueCount(),
                                        s.isNumeric(),
                                        s.getMinValue(),
                                        s.getMaxValue(),
                                        s.getMeanValue(),
                                        s.getMedianValue(),
                                        s.getStandardDeviation(),
                                        s.isNumeric() ? Arrays.asList(
                                                s.getPercentile25(),
                                                s.getPercentile50(),
                                                s.getPercentile75(),
                                                s.getPercentile90(),
                                                s.getPercentile95(),
                                                s.getPercentile99()
                                        ) : null
                                ))
                                .toList(),
                        existing.getCreatedAt(),
                        true
                ))
                .orElseGet(() -> createNewAnalysis(data, contentHash));
    }


    private DataAnalysisResponseDto createNewAnalysis(String data, String contentHash) {

        String[] lines = data.split("\\R", -1);

        if (lines.length == 0 || lines[0].isBlank()) {
            throw new BadRequestException("Invalid CSV");
        }

        String[] headers = lines[0].split(",", -1);
        int numberOfColumns = headers.length;

        long estimatedCellCount = (long) (lines.length - 1) * numberOfColumns;
        if (estimatedCellCount > MAX_CELL_COUNT) {
            throw new BadRequestException("CSV exceeds maximum allowed cell count of one hundred thousand cells");
        }

        int numberOfRows = 0;
        int[] nullCounts = new int[numberOfColumns];
        long totalCharacters = data.length();

        @SuppressWarnings("unchecked")
        Set<String>[] uniqueValues = new Set[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) {
            uniqueValues[i] = new HashSet<>();
        }

        @SuppressWarnings("unchecked")
        List<Double>[] numericValues = new ArrayList[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) {
            numericValues[i] = new ArrayList<>();
        }

        boolean[] isNumericColumn = new boolean[numberOfColumns];
        Arrays.fill(isNumericColumn, true);

        for (int i = 1; i < lines.length; i++) {

            if (lines[i].isBlank()) {
                continue;
            }

            String[] values = lines[i].split(",", -1);

            if (values.length != numberOfColumns) {
                throw new BadRequestException("Invalid CSV");
            }

            numberOfRows++;

            for (int c = 0; c < numberOfColumns; c++) {
                if (values[c].isBlank()) {
                    nullCounts[c]++;
                } else {
                    uniqueValues[c].add(values[c].trim());

                    Double numericValue = tryParseDouble(values[c]);
                    if (numericValue != null) {
                        numericValues[c].add(numericValue);
                    } else {
                        isNumericColumn[c] = false;
                    }
                }
            }
        }

        Double[] minValues = new Double[numberOfColumns];
        Double[] maxValues = new Double[numberOfColumns];
        Double[] meanValues = new Double[numberOfColumns];
        Double[] medianValues = new Double[numberOfColumns];
        Double[] stdDevValues = new Double[numberOfColumns];
        Double[][] percentileValues = new Double[numberOfColumns][6];

        for (int c = 0; c < numberOfColumns; c++) {
            if (isNumericColumn[c] && !numericValues[c].isEmpty()) {
                List<Double> values = numericValues[c];
                Collections.sort(values);

                minValues[c] = values.get(0);
                maxValues[c] = values.get(values.size() - 1);
                meanValues[c] = calculateMean(values);
                medianValues[c] = calculateMedian(values);
                stdDevValues[c] = calculateStandardDeviation(values, meanValues[c]);

                percentileValues[c][0] = calculatePercentile(values, 25);
                percentileValues[c][1] = calculatePercentile(values, 50);
                percentileValues[c][2] = calculatePercentile(values, 75);
                percentileValues[c][3] = calculatePercentile(values, 90);
                percentileValues[c][4] = calculatePercentile(values, 95);
                percentileValues[c][5] = calculatePercentile(values, 99);
            } else {
                isNumericColumn[c] = false;
            }
        }

        OffsetDateTime createdAt = OffsetDateTime.now();

        DataAnalysisEntity dataAnalysisEntity = DataAnalysisEntity.builder()
                .originalData(data)
                .contentHash(contentHash)
                .numberOfRows(numberOfRows)
                .numberOfColumns(numberOfColumns)
                .totalCharacters(totalCharacters)
                .createdAt(createdAt)
                .build();

        dataAnalysisRepository.save(dataAnalysisEntity);

        List<ColumnStatisticsEntity> columnStatisticsEntities =
                IntStream.range(0, numberOfColumns)
                        .mapToObj(i -> ColumnStatisticsEntity.builder()
                                .dataAnalysis(dataAnalysisEntity)
                                .columnName(headers[i])
                                .nullCount(nullCounts[i])
                                .uniqueCount(uniqueValues[i].size())
                                .isNumeric(isNumericColumn[i])
                                .minValue(minValues[i])
                                .maxValue(maxValues[i])
                                .meanValue(meanValues[i])
                                .medianValue(medianValues[i])
                                .standardDeviation(stdDevValues[i])
                                .percentile25(percentileValues[i][0])
                                .percentile50(percentileValues[i][1])
                                .percentile75(percentileValues[i][2])
                                .percentile90(percentileValues[i][3])
                                .percentile95(percentileValues[i][4])
                                .percentile99(percentileValues[i][5])
                                .build())
                        .toList();

        columnStatisticsRepository.saveAll(columnStatisticsEntities);

        return new DataAnalysisResponseDto (
                dataAnalysisEntity.getId(),
                numberOfRows,
                numberOfColumns,
                totalCharacters,
                columnStatisticsEntities.stream()
                        .map(e -> new ColumnStatistics(
                                e.getColumnName(),
                                e.getNullCount(),
                                e.getUniqueCount(),
                                e.isNumeric(),
                                e.getMinValue(),
                                e.getMaxValue(),
                                e.getMeanValue(),
                                e.getMedianValue(),
                                e.getStandardDeviation(),
                                e.isNumeric() ? Arrays.asList(
                                        e.getPercentile25(),
                                        e.getPercentile50(),
                                        e.getPercentile75(),
                                        e.getPercentile90(),
                                        e.getPercentile95(),
                                        e.getPercentile99()
                                ) : null
                        ))
                        .toList(),
                createdAt,
                false
        );
    }


    public DataAnalysisResponseDto getAnalysisById(Long id) {

        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found"));

        return new DataAnalysisResponseDto(
                entity.getId(),
                entity.getNumberOfRows(),
                entity.getNumberOfColumns(),
                entity.getTotalCharacters(),
                entity.getColumnStatistics().stream()
                        .map(s -> new ColumnStatistics(
                                s.getColumnName(),
                                s.getNullCount(),
                                s.getUniqueCount(),
                                s.isNumeric(),
                                s.getMinValue(),
                                s.getMaxValue(),
                                s.getMeanValue(),
                                s.getMedianValue(),
                                s.getStandardDeviation(),
                                s.isNumeric() ? Arrays.asList(
                                        s.getPercentile25(),
                                        s.getPercentile50(),
                                        s.getPercentile75(),
                                        s.getPercentile90(),
                                        s.getPercentile95(),
                                        s.getPercentile99()
                                ) : null
                        ))
                        .toList(),
                entity.getCreatedAt(),
                true
        );
    }


    public void deleteAnalysisById(Long id) {
        if (!dataAnalysisRepository.existsById(id)) {
            throw new NotFoundException("Analysis not found");
        }
        dataAnalysisRepository.deleteById(id);
    }

}
