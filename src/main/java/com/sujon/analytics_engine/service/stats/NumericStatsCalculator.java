package com.sujon.analytics_engine.service.stats;


import com.sujon.analytics_engine.dto.NumericColumnStatsDto;
import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Component
public class NumericStatsCalculator {

    private static final double[] PERCENTILES_TO_CALCULATE = {25, 50, 75, 90, 95, 99};

    public NumericColumnStatsDto[] calculateNumericStatsPerColumn(ParsedDatasetDto parsedDataset) {
        int columnCount = parsedDataset.columnHeaders().length;
        NumericColumnStatsDto[] statsPerColumn = new NumericColumnStatsDto[columnCount];

        for (int c = 0; c < columnCount; c++) {
            if (!parsedDataset.isNumericColumnFlags()[c]) continue;

            List<Double> rawValues = parsedDataset.numericValuesPerColumn()[c];
            if (rawValues.isEmpty()) continue;

            statsPerColumn[c] = calculateStatsForOneColumn(rawValues);
        }

        return statsPerColumn;
    }

    private NumericColumnStatsDto calculateStatsForOneColumn(List<Double> rawValues) {
        List<Double> sortedValues = new ArrayList<>(rawValues);
        Collections.sort(sortedValues);

        double meanValue = calculateMeanOfValues(sortedValues);

        List<Double> percentileResults = new ArrayList<>(PERCENTILES_TO_CALCULATE.length);
        for (double percentile : PERCENTILES_TO_CALCULATE) {
            percentileResults.add(calculatePercentileOfSortedValues(sortedValues, percentile));
        }

        return new NumericColumnStatsDto(
                sortedValues.get(0),
                sortedValues.get(sortedValues.size() - 1),
                meanValue,
                calculateMedianOfSortedValues(sortedValues),
                calculateStandardDeviationOfValues(sortedValues, meanValue),
                percentileResults
        );
    }

    private double calculateMeanOfValues(List<Double> values) {
        double sum = 0.0;
        for (Double value : values) sum += value;
        return sum / values.size();
    }

    private double calculateMedianOfSortedValues(List<Double> sortedValues) {
        int size = sortedValues.size();
        if (size % 2 == 0) {
            return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
        }
        return sortedValues.get(size / 2);
    }

    private double calculateStandardDeviationOfValues(List<Double> values, double mean) {
        double sumSquaredDifferences = 0.0;
        for (Double value : values) {
            double difference = value - mean;
            sumSquaredDifferences += difference * difference;
        }
        return Math.sqrt(sumSquaredDifferences / values.size());
    }

    private double calculatePercentileOfSortedValues(List<Double> sortedValues, double percentile) {
        if (sortedValues.size() == 1) return sortedValues.get(0);
        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);
        if (lowerIndex == upperIndex) return sortedValues.get(lowerIndex);
        double fraction = index - lowerIndex;
        return sortedValues.get(lowerIndex) + fraction * (sortedValues.get(upperIndex) - sortedValues.get(lowerIndex));
    }
}
