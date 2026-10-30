package com.sujon.analytics_engine.service.parser;

import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class JsonParserUnitTest {

    private final JsonParser jsonParser = new JsonParser();

    private String readTestResourceAsString(String resourcePath) throws IOException {
        return new String(getClass().getClassLoader().getResourceAsStream(resourcePath).readAllBytes());
    }

    @Test
    void parseJsonBodyToDataset_shouldMarkAllColumnsNumeric_whenJsonArrayHasOnlyNumbers() throws IOException {
        ParsedDatasetDto parsedDataset = jsonParser.parseJsonBodyToDataset(
                readTestResourceAsString("test-data/json/all-numeric.json"), false);

        assertArrayEquals(new String[]{"id", "score", "rank"}, parsedDataset.columnHeaders());
        assertEquals(4, parsedDataset.totalRowCount());
        assertArrayEquals(new boolean[]{true, true, true}, parsedDataset.isNumericColumnFlags());
        assertEquals(List.of(10.5, 20.5, 30.5, 40.5), parsedDataset.numericValuesPerColumn()[1]);
    }

    @Test
    void parseJsonBodyToDataset_shouldCountNullsAndInferText_whenValuesAreMixed() throws IOException {
        ParsedDatasetDto parsedDataset = jsonParser.parseJsonBodyToDataset(
                readTestResourceAsString("test-data/json/mixed-null.json"), false);

        assertArrayEquals(new int[]{2, 2, 2}, parsedDataset.nullCountPerColumn());
        assertArrayEquals(new boolean[]{true, false, true}, parsedDataset.isNumericColumnFlags());
    }

    @Test
    void parseJsonBodyToDataset_shouldParseEachLine_whenNdjsonProvided() throws IOException {
        ParsedDatasetDto parsedDataset = jsonParser.parseJsonBodyToDataset(
                readTestResourceAsString("test-data/ndjson/all-numeric.ndjson"), true);

        assertEquals(4, parsedDataset.totalRowCount());
        assertArrayEquals(new String[]{"id", "score", "rank"}, parsedDataset.columnHeaders());
    }

    @Test
    void parseJsonBodyToDataset_shouldThrowBadRequest_whenRecordsContainNestedObjects() throws IOException {
        String nestedJson = readTestResourceAsString("test-data/json/nested.json");
        assertThrows(BadRequestException.class, () -> jsonParser.parseJsonBodyToDataset(nestedJson, false));
    }

    @Test
    void parseJsonBodyToDataset_shouldThrowBadRequest_whenArrayIsEmpty() {
        assertThrows(BadRequestException.class, () -> jsonParser.parseJsonBodyToDataset("[]", false));
    }

    @Test
    void parseJsonBodyToDataset_shouldThrowBadRequest_whenJsonIsMalformed() {
        assertThrows(BadRequestException.class, () -> jsonParser.parseJsonBodyToDataset("[{\"id\": 1,", false));
    }
}
