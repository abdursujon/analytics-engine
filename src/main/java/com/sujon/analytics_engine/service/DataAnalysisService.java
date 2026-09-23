package com.sujon.analytics_engine.service;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.dto.NumericColumnStatsDto;
import com.sujon.analytics_engine.dto.NumericCorrelationMatrixDto;
import com.sujon.analytics_engine.dto.ParsedDatasetDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import com.sujon.analytics_engine.exception.NotFoundException;
import com.sujon.analytics_engine.model.DataFormat;
import com.sujon.analytics_engine.repository.ColumnStatisticsRepository;
import com.sujon.analytics_engine.repository.DataAnalysisRepository;
import com.sujon.analytics_engine.repository.entity.ColumnStatisticsEntity;
import com.sujon.analytics_engine.repository.entity.DataAnalysisEntity;
import com.sujon.analytics_engine.service.hash.ContentHasher;
import com.sujon.analytics_engine.service.mapper.AnalysisMapper;
import com.sujon.analytics_engine.service.parser.CsvParser;
import com.sujon.analytics_engine.service.parser.JsonParser;
import com.sujon.analytics_engine.service.parser.ParquetParser;
import com.sujon.analytics_engine.service.stats.NumericCorrelationCalculator;
import com.sujon.analytics_engine.service.stats.NumericStatsCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DataAnalysisService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    private final DataAnalysisRepository dataAnalysisRepository;
    private final ColumnStatisticsRepository columnStatisticsRepository;
    private final ContentHasher contentHasher;
    private final CsvParser csvParser;
    private final JsonParser jsonParser;
    private final ParquetParser parquetParser;
    private final NumericStatsCalculator numericStatsCalculator;
    private final NumericCorrelationCalculator numericCorrelationCalculator;
    private final AnalysisMapper analysisMapper;

    public DataAnalysisResponseDto analyseCsvData(String data) {
        requireNonBlank(data, "Invalid CSV");
        requireUnderSizeLimit(data.getBytes(StandardCharsets.UTF_8).length);
        String contentHash = contentHasher.hashTextToHex(contentHasher.normaliseCsvForStableHash(data));
        return findCachedOrAnalyse(contentHash,
                () -> csvParser.parseCsvBodyToDataset(data),
                DataFormat.CSV, data);
    }

    public DataAnalysisResponseDto analyseJsonData(String body, boolean isNdjson) {
        requireNonBlank(body, "Invalid JSON");
        requireUnderSizeLimit(body.getBytes(StandardCharsets.UTF_8).length);
        String contentHash = contentHasher.hashTextToHex(body);
        return findCachedOrAnalyse(contentHash,
                () -> jsonParser.parseJsonBodyToDataset(body, isNdjson),
                DataFormat.JSON, body);
    }

    public DataAnalysisResponseDto analyseParquetData(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("Invalid Parquet");
        requireUnderSizeLimit(bytes.length);
        String contentHash = contentHasher.hashBytesToHex(bytes);
        return findCachedOrAnalyse(contentHash,
                () -> parquetParser.parseParquetBytesToDataset(bytes),
                DataFormat.PARQUET, null);
    }

    public DataAnalysisResponseDto getAnalysisById(Long id) {
        DataAnalysisEntity entity = dataAnalysisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Analysis not found"));
        return analysisMapper.mapAnalysisEntityToResponseDto(entity, entity.getColumnStatistics(), entity.getNumericCorrelationMatrix(), true);
    }

    public void deleteAnalysisById(Long id) {
        if (!dataAnalysisRepository.existsById(id)) {
            throw new NotFoundException("Analysis not found");
        }
        dataAnalysisRepository.deleteById(id);
    }

    private DataAnalysisResponseDto findCachedOrAnalyse(
            String contentHash,
            Supplier<ParsedDatasetDto> parserCall,
            DataFormat format,
            String originalData
    ) {
        return dataAnalysisRepository.findByContentHash(contentHash)
                .map(entity -> analysisMapper.mapAnalysisEntityToResponseDto(
                        entity, entity.getColumnStatistics(),  entity.getNumericCorrelationMatrix(), true))
                .orElseGet(() -> persistNewAnalysis(parserCall.get(), format, contentHash, originalData));
    }

    private DataAnalysisResponseDto persistNewAnalysis(
            ParsedDatasetDto parsedDataset,
            DataFormat format,
            String contentHash,
            String originalData
    ) {
        NumericColumnStatsDto[] numericStats =
                numericStatsCalculator.calculateNumericStatsPerColumn(parsedDataset);

        NumericCorrelationMatrixDto numericCorrelationMatrix =
                numericCorrelationCalculator.calculateCorrelationMatrix(parsedDataset);

        OffsetDateTime createdAt = OffsetDateTime.now();
        DataAnalysisEntity analysisEntity = analysisMapper.buildDataAnalysisEntityFromParsedDataset(
                parsedDataset, numericCorrelationMatrix, format, contentHash, originalData, createdAt);
        dataAnalysisRepository.save(analysisEntity);

        List<ColumnStatisticsEntity> columnEntities =
                analysisMapper.buildColumnStatisticsEntities(parsedDataset, numericStats, analysisEntity);
        columnStatisticsRepository.saveAll(columnEntities);

        return analysisMapper.mapAnalysisEntityToResponseDto(analysisEntity, columnEntities, numericCorrelationMatrix, false);
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new BadRequestException(message);
    }

    private void requireUnderSizeLimit(long sizeInBytes) {
        if (sizeInBytes > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File size exceeds maximum allowed size of 5MB");
        }
    }
}