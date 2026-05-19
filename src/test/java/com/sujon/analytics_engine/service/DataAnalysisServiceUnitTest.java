package com.sujon.analytics_engine.service;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import com.sujon.analytics_engine.exception.NotFoundException;
import com.sujon.analytics_engine.repository.ColumnStatisticsRepository;
import com.sujon.analytics_engine.repository.DataAnalysisRepository;
import com.sujon.analytics_engine.repository.entity.ColumnStatisticsEntity;
import com.sujon.analytics_engine.repository.entity.DataAnalysisEntity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class DataAnalysisServiceUnitTest {

    @Mock
    private DataAnalysisRepository dataAnalysisRepository;

    @Mock
    private ColumnStatisticsRepository columnStatisticsRepository;

    @InjectMocks
    private DataAnalysisService dataAnalysisService;


    //=================================================================//
    // 1. sha256
    //=================================================================//
    @Test
    void sha256_shouldReturnKnownHash_whenInputIsAbc() {
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        String actual = dataAnalysisService.sha256("abc");
        System.out.println("sha256(\"abc\") = " + actual);
        assertEquals(expected, actual);
    }

    @Test
    void sha256_shouldReturnKnownHash_whenInputIsEmptyString() {
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String actual = dataAnalysisService.sha256("");
        System.out.println("sha256(\"\") = " + actual);
        assertEquals(expected, actual);
    }

    @Test
    void sha256_shouldReturn64LowercaseHexCharacters_whenAnyInputProvided() {
        String hash = dataAnalysisService.sha256("any string at all");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        System.out.println("hash = " + hash + " : hash length = " + hash.length());
    }

    @Test
    void sha256_shouldBeDeterministic_whenSameInputHashedTwice() {
        String input = "name,age,city\nAlice,30,London";
        String h1 = dataAnalysisService.sha256(input);
        String h2 = dataAnalysisService.sha256(input);
        assertEquals(h1, h2);
        System.out.println("h1  = " + h1);
        System.out.println("h2 = " + h2);
        System.out.println("Input one and input two hash is equal? " + h1.equals(h2));
    }

    @Test
    void sha256_shouldProduceDifferentHashes_whenInputsDiffer() {
        String h1 = dataAnalysisService.sha256("input one");
        String h2 = dataAnalysisService.sha256("input two");
        assertNotEquals(h1, h2);
        System.out.println("h1 = " + h1);
        System.out.println("h2 = " + h2);
        System.out.println("Input one and input two hash is equal? " + h1.equals(h2));
    }

    @Test
    void sha256_shouldDetectSingleCharacterChange_whenInputsAlmostIdentical() {
        String h1 = dataAnalysisService.sha256("hello world");
        String h2 = dataAnalysisService.sha256("hello worle");
        assertNotEquals(h1, h2);
        System.out.println("h1 = " + h1);
        System.out.println("h2 = " + h2);
        System.out.println("Input one and input two hash is equal? " + h1.equals(h2));
    }

    @Test
    void sha256_shouldUseUtf8Encoding_whenInputContainsNonAsciiCharacters() {
        String unicode = dataAnalysisService.sha256("café résumé naïve");
        String ascii   = dataAnalysisService.sha256("cafe resume naive");
        assertEquals(64, unicode.length());
        assertNotEquals(unicode, ascii);
        System.out.println("unicode = " + unicode);
        System.out.println("ascii   = " + ascii);
        System.out.println("Input one and input two hash is equal? " + unicode.equals(ascii));
    }


    //=================================================================//
    // 2. normalizeForHash
    //=================================================================//
    @Test
    void normalizeForHash_shouldConvertCrlfToLf_whenInputUsesWindowsLineEndings() {
        String result = dataAnalysisService.normalizeForHash("a,b\r\n1,2");
        assertEquals("a,b\n1,2", result);
        System.out.println("result = " + result.replace("\n", "\\n"));
    }

    @Test
    void normalizeForHash_shouldTrimEachLine_whenLinesHaveSurroundingWhitespace() {
        String result = dataAnalysisService.normalizeForHash("  a,b  \n  1,2  ");
        System.out.println("result = [" + result.replace("\n", "\\n") + "]");
        assertEquals("a,b\n1,2", result);
    }

    @Test
    void normalizeForHash_shouldDropBlankLines_whenInputContainsEmptyLines() {
        String result = dataAnalysisService.normalizeForHash("a,b\n\n\n1,2\n");
        System.out.println("result = " + result.replace("\n", "\\n"));
        assertEquals("a,b\n1,2", result);
    }

    @Test
    void normalizeForHash_shouldDropWhitespaceOnlyLines_whenLinesContainOnlySpacesOrTabs() {
        String result = dataAnalysisService.normalizeForHash("a,b\n   \n\t\t\n1,2");
        System.out.println("result = " + result.replace("\n", "\\n"));
        assertEquals("a,b\n1,2", result);
    }

    @Test
    void normalizeForHash_shouldReturnEmptyString_whenInputIsEmpty() {
        String result = dataAnalysisService.normalizeForHash("");
        System.out.println("result = [" + result + "]");
        assertEquals("", result);
    }

    @Test
    void normalizeForHash_shouldReturnEmptyString_whenInputIsOnlyWhitespaceAndNewlines() {
        String result = dataAnalysisService.normalizeForHash("   \n\t\n\r\n   ");
        System.out.println("result = [" + result + "]");
        assertEquals("", result);
    }

    @Test
    void normalizeForHash_shouldPreserveSingleLine_whenInputHasNoLineBreaks() {
        String result = dataAnalysisService.normalizeForHash("a,b,c");
        System.out.println("result = " + result);
        assertEquals("a,b,c", result);
    }

    @Test
    void normalizeForHash_shouldProduceSameOutput_whenInputDiffersOnlyInLineEndings() {
        String unix    = dataAnalysisService.normalizeForHash("a,b\n1,2");
        String windows = dataAnalysisService.normalizeForHash("a,b\r\n1,2");
        System.out.println("unix    = " + unix.replace("\n", "\\n"));
        System.out.println("windows = " + windows.replace("\n", "\\n"));
        assertEquals(unix, windows);
    }

    @Test
    void normalizeForHash_shouldProduceSameOutput_whenInputDiffersOnlyInTrailingBlankLines() {
        String clean    = dataAnalysisService.normalizeForHash("a,b\n1,2");
        String trailing = dataAnalysisService.normalizeForHash("a,b\n1,2\n\n\n");
        System.out.println("clean    = " + clean.replace("\n", "\\n"));
        System.out.println("trailing = " + trailing.replace("\n", "\\n"));
        assertEquals(clean, trailing);
    }

    @Test
    void normalizeForHash_shouldPreserveInternalWhitespace_whenLineContainsSpacesBetweenTokens() {
        String result = dataAnalysisService.normalizeForHash("first name,age\nJohn Doe,30");
        System.out.println("result = " + result.replace("\n", "\\n"));
        assertEquals("first name,age\nJohn Doe,30", result);
    }

    @Test
    void normalizeForHash_shouldBeIdempotent_whenAppliedTwice() {
        String once  = dataAnalysisService.normalizeForHash("  a,b  \r\n\n  1,2  \r\n");
        String twice = dataAnalysisService.normalizeForHash(once);
        System.out.println("once  = " + once.replace("\n", "\\n"));
        System.out.println("twice = " + twice.replace("\n", "\\n"));
        assertEquals(once, twice);
    }

    //=================================================================//
    // 3. tryParseDouble
    //=================================================================//
    @Test
    void tryParseDouble_shouldReturnNull_whenInputIsNull() {
        Double result = dataAnalysisService.tryParseDouble(null);
        System.out.println("result = " + result);
        assertNull(result);
    }

    @Test
    void tryParseDouble_shouldReturnNull_whenInputIsBlank() {
        System.out.println("empty: " + dataAnalysisService.tryParseDouble(""));
        System.out.println("space: " + dataAnalysisService.tryParseDouble("   "));
        assertNull(dataAnalysisService.tryParseDouble(""));
        assertNull(dataAnalysisService.tryParseDouble("   "));
    }

    @Test
    void tryParseDouble_shouldParseInteger_whenInputIsValidWholeNumber() {
        Double result = dataAnalysisService.tryParseDouble("42");
        System.out.println("result = " + result);
        assertEquals(42.0, result);
    }

    @Test
    void tryParseDouble_shouldParseDecimal_whenInputIsValidFloatingPoint() {
        Double result = dataAnalysisService.tryParseDouble("3.14");
        System.out.println("result = " + result);
        assertEquals(3.14, result);
    }

    @Test
    void tryParseDouble_shouldParseNegativeNumber_whenInputHasMinusSign() {
        Double result = dataAnalysisService.tryParseDouble("-5.5");
        System.out.println("result = " + result);
        assertEquals(-5.5, result);
    }

    @Test
    void tryParseDouble_shouldHandleSurroundingWhitespace_whenInputHasPaddingSpaces() {
        Double result = dataAnalysisService.tryParseDouble("  7  ");
        System.out.println("result = " + result);
        assertEquals(7.0, result);
    }

    @Test
    void tryParseDouble_shouldReturnNull_whenInputIsNonNumeric() {
        Double result = dataAnalysisService.tryParseDouble("hello");
        System.out.println("result = " + result);
        assertNull(result);
    }

    //=================================================================//
    // 4. calculateMean
    //=================================================================//
    @Test
    void calculateMean_shouldReturnNull_whenListIsEmpty() {
        Double result = dataAnalysisService.calculateMean(List.of());
        System.out.println("result = " + result);
        assertNull(result);
    }

    @Test
    void calculateMean_shouldReturnSingleValue_whenListHasOneElement() {
        Double result = dataAnalysisService.calculateMean(List.of(42.0));
        System.out.println("result = " + result);
        assertEquals(42.0, result);
    }

    @Test
    void calculateMean_shouldReturnArithmeticMean_whenListHasMultipleValues() {
        Double result = dataAnalysisService.calculateMean(List.of(1.0, 2.0, 3.0, 4.0, 5.0));
        System.out.println("result = " + result);
        assertEquals(3.0, result);
    }

    //=================================================================//
    // 5. calculateMedian
    //=================================================================//
    @Test
    void calculateMedian_shouldReturnNull_whenListIsEmpty() {
        Double result = dataAnalysisService.calculateMedian(List.of());
        System.out.println("result = " + result);
        assertNull(result);
    }

    @Test
    void calculateMedian_shouldReturnSingleValue_whenListHasOneElement() {
        Double result = dataAnalysisService.calculateMedian(List.of(7.0));
        System.out.println("result = " + result);
        assertEquals(7.0, result);
    }

    @Test
    void calculateMedian_shouldReturnMiddleValue_whenListSizeIsOdd() {
        Double result = dataAnalysisService.calculateMedian(List.of(1.0, 2.0, 3.0, 4.0, 5.0));
        System.out.println("result = " + result);
        assertEquals(3.0, result);
    }

    @Test
    void calculateMedian_shouldReturnAverageOfTwoMiddleValues_whenListSizeIsEven() {
        // Middle two of [1,2,3,4] are 2 and 3, average = 2.5
        Double result = dataAnalysisService.calculateMedian(List.of(1.0, 2.0, 3.0, 4.0));
        System.out.println("result = " + result);
        assertEquals(2.5, result);
    }

    //=================================================================//
    // 6. calculateStandardDeviation
    //=================================================================//
    @Test
    void calculateStandardDeviation_shouldReturnNull_whenListIsEmpty() {
        Double result = dataAnalysisService.calculateStandardDeviation(List.of(), 0.0);
        System.out.println("result = " + result);
        assertNull(result);
    }

    @Test
    void calculateStandardDeviation_shouldReturnNull_whenMeanIsNull() {
        Double result = dataAnalysisService.calculateStandardDeviation(List.of(1.0, 2.0), null);
        System.out.println("result = " + result);
        assertNull(result);
    }

    @Test
    void calculateStandardDeviation_shouldReturnZero_whenListHasSingleValue() {
        Double result = dataAnalysisService.calculateStandardDeviation(List.of(5.0), 5.0);
        System.out.println("result = " + result);
        assertEquals(0.0, result);
    }

    @Test
    void calculateStandardDeviation_shouldReturnPopulationStdDev_whenListHasMultipleValues() {
        // Population std dev of [2,4,4,4,5,5,7,9] with mean 5 is exactly 2.0
        List<Double> values = List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);
        Double result = dataAnalysisService.calculateStandardDeviation(values, 5.0);
        System.out.println("result = " + result);
        assertEquals(2.0, result, 1e-9);
    }


    //=================================================================//
    // 7. calculatePercentile
    //=================================================================//
    @Test
    void calculatePercentile_shouldReturnNull_whenListIsEmpty() {
        Double result = dataAnalysisService.calculatePercentile(List.of(), 50);
        System.out.println("result = " + result);
        assertNull(result);
    }

    @Test
    void calculatePercentile_shouldReturnSingleValue_whenListHasOneElement() {
        Double result = dataAnalysisService.calculatePercentile(List.of(7.0), 90);
        System.out.println("result = " + result);
        assertEquals(7.0, result);
    }

    @Test
    void calculatePercentile_shouldReturnExactValue_whenPercentileLandsOnExistingIndex() {
        // p50 of [1,2,3,4,5]: index = 0.5 * 4 = 2.0 → values.get(2) = 3.0
        Double result = dataAnalysisService.calculatePercentile(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 50);
        System.out.println("result = " + result);
        assertEquals(3.0, result);
    }

    @Test
    void calculatePercentile_shouldInterpolateLinearly_whenPercentileFallsBetweenIndexes() {
        // p25 of [1,2,3,4,5]: index = 0.25 * 4 = 1.0 → values.get(1) = 2.0 (exact)
        // p40 of [1,2,3,4,5]: index = 0.40 * 4 = 1.6 → 2 + 0.6 * (3-2) = 2.6
        Double result = dataAnalysisService.calculatePercentile(List.of(1.0, 2.0, 3.0, 4.0, 5.0), 40);
        System.out.println("result = " + result);
        assertEquals(2.6, result, 1e-9);
    }


    //=================================================================//
    // 8. analyseCsvData — validation and dedup
    //=================================================================//
    @Test
    void analyseCsvData_shouldThrowBadRequest_whenDataIsNull() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> dataAnalysisService.analyseCsvData(null));
        System.out.println("message = " + ex.getMessage());
        assertEquals("Invalid CSV", ex.getMessage());
    }

    @Test
    void analyseCsvData_shouldThrowBadRequest_whenDataIsBlank() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> dataAnalysisService.analyseCsvData("   \n   "));
        System.out.println("message = " + ex.getMessage());
        assertEquals("Invalid CSV", ex.getMessage());
    }

    @Test
    void analyseCsvData_shouldThrowBadRequest_whenFileExceedsMaxSize() {
        // 5 MB + 1 byte of 'a'
        String oversized = "a".repeat(5 * 1024 * 1024 + 1);
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> dataAnalysisService.analyseCsvData(oversized));
        System.out.println("message = " + ex.getMessage());
        assertTrue(ex.getMessage().contains("5MB"));
    }

    @Test
    void analyseCsvData_shouldReturnCachedResult_whenContentHashAlreadyExists() {
        ColumnStatisticsEntity statEntity = ColumnStatisticsEntity.builder()
                .columnName("name")
                .nullCount(0)
                .uniqueCount(2)
                .isNumeric(false)
                .build();

        DataAnalysisEntity cached = DataAnalysisEntity.builder()
                .id(99L)
                .numberOfRows(2)
                .numberOfColumns(1)
                .totalCharacters(15L)
                .columnStatistics(List.of(statEntity))
                .createdAt(OffsetDateTime.now())
                .build();

        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.of(cached));

        DataAnalysisResponseDto result = dataAnalysisService.analyseCsvData("name\nAlice\nBob");
        System.out.println("id = " + result.id());
        System.out.println("alreadyExists = " + result.alreadyExists());
        System.out.println("rows = " + result.numberOfRows());

        assertEquals(99L, result.id());
        assertEquals(2, result.numberOfRows());
        assertTrue(result.alreadyExists());
        verify(dataAnalysisRepository, never()).save(any());
        verify(columnStatisticsRepository, never()).saveAll(any());
    }

    @Test
    void analyseCsvData_shouldCreateNewAnalysis_whenContentHashIsNew() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        DataAnalysisResponseDto result = dataAnalysisService
                .analyseCsvData("name,age\nAlice,30\nBob,25");
        System.out.println("alreadyExists = " + result.alreadyExists());
        System.out.println("rows = " + result.numberOfRows());

        assertFalse(result.alreadyExists());
        assertEquals(2, result.numberOfRows());
        verify(dataAnalysisRepository, times(1)).save(any(DataAnalysisEntity.class));
        verify(columnStatisticsRepository, times(1)).saveAll(any());
    }

    //=================================================================//
    // 9. createNewAnalysis — exercised through analyseCsvData
    //=================================================================//
    @Test
    void createNewAnalysis_shouldThrowBadRequest_whenHeaderRowIsBlank() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());
        // After normalize, blank-only header collapses to "" — but service uses raw `data` for parsing.
        // A header line of just spaces still survives parsing because split keeps it.
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> dataAnalysisService.analyseCsvData("   \nAlice,30"));
        System.out.println("message = " + ex.getMessage());
    }

    @Test
    void createNewAnalysis_shouldThrowBadRequest_whenRowColumnCountMismatchesHeader() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> dataAnalysisService.analyseCsvData("name,age\nAlice"));
        System.out.println("message = " + ex.getMessage());
        assertEquals("Invalid CSV", ex.getMessage());
    }

    @Test
    void createNewAnalysis_shouldThrowBadRequest_whenCsvExceedsCellCountLimit() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        // 1,000,001 single-char columns + one data row → 1,000,001 cells > 1,000,000 limit.
        // Total payload is ~4 MB, safely under the 5 MB file-size cap.
        StringBuilder header = new StringBuilder("a");
        StringBuilder row = new StringBuilder("1");
        for (int i = 1; i < 1_000_001; i++) {
            header.append(",a");
            row.append(",1");
        }
        String csv = header + "\n" + row;

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> dataAnalysisService.analyseCsvData(csv));
        System.out.println("message = " + ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("cell"));
    }

    @Test
    void createNewAnalysis_shouldComputeStatistics_whenColumnIsNumeric() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        DataAnalysisResponseDto result = dataAnalysisService
                .analyseCsvData("age\n10\n20\n30\n40\n50");

        var col = result.columnStatistics().get(0);
        System.out.println("columnName = " + col.columnName());
        System.out.println("isNumeric  = " + col.isNumeric());
        System.out.println("min/max    = " + col.min() + " / " + col.max());
        System.out.println("mean       = " + col.mean());
        System.out.println("median     = " + col.median());
        System.out.println("stdDev     = " + col.standardDeviation());
        System.out.println("percentiles= " + col.percentiles());

        assertEquals("age", col.columnName());
        assertTrue(col.isNumeric());
        assertEquals(10.0, col.min());
        assertEquals(50.0, col.max());
        assertEquals(30.0, col.mean());
        assertEquals(30.0, col.median());
        assertNotNull(col.standardDeviation());
        assertNotNull(col.percentiles());
        assertEquals(6, col.percentiles().size());
    }

    @Test
    void createNewAnalysis_shouldLeaveNumericStatsNull_whenColumnIsText() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        DataAnalysisResponseDto result = dataAnalysisService
                .analyseCsvData("name\nAlice\nBob\nCharlie");

        var col = result.columnStatistics().get(0);
        System.out.println("columnName = " + col.columnName());
        System.out.println("isNumeric  = " + col.isNumeric());
        System.out.println("min        = " + col.min());
        System.out.println("percentiles= " + col.percentiles());

        assertEquals("name", col.columnName());
        assertFalse(col.isNumeric());
        assertNull(col.min());
        assertNull(col.max());
        assertNull(col.mean());
        assertNull(col.median());
        assertNull(col.standardDeviation());
        assertNull(col.percentiles());
        assertEquals(3, col.uniqueCount());
    }

    @Test
    void createNewAnalysis_shouldMarkColumnAsNonNumeric_whenAnyValueIsNonNumeric() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        DataAnalysisResponseDto result = dataAnalysisService
                .analyseCsvData("score\n10\n20\nN/A\n40");

        var col = result.columnStatistics().get(0);
        System.out.println("isNumeric = " + col.isNumeric());
        System.out.println("uniqueCount = " + col.uniqueCount());

        assertFalse(col.isNumeric());
        assertNull(col.mean());
    }

    @Test
    void createNewAnalysis_shouldCountNullsCorrectly_whenColumnHasBlankValues() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        // "Bob," has a non-blank row but a blank `age` cell → counts as one null in column 1.
        DataAnalysisResponseDto result = dataAnalysisService
                .analyseCsvData("name,age\nAlice,10\nBob,\nCharlie,30");

        var ageColumn = result.columnStatistics().get(1);
        System.out.println("nullCount    = " + ageColumn.nullCount());
        System.out.println("uniqueCount  = " + ageColumn.uniqueCount());
        System.out.println("numberOfRows = " + result.numberOfRows());

        assertEquals(1, ageColumn.nullCount());
        assertEquals(2, ageColumn.uniqueCount()); // "10" and "30"
        assertEquals(3, result.numberOfRows());   // all three data rows kept
    }

    @Test
    void createNewAnalysis_shouldSetAlreadyExistsFalse_whenAnalysisIsFresh() {
        when(dataAnalysisRepository.findByContentHash(anyString()))
                .thenReturn(Optional.empty());

        DataAnalysisResponseDto result = dataAnalysisService
                .analyseCsvData("a,b\n1,2");
        System.out.println("alreadyExists = " + result.alreadyExists());
        assertFalse(result.alreadyExists());
    }

    //=================================================================//
    // 10. getAnalysisById
    //=================================================================//
    @Test
    void getAnalysisById_shouldReturnDto_whenIdExists() {
        ColumnStatisticsEntity statEntity = ColumnStatisticsEntity.builder()
                .columnName("score")
                .nullCount(0)
                .uniqueCount(3)
                .isNumeric(true)
                .minValue(1.0)
                .maxValue(3.0)
                .meanValue(2.0)
                .medianValue(2.0)
                .standardDeviation(0.8164965809277260)
                .percentile25(1.5)
                .percentile50(2.0)
                .percentile75(2.5)
                .percentile90(2.8)
                .percentile95(2.9)
                .percentile99(2.98)
                .build();

        DataAnalysisEntity entity = DataAnalysisEntity.builder()
                .id(7L)
                .numberOfRows(3)
                .numberOfColumns(1)
                .totalCharacters(13L)
                .columnStatistics(List.of(statEntity))
                .createdAt(OffsetDateTime.now())
                .build();

        when(dataAnalysisRepository.findById(7L)).thenReturn(Optional.of(entity));

        DataAnalysisResponseDto result = dataAnalysisService.getAnalysisById(7L);
        assertEquals(7L, result.id());
        assertEquals(3, result.numberOfRows());
        assertTrue(result.columnStatistics().get(0).isNumeric());
        assertEquals(6, result.columnStatistics().get(0).percentiles().size());
        System.out.println("alreadyExists = " + result.alreadyExists());
        assertTrue(result.alreadyExists());
        System.out.println("id = " + result.id());
        System.out.println("numericCol = " + result.columnStatistics().get(0).isNumeric());
    }

    @Test
    void getAnalysisById_shouldThrowNotFound_whenIdDoesNotExist() {
        when(dataAnalysisRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> dataAnalysisService.getAnalysisById(99L));
        System.out.println("message = " + ex.getMessage());
        assertEquals("Analysis not found", ex.getMessage());
    }

    //=================================================================//
    // 11. deleteAnalysisById
    //=================================================================//
    @Test
    void deleteAnalysisById_shouldCallRepositoryDelete_whenIdExists() {
        when(dataAnalysisRepository.existsById(1L)).thenReturn(true);
        dataAnalysisService.deleteAnalysisById(1L);
        verify(dataAnalysisRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteAnalysisById_shouldThrowNotFoundException_whenIdDoesNotExist() {
        when(dataAnalysisRepository.existsById(99L)).thenReturn(false);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> dataAnalysisService.deleteAnalysisById(99L));
        assertEquals("Analysis not found", ex.getMessage());
        verify(dataAnalysisRepository, never()).deleteById(any());
        System.out.println("message = " + ex.getMessage());
    }
}
