package com.sujon.analytics_engine.service.stats;

import com.sujon.analytics_engine.dto.NumericColumnStatsDto;
import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class NumericStatsCalculatorUnitTest {

    private static final double TOLERANCE = 1e-9;

    private final NumericStatsCalculator numericStatsCalculator = new NumericStatsCalculator();

    @SuppressWarnings("unchecked")
    private ParsedDatasetDto buildDatasetWithOneNumericAndOneTextColumn(List<Double> numericColumnValues) {
        Set<String>[] uniqueValuesPerColumn = new Set[]{new HashSet<>(), new HashSet<>()};
        List<Double>[] numericValuesPerColumn = new List[]{new ArrayList<>(numericColumnValues), new ArrayList<>()};
        return new ParsedDatasetDto(
                new String[]{"score", "name"},
                numericColumnValues.size(),
                0,
                new int[]{0, 0},
                uniqueValuesPerColumn,
                numericValuesPerColumn,
                new boolean[]{true, false}
        );
    }

    @Test
    void calculateNumericStatsPerColumn_shouldReturnDescriptiveStatistics_whenColumnIsNumeric() {
        NumericColumnStatsDto[] statsPerColumn = numericStatsCalculator.calculateNumericStatsPerColumn(
                buildDatasetWithOneNumericAndOneTextColumn(List.of(4.0, 1.0, 3.0, 2.0)));

        NumericColumnStatsDto scoreStats = statsPerColumn[0];
        assertEquals(1.0, scoreStats.minValue(), TOLERANCE);
        assertEquals(4.0, scoreStats.maxValue(), TOLERANCE);
        assertEquals(2.5, scoreStats.meanValue(), TOLERANCE);
        assertEquals(2.5, scoreStats.medianValue(), TOLERANCE);
        assertEquals(Math.sqrt(1.25), scoreStats.standardDeviation(), TOLERANCE);
    }

    @Test
    void calculateNumericStatsPerColumn_shouldInterpolatePercentiles_whenValuesFallBetweenIndices() {
        NumericColumnStatsDto[] statsPerColumn = numericStatsCalculator.calculateNumericStatsPerColumn(
                buildDatasetWithOneNumericAndOneTextColumn(List.of(1.0, 2.0, 3.0, 4.0)));

        List<Double> expectedPercentiles = List.of(1.75, 2.5, 3.25, 3.7, 3.85, 3.97);
        List<Double> actualPercentiles = statsPerColumn[0].percentiles();
        for (int i = 0; i < expectedPercentiles.size(); i++) {
            assertEquals(expectedPercentiles.get(i), actualPercentiles.get(i), TOLERANCE);
        }
    }

    @Test
    void calculateNumericStatsPerColumn_shouldReturnMiddleValueAsMedian_whenCountIsOdd() {
        NumericColumnStatsDto[] statsPerColumn = numericStatsCalculator.calculateNumericStatsPerColumn(
                buildDatasetWithOneNumericAndOneTextColumn(List.of(9.0, 1.0, 5.0)));

        assertEquals(5.0, statsPerColumn[0].medianValue(), TOLERANCE);
    }

    @Test
    void calculateNumericStatsPerColumn_shouldReturnZeroDeviation_whenSingleValueProvided() {
        NumericColumnStatsDto[] statsPerColumn = numericStatsCalculator.calculateNumericStatsPerColumn(
                buildDatasetWithOneNumericAndOneTextColumn(List.of(7.0)));

        assertEquals(0.0, statsPerColumn[0].standardDeviation(), TOLERANCE);
        assertTrue(statsPerColumn[0].percentiles().stream().allMatch(percentile -> percentile == 7.0));
    }

    @Test
    void calculateNumericStatsPerColumn_shouldReturnNull_whenColumnIsTextOrEmpty() {
        NumericColumnStatsDto[] statsPerColumn = numericStatsCalculator.calculateNumericStatsPerColumn(
                buildDatasetWithOneNumericAndOneTextColumn(List.of()));

        assertNull(statsPerColumn[0]);
        assertNull(statsPerColumn[1]);
    }
}
