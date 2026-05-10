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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class DataAnalysisControllerTest {

    // @Mock a class object to inject that object into target mocks
    @Mock
    private DataAnalysisService dataAnalysisService;

    // Injects the mock object to this class
    @InjectMocks
    private DataAnalysisController dataAnalysisController;

    // MockMvc simulates http requests without starting a real server
    private MockMvc mockMvc;
    private DataAnalysisResponseDto sampleResponse;

    // Runs before each test
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dataAnalysisController).alwaysDo(print()).build();
        sampleResponse = new DataAnalysisResponseDto(
                1L,
                100,
                8,
                150L,
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
        String csvData = "name,age,profession,hobby,reads,smokes,swims,drives\n" +
                "Sujon,22,Engineer,cricket,true,false,true,false\n" +
                "Ryan,27,Teacher,hiking,true,false,false,true,false";
        long csvDataLength = csvData.length();

        when(dataAnalysisService.analyseCsvData(csvData)).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/analysis/ingestCsv").contentType("text/plain").content(csvData))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numberOfRows").value(100))
                .andExpect(jsonPath("$.numberOfColumns").value(8))
                .andExpect(jsonPath("$.totalCharacters").value(csvDataLength));

        verify(dataAnalysisService, times(1)).analyseCsvData(csvData);
    }
}
