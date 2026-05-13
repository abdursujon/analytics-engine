package com.sujon.analytics_engine.controller;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.exception.GlobalExceptionHandler;
import com.sujon.analytics_engine.exception.NotFoundException;

import com.sujon.analytics_engine.model.ColumnStatistics;
import com.sujon.analytics_engine.service.DataAnalysisService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for DataAnalysisController all endpoints.
 * Sequence of tests: post, get and delete
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
public class DataAnalysisControllerUnitTest {

    // MockMvc simulates http requests without starting a real server
    private MockMvc mockMvc;
    private DataAnalysisResponseDto sampleResponse;
    private String csvData;
    private long csvDataLength;

    // @Mock a class object to inject that object into target mocks
    @Mock
    private DataAnalysisService dataAnalysisService;

    // Injects the mock object to this class
    @InjectMocks
    private DataAnalysisController dataAnalysisController;


    // Runs before each test
    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(dataAnalysisController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .alwaysDo(print())
                .build();
        csvData = new String(
                getClass().getClassLoader().getResourceAsStream("test-data/large.csv").readAllBytes()
        );
        Long id = 1L;
        csvDataLength = csvData.length();

        sampleResponse = new DataAnalysisResponseDto(
                id,
                11,
                6,
                csvDataLength,
                List.of(new ColumnStatistics(
                        "name",
                        0,
                        2,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )),
                OffsetDateTime.now(),
                false
        );
    }


    @Test
    void ingestAndAnalyseCsv_shouldReturnResponse_whenValidCsvProvided() throws Exception {
        when(dataAnalysisService.analyseCsvData(csvData)).thenReturn(sampleResponse);
        mockMvc.perform(post("/analytics-engine/ingestCsv").contentType("text/plain").content(csvData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numberOfRows").value(11))
                .andExpect(jsonPath("$.numberOfColumns").value(6))
                .andExpect(jsonPath("$.totalCharacters").value(csvDataLength));
        verify(dataAnalysisService, times(1)).analyseCsvData(csvData);
    }


    @Test
    void getAnalysisById_shouldReturnResponse_whenIdExists() throws Exception {
        when(dataAnalysisService.getAnalysisById(1L)).thenReturn(sampleResponse);
        mockMvc.perform(get("/analytics-engine/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numberOfRows").value(11))
                .andExpect(jsonPath("$.numberOfColumns").value(6))
                .andExpect(jsonPath("$.totalCharacters").value(csvDataLength));
        verify(dataAnalysisService, times(1)).getAnalysisById(1L);
    }


    @Test
    void getAnalysisById_shouldReturn404_whenIdDoesNotExist() throws Exception {
        when(dataAnalysisService.getAnalysisById(99L)).thenThrow(new NotFoundException("Not found"));
        mockMvc.perform(get("/analytics-engine/99"))
                .andExpect(status().isNotFound());
        verify(dataAnalysisService, times(1)).getAnalysisById(99L);
    }


    @Test
    void downloadJson_shouldReturnJsonFile_whenIdExist() throws Exception {
        when(dataAnalysisService.getAnalysisById(1L)).thenReturn(sampleResponse);
        mockMvc.perform(get("/analytics-engine/1/download.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"analysis.json\""))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        verify(dataAnalysisService, times(1)).getAnalysisById(1L);
    }


    @Test
    void downloadJson_shouldReturn404_whenIdDoesNotExist() throws Exception {
        when(dataAnalysisService.getAnalysisById(99L)).thenThrow(new NotFoundException("Id not found"));
        mockMvc.perform(get("/analytics-engine/99/download.json"))
                .andExpect(status().isNotFound());
        verify(dataAnalysisService, times(1)).getAnalysisById(99L);
    }


    @Test
    void deleteAnalysisById_shouldReturn204_whenIdExists() throws Exception {
        Long id = 1L;
        doNothing().when(dataAnalysisService).deleteAnalysisById(id);
        mockMvc.perform(delete("/analytics-engine/{id}", id))
                .andExpect(status().isNoContent());
        verify(dataAnalysisService, times(1)).deleteAnalysisById(id);
    }


    @Test
    void deleteAnalysisById_shouldReturn404_whenIdDoesNotExists() throws Exception {
        doThrow(new NotFoundException("Analysis ID not found")).doNothing().when(dataAnalysisService).deleteAnalysisById(99L);
        mockMvc.perform(delete("/analytics-engine/99"))
                .andExpect(status().isNotFound());
        verify(dataAnalysisService, times(1)).deleteAnalysisById(99L);
    }

}
