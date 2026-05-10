package com.sujon.analytics_engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.repository.DataAnalysisRepository;
import com.sujon.analytics_engine.repository.entity.DataAnalysisEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for CSV analysis endpoints: ingest, retrieve, delete, and download
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataAnalysisApiFullWorkflowTest {

    private String performAndLog(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        var result = mockMvc.perform(requestBuilder)
                .andDo(print())
                .andReturn();

        System.out.println("Response body: " + result.getResponse().getContentAsString());
        System.out.println("Status: " + result.getResponse().getStatus());
        return result.getResponse().getContentAsString();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataAnalysisRepository dataAnalysisRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        dataAnalysisRepository.deleteAll();
    }

    @Test
    void shouldCalculateUniqueCountsForSimpleCsv(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv
    ) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8);

        String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

       DataAnalysisResponseDto response = objectMapper.readValue(responseBody,DataAnalysisResponseDto.class);
        System.out.println("Parsed response: " + response);

        assertThat(response.columnStatistics()).hasSize(3);
        assertThat(response.columnStatistics())
                .anyMatch(stat -> stat.columnName().equals("driver") && stat.uniqueCount() == 3)
                .anyMatch(stat -> stat.columnName().equals("number") && stat.uniqueCount() == 3)
                .anyMatch(stat -> stat.columnName().equals("team") && stat.uniqueCount() == 3);
    }

    @Test
    void shouldCalculateUniqueCountsWithDuplicates(
            @Value("classpath:test-data/large.csv") Resource largeCsv
    ) throws Exception {
        String csvData = largeCsv.getContentAsString(UTF_8);

        String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

       DataAnalysisResponseDto response = objectMapper.readValue(responseBody,DataAnalysisResponseDto.class);
        System.out.println("Parsed response: " + response);

        assertThat(response.columnStatistics()).hasSize(6);
        assertThat(response.columnStatistics())
                .anyMatch(stat -> stat.columnName().equals("driver") && stat.uniqueCount() == 10)
                .anyMatch(stat -> stat.columnName().equals("team") && stat.uniqueCount() == 6);
    }


    @Test
    void shouldExcludeNullsFromUniqueCount(
            @Value("classpath:test-data/with-nulls.csv") Resource withNullsCsv
    ) throws Exception {
        String csvData = withNullsCsv.getContentAsString(UTF_8);

        String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

       DataAnalysisResponseDto response = objectMapper.readValue(responseBody,DataAnalysisResponseDto.class);
        System.out.println("Parsed response: " + response);

        assertThat(response.columnStatistics()).hasSize(4);
        assertThat(response.columnStatistics())
                .anyMatch(stat -> stat.columnName().equals("driver") && stat.uniqueCount() == 4)
                .anyMatch(stat -> stat.columnName().equals("number") && stat.uniqueCount() == 2)
                .anyMatch(stat -> stat.columnName().equals("team") && stat.uniqueCount() == 2)
                .anyMatch(stat -> stat.columnName().equals("nationality") && stat.uniqueCount() == 3);
    }

    @Test
    void shouldReturnZeroUniqueCountForHeaderOnlyCsv(
            @Value("classpath:test-data/header-only.csv") Resource emptyCsv
    ) throws Exception {
        String csvData = emptyCsv.getContentAsString(UTF_8);

        String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

       DataAnalysisResponseDto response = objectMapper.readValue(responseBody,DataAnalysisResponseDto.class);
        System.out.println("Parsed response: " + response);

        assertThat(response.columnStatistics()).hasSize(3);
        assertThat(response.columnStatistics())
                .allMatch(stat -> stat.uniqueCount() == 0);
    }

    @Test
    void shouldPersistUniqueCountsToDatabase(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv
    ) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8);

        performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

        var entities = dataAnalysisRepository.findAll();
        System.out.println("Database entities: " + entities);
        assertThat(entities).hasSize(1);

        var entity = entities.get(0);
        assertThat(entity.getColumnStatistics()).hasSize(3);
        assertThat(entity.getColumnStatistics())
                .anyMatch(stat -> stat.getColumnName().equals("driver") && stat.getUniqueCount() == 3)
                .anyMatch(stat -> stat.getColumnName().equals("number") && stat.getUniqueCount() == 3)
                .anyMatch(stat -> stat.getColumnName().equals("team") && stat.getUniqueCount() == 3);
    }

    @Test
    void shouldRetrievePreviousAnalysisById(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv
    ) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8);

        performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

        var entities = dataAnalysisRepository.findAll();
        Long analysisId = entities.get(0).getId();

        String responseBody = performAndLog(get("/api/analysis/{id}", analysisId));
       DataAnalysisResponseDto response = objectMapper.readValue(responseBody,DataAnalysisResponseDto.class);
        System.out.println("Parsed response: " + response);

        assertThat(response.numberOfRows()).isEqualTo(3);
        assertThat(response.numberOfColumns()).isEqualTo(3);
        assertThat(response.totalCharacters()).isEqualTo(csvData.length());
        assertThat(response.columnStatistics()).hasSize(3);
    }

    @Test
    void shouldReturn404ForNonExistentAnalysis() throws Exception {
        performAndLog(get("/api/analysis/{id}", 999L));
    }

    @Test
    void shouldRetrieveMultipleAnalysesIndependently(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv,
            @Value("classpath:test-data/with-nulls.csv") Resource withNullsCsv
    ) throws Exception {
        String csvData1 = simpleCsv.getContentAsString(UTF_8);
        String csvData2 = withNullsCsv.getContentAsString(UTF_8);

        String responseBody1 = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData1));

        String responseBody2 = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData2));

        var entities = dataAnalysisRepository.findAll();
        assertThat(entities).hasSize(2);

        Long id1 = entities.get(0).getId();
        String getResponse1 = performAndLog(get("/api/analysis/{id}", id1));
       DataAnalysisResponseDto response1 = objectMapper.readValue(getResponse1,DataAnalysisResponseDto.class);

        Long id2 = entities.get(1).getId();
        String getResponse2 = performAndLog(get("/api/analysis/{id}", id2));
       DataAnalysisResponseDto response2 = objectMapper.readValue(getResponse2,DataAnalysisResponseDto.class);

        assertThat(response1.numberOfRows()).isEqualTo(3);
        assertThat(response2.numberOfRows()).isEqualTo(4);
    }

    @Test
    void shouldDeleteAnalysisById(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv
    ) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8);

        String ingestResponse = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

        var entities = dataAnalysisRepository.findAll();
        assertThat(entities).hasSize(1);
        Long analysisId = entities.get(0).getId();

        performAndLog(delete("/api/analysis/{id}", analysisId));

        assertThat(dataAnalysisRepository.count()).isEqualTo(0);

        performAndLog(get("/api/analysis/{id}", analysisId));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentAnalysis(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv
    ) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8);

        performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

        assertThat(dataAnalysisRepository.count()).isEqualTo(1);

        performAndLog(delete("/api/analysis/{id}", 999L));

        assertThat(dataAnalysisRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldDeleteOnlySpecifiedAnalysis(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv,
            @Value("classpath:test-data/with-nulls.csv") Resource withNullsCsv
    ) throws Exception {
        String csvData1 = simpleCsv.getContentAsString(UTF_8);
        String csvData2 = withNullsCsv.getContentAsString(UTF_8);

        performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData1));

        performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData2));

        var entities = dataAnalysisRepository.findAll();
        assertThat(entities).hasSize(2);

        Long id1 = entities.get(0).getId();
        Long id2 = entities.get(1).getId();

        performAndLog(delete("/api/analysis/{id}", id1));

        assertThat(dataAnalysisRepository.count()).isEqualTo(1);

        performAndLog(get("/api/analysis/{id}", id2));
        performAndLog(get("/api/analysis/{id}", id1));
    }

    @Test
    void shouldCascadeDeleteColumnStatistics(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv
    ) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8);

        String ingestResponse = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

        var entities = dataAnalysisRepository.findAll();
        assertThat(entities).hasSize(1);

        DataAnalysisEntity entity = entities.get(0);
        Long analysisId = entity.getId();

        assertThat(entity.getColumnStatistics()).hasSize(3);

        performAndLog(delete("/api/analysis/{id}", analysisId));
        assertThat(dataAnalysisRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldDownloadPrettyJson(@Value("classpath:test-data/simple.csv") Resource simpleCsv) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8);

        String ingestResponse = performAndLog(post("/api/analysis/ingestCsv")
                .contentType(TEXT_PLAIN)
                .content(csvData));

       DataAnalysisResponseDto response = objectMapper.readValue(ingestResponse,DataAnalysisResponseDto.class);
        Long id = response.id();

        byte[] downloadedBytes = performAndLog(get("/api/analysis/{id}/download.json", id)).getBytes(UTF_8);

        Path downloadPath = Paths.get("src/test/resources/test-data/analysis.json");
        Files.createDirectories(downloadPath.getParent());
        Files.write(downloadPath, downloadedBytes);

        String downloadedContent = Files.readString(downloadPath, UTF_8);
        System.out.println("Downloaded content: " + downloadedContent);

        assertThat(downloadedContent).contains("\"id\" : " + id);
        assertThat(downloadedContent).contains("\"numberOfRows\" : " + response.numberOfRows());
        assertThat(downloadedContent).contains("\"numberOfColumns\" : " + response.numberOfColumns());
    }

}
