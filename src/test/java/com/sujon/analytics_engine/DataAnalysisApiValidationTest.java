package com.sujon.analytics_engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.repository.DataAnalysisRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;

/**
 * This test class validates the API’s CSV ingestion, analysis,
 * and persistence behavior using MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataAnalysisApiValidationTest {

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

        private String performAndLog(MockHttpServletRequestBuilder requestBuilder) throws Exception {
                var result = mockMvc.perform(requestBuilder)
                                .andDo(print())
                                .andReturn();

                System.out.println("Response body: " + result.getResponse().getContentAsString());
                System.out.println("Status: " + result.getResponse().getStatus());
                return result.getResponse().getContentAsString();
        }

        @Test
        void contextLoads() {
        }

        @Test
        void shouldReturnBadRequestForEmptyCsvFile(
                        @Value("classpath:test-data/empty.csv") Resource emptyCsv) throws Exception {
                String content = emptyCsv.getContentAsString(UTF_8);

                mockMvc.perform(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(content))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnBadRequestForEmptyTxtFile(
                        @Value("classpath:test-data/empty.txt") Resource emptyTxt) throws Exception {
                String content = emptyTxt.getContentAsString(UTF_8);

                mockMvc.perform(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(content))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldHandleEmptyCsvWithHeaderOnly(
                        @Value("classpath:test-data/header-only.csv") Resource emptyCsv) throws Exception {
                String csvData = emptyCsv.getContentAsString(UTF_8);

                var result = mockMvc.perform(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andReturn();

                DataAnalysisResponseDto response = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                DataAnalysisResponseDto.class);

                assertThat(response.numberOfRows()).isEqualTo(0);
                assertThat(response.numberOfColumns()).isEqualTo(3);
                assertThat(response.columnStatistics()).hasSize(3);
                assertThat(response.columnStatistics())
                                .anyMatch(stat -> stat.columnName().equals("driver") && stat.nullCount() == 0)
                                .anyMatch(stat -> stat.columnName().equals("number") && stat.nullCount() == 0)
                                .anyMatch(stat -> stat.columnName().equals("team") && stat.nullCount() == 0);
        }

        @Test
        void shouldHandleSingleRowCsv(
                        @Value("classpath:test-data/single-row.csv") Resource singleRowCsv) throws Exception {
                String csvData = singleRowCsv.getContentAsString(UTF_8);

                String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class);

                assertThat(response.numberOfRows()).isEqualTo(1);
                assertThat(response.numberOfColumns()).isEqualTo(3);
                assertThat(response.columnStatistics()).hasSize(3);
        }

        @Test
        void shouldCountNullValuesCorrectly(
                        @Value("classpath:test-data/with-nulls.csv") Resource withNullsCsv) throws Exception {
                String csvData = withNullsCsv.getContentAsString(UTF_8);

                String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class);

                assertThat(response.numberOfRows()).isEqualTo(4);
                assertThat(response.numberOfColumns()).isEqualTo(4);
                assertThat(response.columnStatistics()).hasSize(4);
                assertThat(response.columnStatistics())
                                .anyMatch(stat -> stat.columnName().equals("driver") && stat.nullCount() == 0)
                                .anyMatch(stat -> stat.columnName().equals("number") && stat.nullCount() == 2)
                                .anyMatch(stat -> stat.columnName().equals("team") && stat.nullCount() == 2)
                                .anyMatch(stat -> stat.columnName().equals("nationality") && stat.nullCount() == 0);
        }

        @Test
        void shouldHandleMixedNullValues(
                        @Value("classpath:test-data/mixed-nulls.csv") Resource mixedNullsCsv) throws Exception {
                String csvData = mixedNullsCsv.getContentAsString(UTF_8);

                String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class);

                assertThat(response.numberOfRows()).isEqualTo(6);
                assertThat(response.numberOfColumns()).isEqualTo(5);
                assertThat(response.columnStatistics()).hasSize(5);
                assertThat(response.columnStatistics())
                                .anyMatch(stat -> stat.columnName().equals("driver") && stat.nullCount() == 0)
                                .anyMatch(stat -> stat.columnName().equals("team") && stat.nullCount() == 1)
                                .anyMatch(stat -> stat.columnName().equals("number") && stat.nullCount() == 3)
                                .anyMatch(stat -> stat.columnName().equals("nationality") && stat.nullCount() == 1)
                                .anyMatch(stat -> stat.columnName().equals("podiums") && stat.nullCount() == 0);
        }

        @Test
        void shouldHandleLargeCsv(
                        @Value("classpath:test-data/large.csv") Resource largeCsv) throws Exception {
                String csvData = largeCsv.getContentAsString(UTF_8);

                String responseBody = performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class);

                assertThat(response.numberOfRows()).isEqualTo(10);
                assertThat(response.numberOfColumns()).isEqualTo(6);
                assertThat(response.totalCharacters()).isEqualTo(csvData.length());
                assertThat(response.columnStatistics()).hasSize(6);
        }

        @Test
        void shouldPersistDataToDatabase(
                        @Value("classpath:test-data/simple.csv") Resource simpleCsv) throws Exception {
                String csvData = simpleCsv.getContentAsString(UTF_8);

                performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                assertThat(dataAnalysisRepository.count()).isEqualTo(1);
        }

        @Test
        void shouldPersistCorrectAnalysisData(
                        @Value("classpath:test-data/simple.csv") Resource simpleCsv) throws Exception {
                String csvData = simpleCsv.getContentAsString(UTF_8);

                performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                var entities = dataAnalysisRepository.findAll();
                assertThat(entities).hasSize(1);

                var entity = entities.get(0);
                assertThat(entity.getNumberOfRows()).isEqualTo(3);
                assertThat(entity.getNumberOfColumns()).isEqualTo(3);
                assertThat(entity.getTotalCharacters()).isEqualTo(csvData.length());
                assertThat(entity.getOriginalData()).isEqualTo(csvData);
                assertThat(entity.getCreatedAt()).isNotNull();
        }

        @Test
        void shouldPersistColumnStatisticsEntities(
                        @Value("classpath:test-data/simple.csv") Resource simpleCsv) throws Exception {
                String csvData = simpleCsv.getContentAsString(UTF_8);

                performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                var entities = dataAnalysisRepository.findAll();
                assertThat(entities).hasSize(1);

                var entity = entities.get(0);
                assertThat(entity.getColumnStatistics()).hasSize(3);
                assertThat(entity.getColumnStatistics())
                                .anyMatch(stat -> stat.getColumnName().equals("driver") && stat.getNullCount() == 0)
                                .anyMatch(stat -> stat.getColumnName().equals("number") && stat.getNullCount() == 0)
                                .anyMatch(stat -> stat.getColumnName().equals("team") && stat.getNullCount() == 0);

                entity.getColumnStatistics().forEach(stat -> assertThat(stat.getDataAnalysis()).isEqualTo(entity));
        }

        @Test
        void shouldHandleMultipleIngestRequests(
                        @Value("classpath:test-data/simple.csv") Resource simpleCsv,
                        @Value("classpath:test-data/with-nulls.csv") Resource withNullsCsv) throws Exception {
                String csvData1 = simpleCsv.getContentAsString(UTF_8);
                String csvData2 = withNullsCsv.getContentAsString(UTF_8);

                performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData1));

                performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData2));

                assertThat(dataAnalysisRepository.count()).isEqualTo(2);
        }

        @Test
        void shouldReturnBadRequestForInvalidCsv(
                        @Value("classpath:test-data/invalid.csv") Resource invalidCsv) throws Exception {
                String csvData = invalidCsv.getContentAsString(UTF_8);

                mockMvc.perform(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectCsvContainingSonnyHayes(
                        @Value("classpath:test-data/sonny-hayes.csv") Resource sonnyHayesCsv) throws Exception {
                String csvData = sonnyHayesCsv.getContentAsString(UTF_8);

                performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(csvData));

                assertThat(dataAnalysisRepository.count()).isEqualTo(0);
        }

        @Test
        void shouldReturnBadRequestForEmptyInput() throws Exception {
                performAndLog(post("/api/analysis/ingestCsv")
                                .contentType(TEXT_PLAIN)
                                .content(""));
        }
}
