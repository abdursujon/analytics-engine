package com.sujon.analytics_engine.service.parser;

import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ParquetParser {

    private static final long MAX_CELL_COUNT = 1_000_000;
    private static final String DESCRIBE_PARQUET_SQL = "DESCRIBE SELECT * FROM read_parquet(?)";
    private static final String SELECT_PARQUET_SQL = "SELECT * FROM read_parquet(?)";

    public ParsedDatasetDto parseParquetBytesToDataset(byte[] parquetBytes) {
        Path tempParquetFile = null;
        try {
            tempParquetFile = writeBytesToTempParquetFile(parquetBytes);

            try (Connection duckDbConnection = DriverManager.getConnection("jdbc:duckdb:")) {
                ParquetSchema schema = readSchemaFromParquetFile(duckDbConnection, tempParquetFile);

                if (schema.columnHeaders.length == 0) {
                    throw new BadRequestException("Invalid Parquet");
                }

                return readRowsIntoDataset(duckDbConnection, tempParquetFile, schema, parquetBytes.length);
            }

        } catch (IOException | SQLException e) {
            throw new BadRequestException("Failed to read Parquet: " + e.getMessage());
        } finally {
            deleteTempFileQuietly(tempParquetFile);
        }
    }

    private Path writeBytesToTempParquetFile(byte[] parquetBytes) throws IOException {
        Path tempFile = Files.createTempFile("upload-", ".parquet");
        Files.write(tempFile, parquetBytes);
        return tempFile;
    }

    private ParquetSchema readSchemaFromParquetFile(Connection connection, Path parquetFile) throws SQLException {
        List<String> headerList = new ArrayList<>();
        List<Boolean> numericFlagList = new ArrayList<>();

        try (PreparedStatement describeStatement = connection.prepareStatement(DESCRIBE_PARQUET_SQL)) {
            describeStatement.setString(1, parquetFile.toString());
            try (ResultSet resultSet = describeStatement.executeQuery()) {
                while (resultSet.next()) {
                    headerList.add(resultSet.getString("column_name"));
                    numericFlagList.add(isDuckDbNumericType(resultSet.getString("column_type")));
                }
            }
        }

        int columnCount = headerList.size();
        String[] columnHeaders = headerList.toArray(new String[0]);
        boolean[] isNumericColumnFlags = new boolean[columnCount];
        for (int c = 0; c < columnCount; c++) {
            isNumericColumnFlags[c] = numericFlagList.get(c);
        }
        return new ParquetSchema(columnHeaders, isNumericColumnFlags);
    }

    private ParsedDatasetDto readRowsIntoDataset(
            Connection connection,
            Path parquetFile,
            ParquetSchema schema,
            long payloadSize
    ) throws SQLException {
        int columnCount = schema.columnHeaders.length;

        int[] nullCountPerColumn = new int[columnCount];
        @SuppressWarnings("unchecked")
        Set<String>[] uniqueValuesPerColumn = new Set[columnCount];
        @SuppressWarnings("unchecked")
        List<Double>[] numericValuesPerColumn = new ArrayList[columnCount];
        for (int c = 0; c < columnCount; c++) {
            uniqueValuesPerColumn[c] = new HashSet<>();
            numericValuesPerColumn[c] = new ArrayList<>();
        }

        int rowCount = 0;
        try (PreparedStatement selectStatement = connection.prepareStatement(SELECT_PARQUET_SQL)) {
            selectStatement.setString(1, parquetFile.toString());
            try (ResultSet resultSet = selectStatement.executeQuery()) {
                while (resultSet.next()) {
                    rowCount++;

                    long cellsAccumulated = (long) rowCount * columnCount;
                    if (cellsAccumulated > MAX_CELL_COUNT) {
                        throw new BadRequestException(
                                "Parquet exceeds maximum allowed cell count of one million cells");
                    }

                    for (int c = 0; c < columnCount; c++) {
                        String cellValue = resultSet.getString(c + 1);

                        if (resultSet.wasNull() || cellValue == null || cellValue.isBlank()) {
                            nullCountPerColumn[c]++;
                            continue;
                        }

                        uniqueValuesPerColumn[c].add(cellValue.trim());

                        if (schema.isNumericColumnFlags[c]) {
                            Double numericCellValue = tryParseStringToDouble(cellValue);
                            if (numericCellValue != null) {
                                numericValuesPerColumn[c].add(numericCellValue);
                            }
                        }
                    }
                }
            }
        }

        return new ParsedDatasetDto(
                schema.columnHeaders,
                rowCount,
                payloadSize,
                nullCountPerColumn,
                uniqueValuesPerColumn,
                numericValuesPerColumn,
                schema.isNumericColumnFlags
        );
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) return;
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
    }

    private boolean isDuckDbNumericType(String duckDbTypeName) {
        if (duckDbTypeName == null) return false;
        String upperType = duckDbTypeName.toUpperCase();
        return upperType.startsWith("TINYINT")   || upperType.startsWith("SMALLINT")
                || upperType.startsWith("INTEGER")   || upperType.startsWith("BIGINT")
                || upperType.startsWith("HUGEINT")   || upperType.startsWith("UTINYINT")
                || upperType.startsWith("USMALLINT") || upperType.startsWith("UINTEGER")
                || upperType.startsWith("UBIGINT")   || upperType.startsWith("REAL")
                || upperType.startsWith("FLOAT")     || upperType.startsWith("DOUBLE")
                || upperType.startsWith("DECIMAL")   || upperType.startsWith("NUMERIC");
    }

    private Double tryParseStringToDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ParquetSchema(String[] columnHeaders, boolean[] isNumericColumnFlags) {}
}