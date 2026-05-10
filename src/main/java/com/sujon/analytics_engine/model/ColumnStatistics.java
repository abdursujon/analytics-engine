package  com.sujon.analytics_engine.model;

import java.util.List;

/**
 * Model class representing statistical information about a single column in a CSV dataset.
 * Includes basic counts and statistical profiling metrics for numeric columns.
*/
public record ColumnStatistics(
        String columnName,
        int nullCount,
        int uniqueCount,
        boolean isNumeric,
        Double min,
        Double max,
        Double mean,
        Double median,
        Double standardDeviation,
        List<Double> percentiles
) {
}
