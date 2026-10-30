package com.sujon.analytics_engine.service.parser;

import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class CsvParserUnitTest {

    private final CsvParser csvParser = new CsvParser();

    private String readTestResourceAsString(String resourcePath) throws IOException {
        return new String(getClass().getClassLoader().getResourceAsStream(resourcePath).readAllBytes());
    }

    @Test
    void parseCsvBodyToDataset_shouldInferColumnTypes_whenValidCsvProvided() throws IOException {
        ParsedDatasetDto parsedDataset = csvParser.parseCsvBodyToDataset(readTestResourceAsString("test-data/csv/simple.csv"));

        assertArrayEquals(new String[]{"driver", "number", "team"}, parsedDataset.columnHeaders());
        assertEquals(3, parsedDataset.totalRowCount());
        assertArrayEquals(new boolean[]{false, true, false}, parsedDataset.isNumericColumnFlags());
        assertEquals(List.of(1.0, 44.0, 16.0), parsedDataset.numericValuesPerColumn()[1]);
    }

    @Test
    void parseCsvBodyToDataset_shouldCountNullsAndUniqueValues_whenCellsAreBlank() throws IOException {
        ParsedDatasetDto parsedDataset = csvParser.parseCsvBodyToDataset(readTestResourceAsString("test-data/csv/with-nulls.csv"));

        assertArrayEquals(new int[]{0, 2, 2, 0}, parsedDataset.nullCountPerColumn());
        assertEquals(4, parsedDataset.uniqueValuesPerColumn()[0].size());
        assertEquals(3, parsedDataset.uniqueValuesPerColumn()[3].size());
        assertTrue(parsedDataset.isNumericColumnFlags()[1]);
    }

    @Test
    void parseCsvBodyToDataset_shouldReturnZeroRows_whenOnlyHeaderProvided() {
        ParsedDatasetDto parsedDataset = csvParser.parseCsvBodyToDataset("driver,number,team\n");

        assertEquals(0, parsedDataset.totalRowCount());
        assertEquals(3, parsedDataset.columnHeaders().length);
    }

    @Test
    void parseCsvBodyToDataset_shouldThrowBadRequest_whenRowWidthDoesNotMatchHeader() throws IOException {
        String invalidCsv = readTestResourceAsString("test-data/csv/invalid.csv");
        assertThrows(BadRequestException.class, () -> csvParser.parseCsvBodyToDataset(invalidCsv));
    }

    @Test
    void parseCsvBodyToDataset_shouldThrowBadRequest_whenHeaderIsBlank() {
        assertThrows(BadRequestException.class, () -> csvParser.parseCsvBodyToDataset(""));
    }

    @Test
    void parseCsvBodyToDataset_shouldThrowBadRequest_whenCellCountExceedsLimit() {
        String oversizedCsv = "a,b\n" + "1,2\n".repeat(500_001);
        assertThrows(BadRequestException.class, () -> csvParser.parseCsvBodyToDataset(oversizedCsv));
    }
}
