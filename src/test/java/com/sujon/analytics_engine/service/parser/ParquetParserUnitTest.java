package com.sujon.analytics_engine.service.parser;

import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class ParquetParserUnitTest {

    private final ParquetParser parquetParser = new ParquetParser();

    private byte[] readTestResourceAsBytes(String resourcePath) throws IOException {
        return getClass().getClassLoader().getResourceAsStream(resourcePath).readAllBytes();
    }

    @Test
    void parseParquetBytesToDataset_shouldReadRowsAndNumericValues_whenValidParquetProvided() throws IOException {
        ParsedDatasetDto parsedDataset = parquetParser.parseParquetBytesToDataset(
                readTestResourceAsBytes("test-data/parquet/all-numeric.parquet"));

        assertArrayEquals(new String[]{"id", "score"}, parsedDataset.columnHeaders());
        assertEquals(4, parsedDataset.totalRowCount());
        assertArrayEquals(new boolean[]{true, true}, parsedDataset.isNumericColumnFlags());
        assertEquals(List.of(10.5, 20.5, 30.5, 40.5), parsedDataset.numericValuesPerColumn()[1]);
    }

    @Test
    void parseParquetBytesToDataset_shouldInferTypesFromSchema_whenColumnsAreMixed() throws IOException {
        ParsedDatasetDto parsedDataset = parquetParser.parseParquetBytesToDataset(
                readTestResourceAsBytes("test-data/parquet/mixed-null.parquet"));

        assertArrayEquals(new String[]{"id", "name", "score"}, parsedDataset.columnHeaders());
        assertArrayEquals(new boolean[]{true, false, true}, parsedDataset.isNumericColumnFlags());
        assertTrue(parsedDataset.nullCountPerColumn()[1] > 0);
    }

    @Test
    void parseParquetBytesToDataset_shouldThrowBadRequest_whenBytesAreNotParquet() throws IOException {
        byte[] invalidParquetBytes = readTestResourceAsBytes("test-data/parquet/invalid.parquet");
        assertThrows(BadRequestException.class, () -> parquetParser.parseParquetBytesToDataset(invalidParquetBytes));
    }

    @Test
    void parseParquetBytesToDataset_shouldThrowBadRequest_whenFileIsTruncated() throws IOException {
        byte[] truncatedParquetBytes = readTestResourceAsBytes("test-data/parquet/truncated.parquet");
        assertThrows(BadRequestException.class, () -> parquetParser.parseParquetBytesToDataset(truncatedParquetBytes));
    }
}
