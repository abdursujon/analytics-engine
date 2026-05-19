package com.sujon.analytics_engine.service;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.exception.NotFoundException;
import com.sujon.analytics_engine.repository.ColumnStatisticsRepository;
import com.sujon.analytics_engine.repository.DataAnalysisRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DataAnalysisService.
 * Uses the real Spring context and a real (in-memory H2) database — no mocks.
 * Verifies that the persistence layer, JPA mapping, and service logic work together end-to-end.
 */
@Tag("integration")
@SpringBootTest
public class DataAnalysisServiceIntegrationTest {

    @Autowired
    private DataAnalysisService dataAnalysisService;

    @Autowired
    private DataAnalysisRepository dataAnalysisRepository;

    @Autowired
    private ColumnStatisticsRepository columnStatisticsRepository;


    @BeforeEach
    void cleanDatabase() {
        // Delete child rows first to avoid FK violations
        columnStatisticsRepository.deleteAll();
        dataAnalysisRepository.deleteAll();
    }

    //============================= analyseCsvData ========================//
    @Test
    void analyseCsvData_shouldPersistAnalysisWithGeneratedId_whenNewCsvIsUploaded() {
        DataAnalysisResponseDto result = dataAnalysisService
                .analyseCsvData("name,age\nAlice,30\nBob,25");

        assertNotNull(result.id(), "JPA should assign a generated id");
        assertEquals(1, dataAnalysisRepository.count());
        assertEquals(2, columnStatisticsRepository.count()); // 2 columns persisted

        System.out.println("generated id      = " + result.id());
        System.out.println("analyses in db    = " + dataAnalysisRepository.count());
        System.out.println("col stats in db   = " + columnStatisticsRepository.count());
    }


    @Test
    void analyseCsvData_shouldPersistMultipleDistinctAnalyses_whenInputsAreDifferent() {
        DataAnalysisResponseDto first  = dataAnalysisService.analyseCsvData("a,b\n1,2");
        DataAnalysisResponseDto second = dataAnalysisService.analyseCsvData("x,y\n5,6");
        DataAnalysisResponseDto third  = dataAnalysisService.analyseCsvData("p,q\n9,8");

        assertNotEquals(first.id(), second.id());
        assertNotEquals(second.id(), third.id());
        assertEquals(3, dataAnalysisRepository.count());
        assertEquals(6, columnStatisticsRepository.count()); // 2 cols × 3 analyses

        System.out.println("ids = " + first.id() + ", " + second.id() + ", " + third.id());
    }

    @Test
    void analyseCsvData_shouldReturnCachedResult_whenSameCsvUploadedTwice() {
        DataAnalysisResponseDto first  = dataAnalysisService.analyseCsvData("name,age\nAlice,30");
        DataAnalysisResponseDto second = dataAnalysisService.analyseCsvData("name,age\nAlice,30");

        assertFalse(first.alreadyExists());
        assertTrue(second.alreadyExists());
        assertEquals(first.id(), second.id(), "Dedup should return the same row");
        assertEquals(1, dataAnalysisRepository.count(), "Only one row should be persisted");

        System.out.println("first.id  = " + first.id()  + " alreadyExists=" + first.alreadyExists());
        System.out.println("second.id = " + second.id() + " alreadyExists=" + second.alreadyExists());
    }

    @Test
    void analyseCsvData_shouldDedupe_whenInputDiffersOnlyInWhitespaceOrLineEndings() {
        DataAnalysisResponseDto unix    = dataAnalysisService.analyseCsvData("a,b\n1,2");
        DataAnalysisResponseDto windows = dataAnalysisService.analyseCsvData("a,b\r\n1,2");
        DataAnalysisResponseDto padded  = dataAnalysisService.analyseCsvData("  a,b  \n\n  1,2  \n");

        System.out.println("unix.id    = " + unix.id()    + " new=" + !unix.alreadyExists());
        System.out.println("windows.id = " + windows.id() + " cached=" + windows.alreadyExists());
        System.out.println("padded.id  = " + padded.id()  + " cached=" + padded.alreadyExists());

        assertFalse(unix.alreadyExists());
        assertTrue(windows.alreadyExists());
        assertTrue(padded.alreadyExists());
        assertEquals(unix.id(), windows.id());
        assertEquals(unix.id(), padded.id());
        assertEquals(1, dataAnalysisRepository.count());
    }


