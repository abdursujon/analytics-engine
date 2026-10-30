package com.sujon.analytics_engine.service.stats;

import com.sujon.analytics_engine.dto.NumericCorrelationMatrixDto;
import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
public class NumericCorrelationCalculatorUnitTest {

    private static final double TOLERANCE = 1e-9;

    private final NumericCorrelationCalculator numericCorrelationCalculator = new NumericCorrelationCalculator();

    @SuppressWarnings("unchecked")
    private ParsedDatasetDto buildDatasetFromColumns(
            String[] columnHeaders,
            List<List<Double>> numericValuesForEachColumn,
            boolean[] isNumericColumnFlags
    ) {
        int columnCount = columnHeaders.length;
        Set<String>[] uniqueValuesPerColumn = new Set[columnCount];
        List<Double>[] numericValuesPerColumn = new List[columnCount];
        for (int c = 0; c < columnCount; c++) {
            uniqueValuesPerColumn[c] = new HashSet<>();
            numericValuesPerColumn[c] = new ArrayList<>(numericValuesForEachColumn.get(c));
        }
        return new ParsedDatasetDto(
                columnHeaders,
                numericValuesForEachColumn.get(0).size(),
                0,
                new int[columnCount],
                uniqueValuesPerColumn,
                numericValuesPerColumn,
                isNumericColumnFlags
        );
    }

    @Test
    void calculateCorrelationMatrix_shouldReturnPerfectCorrelations_whenColumnsAreLinear() {
        ParsedDatasetDto parsedDataset = buildDatasetFromColumns(
                new String[]{"x", "doubleX", "reverseX"},
                List.of(List.of(1.0, 2.0, 3.0, 4.0), List.of(2.0, 4.0, 6.0, 8.0), List.of(4.0, 3.0, 2.0, 1.0)),
                new boolean[]{true, true, true});

        NumericCorrelationMatrixDto correlationMatrix = numericCorrelationCalculator.calculateCorrelationMatrix(parsedDataset);

        assertEquals(List.of("x", "doubleX", "reverseX"), correlationMatrix.numericColumnNames());
        assertEquals(1.0, correlationMatrix.correlationValues().get(0).get(1), TOLERANCE);
        assertEquals(-1.0, correlationMatrix.correlationValues().get(0).get(2), TOLERANCE);
        assertEquals(correlationMatrix.correlationValues().get(0).get(1), correlationMatrix.correlationValues().get(1).get(0));
        assertEquals(1.0, correlationMatrix.correlationValues().get(2).get(2), TOLERANCE);
        assertEquals(3, correlationMatrix.strongPairs().size());
    }

    @Test
    void calculateCorrelationMatrix_shouldReturnModerateCoefficient_whenColumnsArePartiallyRelated() {
        ParsedDatasetDto parsedDataset = buildDatasetFromColumns(
                new String[]{"x", "y"},
                List.of(List.of(1.0, 2.0, 3.0, 4.0, 5.0), List.of(2.0, 1.0, 4.0, 3.0, 5.0)),
                new boolean[]{true, true});

        NumericCorrelationMatrixDto correlationMatrix = numericCorrelationCalculator.calculateCorrelationMatrix(parsedDataset);

        assertEquals(0.8, correlationMatrix.correlationValues().get(0).get(1), TOLERANCE);
        assertTrue(correlationMatrix.strongPairs().isEmpty());
    }

    @Test
    void calculateCorrelationMatrix_shouldExcludeTextColumns_whenDatasetIsMixed() {
        ParsedDatasetDto parsedDataset = buildDatasetFromColumns(
                new String[]{"name", "score"},
                List.of(List.of(), List.of(1.0, 2.0)),
                new boolean[]{false, true});

        NumericCorrelationMatrixDto correlationMatrix = numericCorrelationCalculator.calculateCorrelationMatrix(parsedDataset);

        assertEquals(List.of("score"), correlationMatrix.numericColumnNames());
        assertEquals(1, correlationMatrix.correlationValues().size());
    }

    @Test
    void calculateCorrelationMatrix_shouldWarnAndReturnZero_whenColumnIsConstant() {
        ParsedDatasetDto parsedDataset = buildDatasetFromColumns(
                new String[]{"x", "constant"},
                List.of(List.of(1.0, 2.0, 3.0), List.of(5.0, 5.0, 5.0)),
                new boolean[]{true, true});

        NumericCorrelationMatrixDto correlationMatrix = numericCorrelationCalculator.calculateCorrelationMatrix(parsedDataset);

        assertEquals(0.0, correlationMatrix.correlationValues().get(0).get(1), TOLERANCE);
        assertEquals(1, correlationMatrix.warnings().size());
        assertTrue(correlationMatrix.warnings().get(0).contains("constant"));
    }
}
