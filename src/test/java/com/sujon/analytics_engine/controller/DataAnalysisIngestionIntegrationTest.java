package com.sujon.analytics_engine.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Tag("integration")
public class DataAnalysisIngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private byte[] readTestResourceAsBytes(String resourcePath) throws IOException {
        return getClass().getClassLoader().getResourceAsStream(resourcePath).readAllBytes();
    }

    private long ingestCsvAndReturnId(String csvBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/analytics-engine/ingestCsv")
                        .contentType("text/csv").content(csvBody))
                .andExpect(status().isOk())
                .andReturn();
        Number analysisId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return analysisId.longValue();
    }

    @Test
    void ingestJson_shouldReturnStatisticsAndCorrelation_whenAllColumnsNumeric() throws Exception {
        mockMvc.perform(post("/analytics-engine/ingestJson")
                        .contentType("application/json")
                        .content(readTestResourceAsBytes("test-data/json/all-numeric.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("JSON"))
                .andExpect(jsonPath("$.numberOfRows").value(4))
                .andExpect(jsonPath("$.numberOfColumns").value(3))
                .andExpect(jsonPath("$.columnStatistics[1].columnName").value("score"))
                .andExpect(jsonPath("$.columnStatistics[1].isNumeric").value(true))
                .andExpect(jsonPath("$.columnStatistics[1].mean").value(closeTo(25.5, 1e-9)))
                .andExpect(jsonPath("$.columnStatistics[1].percentiles.length()").value(6))
                .andExpect(jsonPath("$.numericCorrelationMatrix.numericColumnNames.length()").value(3))
                .andExpect(jsonPath("$.numericCorrelationMatrix.correlationValues[0][1]").value(closeTo(1.0, 1e-9)));
    }

    @Test
    void ingestNdjson_shouldCountNullsAndInferText_whenValuesAreMixed() throws Exception {
        mockMvc.perform(post("/analytics-engine/ingestNdjson")
                        .contentType("application/x-ndjson")
                        .content(readTestResourceAsBytes("test-data/ndjson/mixed-null.ndjson")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfRows").value(4))
                .andExpect(jsonPath("$.columnStatistics[1].columnName").value("name"))
                .andExpect(jsonPath("$.columnStatistics[1].nullCount").value(2))
                .andExpect(jsonPath("$.columnStatistics[1].isNumeric").value(false));
    }

    @Test
    void ingestParquet_shouldReturnStatistics_whenValidParquetUploaded() throws Exception {
        MockMultipartFile parquetFile = new MockMultipartFile(
                "file", "all-numeric.parquet", "application/octet-stream",
                readTestResourceAsBytes("test-data/parquet/all-numeric.parquet"));

        mockMvc.perform(multipart("/analytics-engine/ingestParquet").file(parquetFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("PARQUET"))
                .andExpect(jsonPath("$.numberOfRows").value(4))
                .andExpect(jsonPath("$.columnStatistics[1].min").value(closeTo(10.5, 1e-9)))
                .andExpect(jsonPath("$.columnStatistics[1].max").value(closeTo(40.5, 1e-9)));
    }

    @Test
    void ingestParquet_shouldReturnBadRequest_whenFileIsNotParquet() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "invalid.parquet", "application/octet-stream",
                readTestResourceAsBytes("test-data/parquet/invalid.parquet"));

        mockMvc.perform(multipart("/analytics-engine/ingestParquet").file(invalidFile))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ingestCsv_shouldReturnStoredAnalysis_whenSameContentUploadedTwice() throws Exception {
        String csvBody = "city,population\nLondon,8900000\nLeeds,800000\n";
        long firstAnalysisId = ingestCsvAndReturnId(csvBody);

        mockMvc.perform(post("/analytics-engine/ingestCsv")
                        .contentType("text/csv").content(csvBody.replace("\n", "\r\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstAnalysisId))
                .andExpect(jsonPath("$.alreadyExists").value(true));
    }

    @Test
    void ingestJson_shouldReturnBadRequest_whenRecordsAreNested() throws Exception {
        mockMvc.perform(post("/analytics-engine/ingestJson")
                        .contentType("application/json")
                        .content(readTestResourceAsBytes("test-data/json/nested.json")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Nested")));
    }

    @Test
    void downloadJson_shouldReturnAttachment_whenAnalysisExists() throws Exception {
        long analysisId = ingestCsvAndReturnId("product,price\nPen,1.5\nBook,12\n");

        mockMvc.perform(get("/analytics-engine/{id}/download.json", analysisId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("analysis.json")))
                .andExpect(jsonPath("$.id").value(analysisId));
    }

    @Test
    void deleteAnalysisById_shouldRemoveAnalysis_whenIdExists() throws Exception {
        long analysisId = ingestCsvAndReturnId("colour,count\nred,3\nblue,5\n");

        mockMvc.perform(delete("/analytics-engine/{id}", analysisId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/analytics-engine/{id}", analysisId))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorHealth_shouldReportUp_whenApplicationIsRunning() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocs_shouldListIngestionEndpoints_whenRequested() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/analytics-engine/ingestCsv']").exists())
                .andExpect(jsonPath("$.paths['/analytics-engine/ingestParquet']").exists());
    }
}
