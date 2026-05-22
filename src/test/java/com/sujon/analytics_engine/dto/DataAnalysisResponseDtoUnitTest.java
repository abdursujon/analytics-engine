package com.sujon.analytics_engine.dto;

import com.sujon.analytics_engine.model.ColumnStatistics;
import com.sujon.analytics_engine.model.DataFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class DataAnalysisResponseDtoUnitTest {

    private DataAnalysisResponseDto sampleResponse;
    private String csvData;

    @Test
    void shouldReturnExpectedSampleResponse() throws Exception{

        csvData = new String(
                getClass().getClassLoader().getResourceAsStream("test-data/csv/large.csv").readAllBytes()
        );

        String[] line = csvData.split("\\R");
        int numberOfRows = line.length - 1;
        int numberOfColumns = line[0].split(",").length;
        int totalCharacters = csvData.length();

        sampleResponse = new DataAnalysisResponseDto(
                1L,
                numberOfRows,
                numberOfColumns,
                totalCharacters,
                List.of(new ColumnStatistics(
                        "driver",
                        0,
                        2,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )),
                OffsetDateTime.now(),
                false,
                DataFormat.CSV
        );

        assertEquals(1L, sampleResponse.id());
        assertEquals(10, sampleResponse.numberOfRows());
        assertEquals(6, sampleResponse.numberOfColumns());
        assertEquals(460, sampleResponse.totalCharacters());

        ColumnStatistics stats = sampleResponse.columnStatistics().get(0);
        assertEquals("driver", stats.columnName());
        assertEquals(0, stats.nullCount());
        assertEquals(2, stats.uniqueCount());
        assertFalse(stats.isNumeric());
        assertNull(stats.min());
        assertNull(stats.max());
        assertNull(stats.mean());
        assertNull(stats.median());
        assertNull(stats.standardDeviation());
        assertNull(stats.percentiles());

        assertFalse(sampleResponse.alreadyExists());
        System.out.println(sampleResponse);
    }
}
