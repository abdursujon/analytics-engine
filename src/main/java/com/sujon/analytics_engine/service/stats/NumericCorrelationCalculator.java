package com.sujon.analytics_engine.service.stats;

import com.sujon.analytics_engine.dto.NumericCorrelationMatrixDto;
import com.sujon.analytics_engine.dto.NumericCorrelationMatrixDto.CorrelationPair;
import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class NumericCorrelationCalculator {

    private static final double STRONG_CORRELATION_THRESHOLD = 0.95;

    public NumericCorrelationMatrixDto calculateCorrelationMatrix(ParsedDatasetDto parsedDataset) {
        List<Integer> numericColumnIndices = collectNumericColumnIndices(parsedDataset);
        List<String> numericColumnNames = collectNumericColumnNames(parsedDataset, numericColumnIndices);
        List<String> warnings = new ArrayList<>();

        List<List<Double>> normalisedValuesPerColumn =
                collectAndValidateNumericValues(parsedDataset, numericColumnIndices, numericColumnNames, warnings);

        int columnCount = numericColumnNames.size();
        List<List<Double>> correlationValues = buildEmptyMatrix(columnCount);
        List<CorrelationPair> strongPairs = new ArrayList<>();

        for (int i = 0; i < columnCount; i++) {
            correlationValues.get(i).set(i, 1.0);
            for (int j = i + 1; j < columnCount; j++) {
                double pearsonR = calculatePearsonCorrelation(
                        normalisedValuesPerColumn.get(i),
                        normalisedValuesPerColumn.get(j));

                correlationValues.get(i).set(j, pearsonR);
                correlationValues.get(j).set(i, pearsonR);

                if (Math.abs(pearsonR) >= STRONG_CORRELATION_THRESHOLD) {
                    strongPairs.add(new CorrelationPair(
                            numericColumnNames.get(i),
                            numericColumnNames.get(j),
                            roundToTwoDecimals(pearsonR)));
                }
            }
        }

        return new NumericCorrelationMatrixDto(numericColumnNames, correlationValues, strongPairs, warnings);
    }

    private List<Integer> collectNumericColumnIndices(ParsedDatasetDto parsedDataset) {
        List<Integer> indices = new ArrayList<>();
        for (int c = 0; c < parsedDataset.columnHeaders().length; c++) {
            if (parsedDataset.isNumericColumnFlags()[c]
                    && !parsedDataset.numericValuesPerColumn()[c].isEmpty()) {
                indices.add(c);
            }
        }
        return indices;
    }

    private List<String> collectNumericColumnNames(ParsedDatasetDto parsedDataset, List<Integer> indices) {
        List<String> names = new ArrayList<>(indices.size());
        for (int columnIndex : indices) {
            names.add(parsedDataset.columnHeaders()[columnIndex]);
        }
        return names;
    }

    private List<List<Double>> collectAndValidateNumericValues(
            ParsedDatasetDto parsedDataset,
            List<Integer> indices,
            List<String> names,
            List<String> warnings
    ) {
        List<List<Double>> result = new ArrayList<>(indices.size());
        for (int i = 0; i < indices.size(); i++) {
            List<Double> values = parsedDataset.numericValuesPerColumn()[indices.get(i)];
            if (isConstantColumn(values)) {
                warnings.add("column '" + names.get(i) + "' is constant — excluded from correlation");
            }
            result.add(values);
        }
        return result;
    }

    private boolean isConstantColumn(List<Double> values) {
        double first = values.get(0);
        for (Double value : values) {
            if (!value.equals(first)) return false;
        }
        return true;
    }

    private List<List<Double>> buildEmptyMatrix(int columnCount) {
        List<List<Double>> matrix = new ArrayList<>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            List<Double> row = new ArrayList<>(columnCount);
            for (int j = 0; j < columnCount; j++) row.add(0.0);
            matrix.add(row);
        }
        return matrix;
    }

    private double calculatePearsonCorrelation(List<Double> valuesX, List<Double> valuesY) {
        int pairCount = Math.min(valuesX.size(), valuesY.size());
        if (pairCount < 2) return 0.0;

        double sumX = 0.0, sumY = 0.0;
        for (int i = 0; i < pairCount; i++) {
            sumX += valuesX.get(i);
            sumY += valuesY.get(i);
        }
        double meanX = sumX / pairCount;
        double meanY = sumY / pairCount;

        double numerator = 0.0, sumSquaredDiffX = 0.0, sumSquaredDiffY = 0.0;
        for (int i = 0; i < pairCount; i++) {
            double diffX = valuesX.get(i) - meanX;
            double diffY = valuesY.get(i) - meanY;
            numerator += diffX * diffY;
            sumSquaredDiffX += diffX * diffX;
            sumSquaredDiffY += diffY * diffY;
        }

        double denominator = Math.sqrt(sumSquaredDiffX * sumSquaredDiffY);
        if (denominator == 0.0) return 0.0;
        return numerator / denominator;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}