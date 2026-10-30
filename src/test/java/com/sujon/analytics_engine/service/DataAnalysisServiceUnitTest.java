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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class DataAnalysisServiceUnitTest {

    private static final String SIMPLE_CSV = "driver,number\nMax,1\nLewis,44";

    @Mock
    private DataAnalysisRepository dataAnalysisRepository;
    @Mock
    private ColumnStatisticsRepository columnStatisticsRepository;
    @Spy
    private ContentHasher contentHasher = new ContentHasher();
    @Mock
    private CsvParser csvParser;
    @Mock
    private JsonParser jsonParser;
    @Mock
    private ParquetParser parquetParser;
    @Mock
    private NumericStatsCalculator numericStatsCalculator;
    @Mock
    private NumericCorrelationCalculator numericCorrelationCalculator;
    @Mock
    private AnalysisMapper analysisMapper;

    @InjectMocks
    private DataAnalysisService dataAnalysisService;

    private DataAnalysisResponseDto buildResponseDto(boolean alreadyExists) {
        return new DataAnalysisResponseDto(1L, 2, 2, SIMPLE_CSV.length(), List.of(), null,
                OffsetDateTime.now(), alreadyExists, DataFormat.CSV);
    }

    @Test
    void analyseCsvData_shouldReturnStoredAnalysisWithoutParsing_whenContentHashExists() {
        DataAnalysisEntity storedEntity = DataAnalysisEntity.builder().id(1L).build();
        DataAnalysisResponseDto cachedResponse = buildResponseDto(true);
        when(dataAnalysisRepository.findByContentHash(anyString())).thenReturn(Optional.of(storedEntity));
        when(analysisMapper.mapAnalysisEntityToResponseDto(eq(storedEntity), any(), any(), eq(true)))
                .thenReturn(cachedResponse);

        DataAnalysisResponseDto response = dataAnalysisService.analyseCsvData(SIMPLE_CSV);

        assertTrue(response.alreadyExists());
        verify(csvParser, never()).parseCsvBodyToDataset(anyString());
        verify(numericStatsCalculator, never()).calculateNumericStatsPerColumn(any());
        verify(dataAnalysisRepository, never()).save(any());
    }

    @Test
    void analyseCsvData_shouldParseCalculateAndPersist_whenContentHashIsNew() {
        ParsedDatasetDto parsedDataset = mock(ParsedDatasetDto.class);
        NumericColumnStatsDto[] numericStats = new NumericColumnStatsDto[2];
        NumericCorrelationMatrixDto correlationMatrix =
                new NumericCorrelationMatrixDto(List.of(), List.of(), List.of(), List.of());
        DataAnalysisEntity newEntity = DataAnalysisEntity.builder().build();
        List<ColumnStatisticsEntity> columnEntities = List.of();
        DataAnalysisResponseDto newResponse = buildResponseDto(false);

        when(dataAnalysisRepository.findByContentHash(anyString())).thenReturn(Optional.empty());
        when(csvParser.parseCsvBodyToDataset(SIMPLE_CSV)).thenReturn(parsedDataset);
        when(numericStatsCalculator.calculateNumericStatsPerColumn(parsedDataset)).thenReturn(numericStats);
        when(numericCorrelationCalculator.calculateCorrelationMatrix(parsedDataset)).thenReturn(correlationMatrix);
        when(analysisMapper.buildDataAnalysisEntityFromParsedDataset(
                eq(parsedDataset), eq(correlationMatrix), eq(DataFormat.CSV), anyString(), eq(SIMPLE_CSV), any()))
                .thenReturn(newEntity);
        when(analysisMapper.buildColumnStatisticsEntities(parsedDataset, numericStats, newEntity))
                .thenReturn(columnEntities);
        when(analysisMapper.mapAnalysisEntityToResponseDto(newEntity, columnEntities, correlationMatrix, false))
                .thenReturn(newResponse);

        DataAnalysisResponseDto response = dataAnalysisService.analyseCsvData(SIMPLE_CSV);

        assertFalse(response.alreadyExists());
        verify(dataAnalysisRepository).save(newEntity);
        verify(columnStatisticsRepository).saveAll(columnEntities);
    }

    @Test
    void analyseCsvData_shouldLookUpSameHash_whenCsvDiffersOnlyInLineEndings() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.of(DataAnalysisEntity.builder().build()));
        when(analysisMapper.mapAnalysisEntityToResponseDto(any(), any(), any(), eq(true)))
                .thenReturn(buildResponseDto(true));

        dataAnalysisService.analyseCsvData(SIMPLE_CSV);
        dataAnalysisService.analyseCsvData(SIMPLE_CSV.replace("\n", "\r\n") + "\r\n");

        String expectedHash = contentHasher.hashTextToHex(SIMPLE_CSV);
        verify(dataAnalysisRepository, times(2)).findByContentHash(expectedHash);
    }

    @Test
    void analyseJsonData_shouldDelegateToJsonParser_whenNdjsonFlagIsSet() {
        String ndjsonBody = "{\"id\": 1}\n{\"id\": 2}";
        when(dataAnalysisRepository.findByContentHash(anyString())).thenReturn(Optional.empty());
        when(jsonParser.parseJsonBodyToDataset(ndjsonBody, true)).thenReturn(mock(ParsedDatasetDto.class));

        dataAnalysisService.analyseJsonData(ndjsonBody, true);

        verify(jsonParser).parseJsonBodyToDataset(ndjsonBody, true);
        verify(analysisMapper).buildDataAnalysisEntityFromParsedDataset(
                any(), any(), eq(DataFormat.JSON), anyString(), eq(ndjsonBody), any());
    }

    @Test
    void analyseCsvData_shouldThrowBadRequest_whenBodyIsBlank() {
        assertThrows(BadRequestException.class, () -> dataAnalysisService.analyseCsvData("   "));
        verifyNoInteractions(dataAnalysisRepository);
    }

    @Test
    void analyseCsvData_shouldThrowBadRequest_whenBodyExceedsFiveMegabytes() {
        String oversizedCsv = "a".repeat(5 * 1024 * 1024 + 1);
        assertThrows(BadRequestException.class, () -> dataAnalysisService.analyseCsvData(oversizedCsv));
        verifyNoInteractions(dataAnalysisRepository);
    }

    @Test
    void analyseParquetData_shouldThrowBadRequest_whenBytesAreEmpty() {
        assertThrows(BadRequestException.class, () -> dataAnalysisService.analyseParquetData(new byte[0]));
        verifyNoInteractions(parquetParser);
    }

    @Test
    void getAnalysisById_shouldThrowNotFound_whenIdDoesNotExist() {
        when(dataAnalysisRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> dataAnalysisService.getAnalysisById(99L));
    }

    @Test
    void deleteAnalysisById_shouldDelete_whenIdExists() {
        when(dataAnalysisRepository.existsById(1L)).thenReturn(true);

        dataAnalysisService.deleteAnalysisById(1L);

        verify(dataAnalysisRepository).deleteById(1L);
    }

    @Test
    void deleteAnalysisById_shouldThrowNotFound_whenIdDoesNotExist() {
        when(dataAnalysisRepository.existsById(99L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> dataAnalysisService.deleteAnalysisById(99L));
        verify(dataAnalysisRepository, never()).deleteById(anyLong());
    }
}