    //============================= createNewAnalysis ========================//
    @Test
    void createNewAnalysis_shouldPersistEntityUnderProvidedContentHash_whenCalledDirectly() {
        String customHash = "custom-test-hash-abc123";

        DataAnalysisResponseDto result = dataAnalysisService
                .createNewAnalysis("name,age\nAlice,30", customHash);

        // Round-trip via the repo: the stored entity must be findable by the supplied hash.
        // This is what analyseCsvData later relies on for dedup.
        var lookup = dataAnalysisRepository.findByContentHash(customHash);

        System.out.println("returned id      = " + result.id());
        System.out.println("id under hash    = " + lookup.map(e -> e.getId()).orElse(null));

        assertTrue(lookup.isPresent(), "Entity should be findable by the provided contentHash");
        assertEquals(result.id(), lookup.get().getId());
        assertFalse(result.alreadyExists(), "Direct create should always return alreadyExists=false");
    }


    @Test
    void createNewAnalysis_shouldCascadePersistAllColumnStatistics_whenCsvHasMultipleColumns() {
        DataAnalysisResponseDto result = dataAnalysisService
                .createNewAnalysis("a,b,c\n1,2,3\n4,5,6", "hash-3-cols");
        assertEquals(1, dataAnalysisRepository.count());
        assertEquals(3, columnStatisticsRepository.count());
        assertEquals(3, result.columnStatistics().size());

        System.out.println("analyses in db  = " + dataAnalysisRepository.count());
        System.out.println("col stats in db = " + columnStatisticsRepository.count());
        System.out.println("dto columns     = " + result.columnStatistics().size());

    }


    @Test
    void createNewAnalysis_shouldReturnDtoMatchingPersistedRow_whenRefetchedById() {
        DataAnalysisResponseDto created = dataAnalysisService
                .createNewAnalysis("score\n10\n20\n30\n40\n50", "hash-score-5-rows");

        DataAnalysisResponseDto refetched = dataAnalysisService.getAnalysisById(created.id());

        System.out.println("created.alreadyExists   = " + created.alreadyExists());
        System.out.println("refetched.alreadyExists = " + refetched.alreadyExists());
        System.out.println("mean (created/refetched) = "
                + created.columnStatistics().get(0).mean() + " / "
                + refetched.columnStatistics().get(0).mean());

        assertEquals(created.numberOfRows(), refetched.numberOfRows());
        assertEquals(created.numberOfColumns(), refetched.numberOfColumns());
        assertEquals(created.columnStatistics().get(0).mean(),
                refetched.columnStatistics().get(0).mean());
        assertFalse(created.alreadyExists());
        assertTrue(refetched.alreadyExists());
    }


    @Test
    void createNewAnalysis_shouldNotDedupe_whenCalledMultipleTimesWithDifferentHashes() {
        // createNewAnalysis itself does no dedup — that's analyseCsvData's job.
        // Same data + different hash should produce two distinct rows.
        DataAnalysisResponseDto first  = dataAnalysisService.createNewAnalysis("a,b\n1,2", "hash-one");
        DataAnalysisResponseDto second = dataAnalysisService.createNewAnalysis("a,b\n1,2", "hash-two");

        System.out.println("first.id  = " + first.id());
        System.out.println("second.id = " + second.id());
        System.out.println("total rows = " + dataAnalysisRepository.count());

        assertNotEquals(first.id(), second.id());
        assertEquals(2, dataAnalysisRepository.count());
        assertTrue(dataAnalysisRepository.findByContentHash("hash-one").isPresent());
        assertTrue(dataAnalysisRepository.findByContentHash("hash-two").isPresent());
    }



    //=============================getAnalysisById========================//
    @Test
    void getAnalysisById_shouldReturnPersistedAnalysis_whenIdExists() {
        DataAnalysisResponseDto persisted = dataAnalysisService
                .analyseCsvData("score\n10\n20\n30");
        Long id = persisted.id();

        DataAnalysisResponseDto fetched = dataAnalysisService.getAnalysisById(id);

        System.out.println("fetched id    = " + fetched.id());
        System.out.println("alreadyExists = " + fetched.alreadyExists());
        System.out.println("column        = " + fetched.columnStatistics().get(0).columnName());
        System.out.println("mean          = " + fetched.columnStatistics().get(0).mean());

        assertEquals(id, fetched.id());
        assertTrue(fetched.alreadyExists());
        assertEquals(1, fetched.columnStatistics().size());
        assertEquals("score", fetched.columnStatistics().get(0).columnName());
        assertEquals(20.0, fetched.columnStatistics().get(0).mean());
    }


