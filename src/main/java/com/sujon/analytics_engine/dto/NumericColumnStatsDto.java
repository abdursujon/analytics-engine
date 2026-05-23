package com.sujon.analytics_engine.dto;

import java.util.List;

public record NumericColumnStats(
        double minValue,
        double maxValue,
        double meanValue,
        double medianValue,
        double standardDeviation,
        List<Double> percentiles
) {

}
