package com.sujon.analytics_engine.service.mapper;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.dto.NumericColumnStatsDto;
import com.sujon.analytics_engine.dto.NumericCorrelationMatrixDto;
import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.model.ColumnStatistics;
import com.sujon.analytics_engine.model.DataFormat;
import com.sujon.analytics_engine.repository.entity.ColumnStatisticsEntity;
import com.sujon.analytics_engine.repository.entity.DataAnalysisEntity;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class AnalysisMapper {

    public DataAnalysisResponseDto mapAnalysisEntityToResponseDto(
            DataAnalysisEntity analysisEntity,
            Collection<ColumnStatisticsEntity> columnEntities,
            NumericCorrelationMatrixDto numericCorrelationMatrix,
            boolean alreadyExists
    ) {
        List<ColumnStatistics> columnStatisticsList = columnEntities.stream()
                .map(this::mapColumnEntityToColumnStatistics)
                .toList();

        return new DataAnalysisResponseDto(
                analysisEntity.getId(),
                analysisEntity.getNumberOfRows(),
                analysisEntity.getNumberOfColumns(),
                analysisEntity.getTotalCharacters(),
                columnStatisticsList,
                numericCorrelationMatrix,
                analysisEntity.getCreatedAt(),
                alreadyExists,
                analysisEntity.getFormat()
        );
    }

    private ColumnStatistics mapColumnEntityToColumnStatistics(ColumnStatisticsEntity columnEntity) {
        List<Double> percentiles = columnEntity.isNumeric()
                ? List.of(
                columnEntity.getPercentile25(),
                columnEntity.getPercentile50(),
                columnEntity.getPercentile75(),
                columnEntity.getPercentile90(),
                columnEntity.getPercentile95(),
                columnEntity.getPercentile99()
        )
                : null;

        return new ColumnStatistics(
                columnEntity.getColumnName(),
                columnEntity.getNullCount(),
                columnEntity.getUniqueCount(),
                columnEntity.isNumeric(),
                columnEntity.getMinValue(),
                columnEntity.getMaxValue(),
                columnEntity.getMeanValue(),
                columnEntity.getMedianValue(),
                columnEntity.getStandardDeviation(),
                percentiles
        );
    }

    public DataAnalysisEntity buildDataAnalysisEntityFromParsedDataset(
            ParsedDatasetDto parsedDataset,
            NumericCorrelationMatrixDto numericCorrelationMatrix,
            DataFormat format,
            String contentHash,
            String originalData,
            OffsetDateTime createdAt
    ) {
        return DataAnalysisEntity.builder()
                .originalData(originalData)
                .contentHash(contentHash)
                .numberOfRows(parsedDataset.totalRowCount())
                .numberOfColumns(parsedDataset.columnHeaders().length)
                .totalCharacters(parsedDataset.totalPayloadSize())
                .format(format)
                .numericCorrelationMatrix(numericCorrelationMatrix)
                .createdAt(createdAt)
                .build();
    }

    public List<ColumnStatisticsEntity> buildColumnStatisticsEntities(
            ParsedDatasetDto parsedDataset,
            NumericColumnStatsDto[] numericStatsPerColumn,
            DataAnalysisEntity parentEntity
    ) {
        int columnCount = parsedDataset.columnHeaders().length;
        List<ColumnStatisticsEntity> entities = new ArrayList<>(columnCount);

        for (int c = 0; c < columnCount; c++) {
            NumericColumnStatsDto stats = numericStatsPerColumn[c];
            boolean hasNumericStats = stats != null;

            entities.add(ColumnStatisticsEntity.builder()
                    .dataAnalysis(parentEntity)
                    .columnName(parsedDataset.columnHeaders()[c])
                    .nullCount(parsedDataset.nullCountPerColumn()[c])
                    .uniqueCount(parsedDataset.uniqueValuesPerColumn()[c].size())
                    .isNumeric(hasNumericStats)
                    .minValue(hasNumericStats ? stats.minValue() : null)
                    .maxValue(hasNumericStats ? stats.maxValue() : null)
                    .meanValue(hasNumericStats ? stats.meanValue() : null)
                    .medianValue(hasNumericStats ? stats.medianValue() : null)
                    .standardDeviation(hasNumericStats ? stats.standardDeviation() : null)
                    .percentile25(hasNumericStats ? stats.percentiles().get(0) : null)
                    .percentile50(hasNumericStats ? stats.percentiles().get(1) : null)
                    .percentile75(hasNumericStats ? stats.percentiles().get(2) : null)
                    .percentile90(hasNumericStats ? stats.percentiles().get(3) : null)
                    .percentile95(hasNumericStats ? stats.percentiles().get(4) : null)
                    .percentile99(hasNumericStats ? stats.percentiles().get(5) : null)
                    .build());
        }

        return entities;
    }
}