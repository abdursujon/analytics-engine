package com.sujon.analytics_engine.controller;

import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.model.ColumnStatistics;
import com.sujon.analytics_engine.service.DataAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.OffsetDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class DataAnalysisControllerTest {

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
    void setUp() throws Exception{
        mockMvc = MockMvcBuilders.standaloneSetup(dataAnalysisController).alwaysDo(print()).build();
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
    void ingestAndAnalyseCsv_shouldReturnResponseById_whenValidCsvProvided() throws Exception{
        Long id = 1L;
        when(dataAnalysisService.getAnalysisById(id)).thenReturn(sampleResponse);
        mockMvc.perform(get("/analytics-engine/{id}", id))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numberOfRows").value(11))
                .andExpect(jsonPath("$.numberOfColumns").value(6))
                .andExpect(jsonPath("$.totalCharacters").value(csvDataLength));
        verify(dataAnalysisService, times(1)).getAnalysisById(id);
    }

    @Test
    void deleteAnalysisById() throws Exception{
        Long id = 1L;
        doNothing().when(dataAnalysisService).deleteAnalysisById(id);

        mockMvc.perform(delete("/analytics-engine/{id}", id))
                .andExpect(status().isNoContent());

        verify(dataAnalysisService, times(1)).deleteAnalysisById(id);
    }
}