    @Test
    void getAnalysisById_shouldThrowNotFound_whenIdDoesNotExist() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> dataAnalysisService.getAnalysisById(99_999L));

        System.out.println("message = " + ex.getMessage());
        assertEquals("Analysis not found", ex.getMessage());
    }


    @Test
    void analyseCsvData_thenGetAnalysisById_shouldRoundTripStatisticsThroughJpa() {
        DataAnalysisResponseDto created = dataAnalysisService
                .analyseCsvData("age,city\n30,London\n25,Paris\n45,Berlin");
        Long id = created.id();

        DataAnalysisResponseDto fetched = dataAnalysisService.getAnalysisById(id);

        System.out.println("created columns = " + created.columnStatistics().size());
        System.out.println("fetched columns = " + fetched.columnStatistics().size());
        System.out.println("age mean (created/fetched) = "
                + created.columnStatistics().get(0).mean() + " / "
                + fetched.columnStatistics().get(0).mean());

        assertEquals(created.numberOfRows(), fetched.numberOfRows());
        assertEquals(created.numberOfColumns(), fetched.numberOfColumns());
        assertEquals(created.columnStatistics().size(), fetched.columnStatistics().size());

        var ageCreated = created.columnStatistics().get(0);
        var ageFetched = fetched.columnStatistics().get(0);
        assertEquals(ageCreated.mean(), ageFetched.mean());
        assertEquals(ageCreated.median(), ageFetched.median());
        assertEquals(ageCreated.standardDeviation(), ageFetched.standardDeviation());
        assertEquals(ageCreated.percentiles(), ageFetched.percentiles());
    }


    @Test
    void getAnalysisById_shouldReturnNullStatistics_whenColumnIsTextOnly() {
        // Verifies that null Double values for non-numeric columns round-trip through JPA
        DataAnalysisResponseDto persisted = dataAnalysisService
                .analyseCsvData("name\nAlice\nBob\nCharlie");

        DataAnalysisResponseDto fetched = dataAnalysisService.getAnalysisById(persisted.id());
        var col = fetched.columnStatistics().get(0);

        System.out.println("isNumeric   = " + col.isNumeric());
        System.out.println("min/max     = " + col.min() + " / " + col.max());
        System.out.println("mean        = " + col.mean());
        System.out.println("percentiles = " + col.percentiles());

        assertFalse(col.isNumeric());
        assertNull(col.min());
        assertNull(col.max());
        assertNull(col.mean());
        assertNull(col.median());
        assertNull(col.standardDeviation());
        assertNull(col.percentiles());
        assertEquals(3, col.uniqueCount());
    }


    //=============================deleteAnalysisById========================//
    @Test
    void deleteAnalysisById_shouldRemoveAnalysisFromDatabase_whenIdExists() {
        DataAnalysisResponseDto persisted = dataAnalysisService
                .analyseCsvData("name\nAlice");
        Long id = persisted.id();
        assertEquals(1, dataAnalysisRepository.count());

        dataAnalysisService.deleteAnalysisById(id);

        System.out.println("rows after delete = " + dataAnalysisRepository.count());

        assertEquals(0, dataAnalysisRepository.count());
        assertThrows(NotFoundException.class,
                () -> dataAnalysisService.getAnalysisById(id),
                "Deleted row should not be retrievable");
    }


    @Test
    void deleteAnalysisById_shouldThrowNotFound_whenIdDoesNotExist() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> dataAnalysisService.deleteAnalysisById(99_999L));

        System.out.println("message = " + ex.getMessage());
        assertEquals("Analysis not found", ex.getMessage());
    }


    @Test
    void deleteAnalysisById_shouldCascadeDeleteColumnStatistics_whenParentIsDeleted() {
        DataAnalysisResponseDto persisted = dataAnalysisService
                .analyseCsvData("a,b,c\n1,2,3");
        long beforeDelete = columnStatisticsRepository.count();

        dataAnalysisService.deleteAnalysisById(persisted.id());

        long afterDelete = columnStatisticsRepository.count();
        System.out.println("col stats before delete: " + beforeDelete + " || after delete: " + afterDelete);

        assertEquals(3, beforeDelete);
        assertEquals(0, afterDelete,
                "Column statistics should be cascade-deleted when parent is removed");
    }
}