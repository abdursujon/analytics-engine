package com.sujon.analytics_engine.dto;

import com.sujon.analytics_engine.model.ColumnStatistics;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Immutable response payload representing a persisted CSV analysis result.
 */
public record DataAnalysisResponseDto(
        Long id,
        int numberOfRows,
        int numberOfColumns,
        long totalCharacters,
        List<ColumnStatistics> columnStatistics,
        OffsetDateTime createdAt,
        boolean alreadyExists
) {
}
