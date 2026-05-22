package com.sujon.analytics_engine.service;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import com.sujon.analytics_engine.exception.NotFoundException;
import com.sujon.analytics_engine.model.ColumnStatistics;
import com.sujon.analytics_engine.model.DataFormat;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.Map;

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
    String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed");
        }
    }

    /** SHA-256 over raw bytes — for binary formats. */
    //=======MUST BE TESTED=============/
    String sha256Bytes(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed");
        }
    }


    /** True for DuckDB type strings that represent numeric columns. */
    //=======MUST BE TESTED=============/
    boolean isNumericType(String duckDbType) {
        if (duckDbType == null) return false;
        String t = duckDbType.toUpperCase();
        return t.startsWith("TINYINT")  || t.startsWith("SMALLINT")
                || t.startsWith("INTEGER")  || t.startsWith("BIGINT")
                || t.startsWith("HUGEINT")  || t.startsWith("UTINYINT")
                || t.startsWith("USMALLINT")|| t.startsWith("UINTEGER")
                || t.startsWith("UBIGINT")  || t.startsWith("REAL")
                || t.startsWith("FLOAT")    || t.startsWith("DOUBLE")
                || t.startsWith("DECIMAL")  || t.startsWith("NUMERIC");
    }


    /**
     * Normalizes CSV content for consistent hashing.
     * @param data the raw CSV content
     * @return normalized CSV content suitable for hashing
     */
    String normalizeForHash(String data) {
        return data
                .replace("\r\n", "\n")
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }


   Double tryParseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    Double calculateMean(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        for (Double value : values) {
            sum += value;
        }
        return sum / values.size();
    }


     Double calculateMedian(List<Double> sortedValues) {
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


    Double calculateStandardDeviation(List<Double> values, Double mean) {
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


    Double calculatePercentile(List<Double> sortedValues, double percentile) {
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


    //=======MUST BE TESTED=============/
    private DataAnalysisResponseDto toResponseDto(DataAnalysisEntity entity) {
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
                true,
                entity.getFormat()
        );
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
                .map(this::toResponseDto)
                .orElseGet(() -> createNewAnalysis(data, contentHash));
    }

    //=======MUST BE TESTED=============/
    public DataAnalysisResponseDto analyseParquetData(byte[] bytes) {

        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Invalid Parquet");
        }

        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum allowed size of 5MB");
        }

        String contentHash = sha256Bytes(bytes);

        return dataAnalysisRepository.findByContentHash(contentHash)
                .map(this::toResponseDto)                          // see note below
                .orElseGet(() -> createNewParquetAnalysis(bytes, contentHash));
    }

    //=======MUST BE TESTED=============/
    private DataAnalysisResponseDto createNewParquetAnalysis(byte[] bytes, String contentHash) {

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("upload-", ".parquet");
            Files.write(tempFile, bytes);

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {

                // 1. Read schema → detect numeric columns up-front
                String[] headers;
                boolean[] isNumericColumn;
                int numberOfColumns;

                try (PreparedStatement describe = conn.prepareStatement(
                        "DESCRIBE SELECT * FROM read_parquet(?)")) {
                    describe.setString(1, tempFile.toString());
                    try (ResultSet rs = describe.executeQuery()) {
                        List<String> names = new ArrayList<>();
                        List<Boolean> numericFlags = new ArrayList<>();
                        while (rs.next()) {
                            names.add(rs.getString("column_name"));
                            numericFlags.add(isNumericType(rs.getString("column_type")));
                        }
                        numberOfColumns = names.size();
                        headers = names.toArray(new String[0]);
                        isNumericColumn = new boolean[numberOfColumns];
                        for (int i = 0; i < numberOfColumns; i++) {
                            isNumericColumn[i] = numericFlags.get(i);
                        }
                    }
                }

                if (numberOfColumns == 0) {
                    throw new BadRequestException("Invalid Parquet");
                }

                // 2. Prepare per-column structures (same shape as CSV path)
                int[] nullCounts = new int[numberOfColumns];
                @SuppressWarnings("unchecked")
                Set<String>[] uniqueValues = new Set[numberOfColumns];
                @SuppressWarnings("unchecked")
                List<Double>[] numericValues = new ArrayList[numberOfColumns];
                for (int i = 0; i < numberOfColumns; i++) {
                    uniqueValues[i] = new HashSet<>();
                    numericValues[i] = new ArrayList<>();
                }

                // 3. Iterate rows
                int numberOfRows = 0;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT * FROM read_parquet(?)")) {
                    select.setString(1, tempFile.toString());
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            numberOfRows++;

                            long cellsSoFar = (long) numberOfRows * numberOfColumns;
                            if (cellsSoFar > MAX_CELL_COUNT) {
                                throw new BadRequestException(
                                        "Parquet exceeds maximum allowed cell count of one million cells");
                            }

                            for (int c = 0; c < numberOfColumns; c++) {
                                String value = rs.getString(c + 1);   // JDBC is 1-indexed
                                if (rs.wasNull() || value == null || value.isBlank()) {
                                    nullCounts[c]++;
                                } else {
                                    uniqueValues[c].add(value.trim());
                                    if (isNumericColumn[c]) {
                                        Double num = tryParseDouble(value);
                                        if (num != null) {
                                            numericValues[c].add(num);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                return computeAndPersistStats(
                        headers,
                        numberOfRows,
                        bytes.length,                  // use byte size for Parquet
                        contentHash,
                        null,                          // no raw text for binary format
                        DataFormat.PARQUET,
                        nullCounts,
                        uniqueValues,
                        numericValues,
                        isNumericColumn
                );
            }

        } catch (IOException | SQLException e) {
            throw new BadRequestException("Failed to read Parquet: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
    }

    //=======MUST BE TESTED=============/
    public DataAnalysisResponseDto analyseJsonData(String body, boolean isNdjson) {

        if (body == null || body.isBlank()) {
            throw new BadRequestException("Invalid JSON");
        }

        long fileSizeBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum allowed size of 5MB");
        }

        String contentHash = sha256(body);

        return dataAnalysisRepository.findByContentHash(contentHash)
                .map(this::toResponseDto)
                .orElseGet(() -> createNewJsonAnalysis(body, contentHash, isNdjson));
    }

    //=======MUST BE TESTED=============/
    private DataAnalysisResponseDto createNewJsonAnalysis(String body, String contentHash, boolean isNdjson) {

        List<Map<String, Object>> records = parseJsonRecords(body, isNdjson);

        if (records.isEmpty()) {
            throw new BadRequestException("JSON contains no records");
        }

        // Ordered union of keys across all records
        LinkedHashSet<String> headerSet = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            headerSet.addAll(record.keySet());
        }
        String[] headers = headerSet.toArray(new String[0]);
        int numberOfColumns = headers.length;

        if (numberOfColumns == 0) {
            throw new BadRequestException("JSON records have no fields");
        }

        long estimatedCellCount = (long) records.size() * numberOfColumns;
        if (estimatedCellCount > MAX_CELL_COUNT) {
            throw new BadRequestException("JSON exceeds maximum allowed cell count of one million cells");
        }

        int[] nullCounts = new int[numberOfColumns];
        @SuppressWarnings("unchecked")
        Set<String>[] uniqueValues = new Set[numberOfColumns];
        @SuppressWarnings("unchecked")
        List<Double>[] numericValues = new ArrayList[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) {
            uniqueValues[i] = new HashSet<>();
            numericValues[i] = new ArrayList<>();
        }
        boolean[] isNumericColumn = new boolean[numberOfColumns];
        Arrays.fill(isNumericColumn, true);

        int numberOfRows = 0;
        for (Map<String, Object> record : records) {
            numberOfRows++;
            for (int c = 0; c < numberOfColumns; c++) {
                Object value = record.get(headers[c]);

                if (value == null) {
                    nullCounts[c]++;
                    continue;
                }

                if (value instanceof Map || value instanceof List) {
                    throw new BadRequestException(
                            "Nested objects/arrays not supported in column: " + headers[c]);
                }

                uniqueValues[c].add(value.toString().trim());

                if (value instanceof Number) {
                    numericValues[c].add(((Number) value).doubleValue());
                } else {
                    isNumericColumn[c] = false;
                }
            }
        }

        return computeAndPersistStats(
                headers,
                numberOfRows,
                body.length(),
                contentHash,
                body,
                DataFormat.JSON,
                nullCounts,
                uniqueValues,
                numericValues,
                isNumericColumn
        );
    }

    //=======MUST BE TESTED=============/
    private List<Map<String, Object>> parseJsonRecords(String body, boolean isNdjson) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            if (isNdjson) {
                List<Map<String, Object>> records = new ArrayList<>();
                for (String line : body.split("\\R")) {
                    if (line.isBlank()) continue;
                    records.add(mapper.readValue(line, new TypeReference<>() {}));
                }
                return records;
            } else {
                return mapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse JSON: " + e.getMessage());
        }
    }


    DataAnalysisResponseDto createNewAnalysis(String data, String contentHash) {

        String[] lines = data.split("\\R", -1);

        if (lines.length == 0 || lines[0].isBlank()) {
            throw new BadRequestException("Invalid CSV");
        }

        String[] headers = lines[0].split(",", -1);
        int numberOfColumns = headers.length;

        long estimatedCellCount = (long) (lines.length - 1) * numberOfColumns;
        if (estimatedCellCount > MAX_CELL_COUNT) {
            throw new BadRequestException("CSV exceeds maximum allowed cell count of one million cells");
        }

        int numberOfRows = 0;
        int[] nullCounts = new int[numberOfColumns];
        long totalCharacters = data.length();

        @SuppressWarnings("unchecked")
        Set<String>[] uniqueValues = new Set[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) uniqueValues[i] = new HashSet<>();

        @SuppressWarnings("unchecked")
        List<Double>[] numericValues = new ArrayList[numberOfColumns];
        for (int i = 0; i < numberOfColumns; i++) numericValues[i] = new ArrayList<>();

        boolean[] isNumericColumn = new boolean[numberOfColumns];
        Arrays.fill(isNumericColumn, true);

        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;

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

        return computeAndPersistStats(
                headers,
                numberOfRows,
                totalCharacters,
                contentHash,
                data,
                DataFormat.CSV,
                nullCounts,
                uniqueValues,
                numericValues,
                isNumericColumn
        );
    }


    /**
     * Computes column-level statistics, persists the analysis to the database, and builds the response DTO.
     *
     * <p>This is the shared core used by all format-specific ingestion paths (CSV, Parquet, JSON).
     * Callers are responsible for parsing the input format and producing the format-agnostic
     * intermediate representation (the per-column arrays); this method handles everything from
     * statistical aggregation through persistence and response construction.
     *
     * <p>For each column flagged as numeric in {@code isNumericColumn}, the method sorts the
     * collected numeric values and computes: min, max, mean, median, standard deviation, and the
     * 25th/50th/75th/90th/95th/99th percentiles. Non-numeric columns receive only null/unique counts.
     *
     * @param headers           column names in order
     * @param numberOfRows      total non-blank data rows processed by the caller
     * @param totalCharacters   size of the original payload (characters for text formats, bytes for binary)
     * @param contentHash       SHA-256 of the normalized input, used for deduplication
     * @param originalData      raw source text; {@code null} for binary formats (e.g. Parquet)
     * @param format            source format of the upload (CSV, PARQUET, JSON)
     * @param nullCounts        count of blank/null cells per column
     * @param uniqueValues      distinct non-blank values per column
     * @param numericValues     parsed numeric values per column (only populated where applicable)
     * @param isNumericColumn   per-column flag: {@code true} if all non-blank values parsed as numeric
     * @return a fully populated response DTO with {@code alreadyExists = false}
     */
    //=======MUST BE TESTED=============/
    private DataAnalysisResponseDto computeAndPersistStats(
            String[] headers,
            int numberOfRows,
            long totalCharacters,
            String contentHash,
            String originalData,
            DataFormat format,
            int[] nullCounts,
            Set<String>[] uniqueValues,
            List<Double>[] numericValues,
            boolean[] isNumericColumn
    ) {

        int numberOfColumns = headers.length;

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
                .originalData(originalData)
                .contentHash(contentHash)
                .numberOfRows(numberOfRows)
                .numberOfColumns(numberOfColumns)
                .totalCharacters(totalCharacters)
                .format(format)
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

        return new DataAnalysisResponseDto(
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
                false,
                format
        );

    }

    //=======MUST BE TESTED=============/
    public DataAnalysisResponseDto getAnalysisById(Long id) {

        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found"));

        return toResponseDto(entity);
    }


    public void deleteAnalysisById(Long id) {
        if (!dataAnalysisRepository.existsById(id)) {
            throw new NotFoundException("Analysis not found");
        }
        dataAnalysisRepository.deleteById(id);
    }

}
