package com.sujon.analytics_engine.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
public class ColumnStatisticsUnitTest {

    private String csvData;

    @Test
    void shouldReturnExpectedColumnStatisticsDataResponse() throws Exception{

        csvData = new String(
                getClass().getClassLoader().getResourceAsStream("test-data/csv/large.csv").readAllBytes()
        );

        ColumnStatistics columnStatistics= new ColumnStatistics(
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
        );

        assertEquals("driver", columnStatistics.columnName());
        assertEquals(0, columnStatistics.nullCount());
        assertEquals(2, columnStatistics.uniqueCount());
        assertFalse(columnStatistics.isNumeric());
        assertNull(columnStatistics.min());
        assertNull(columnStatistics.max());
        assertNull(columnStatistics.mean());
        assertNull(columnStatistics.median());
        assertNull(columnStatistics.standardDeviation());
        assertNull(columnStatistics.percentiles());
    }
}
