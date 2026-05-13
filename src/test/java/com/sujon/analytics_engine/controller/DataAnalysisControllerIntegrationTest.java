package com.sujon.analytics_engine.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
public class DataAnalysisControllerIntegrationTest {

    public DataAnalysisControllerIntegrationTest() throws IOException {
    }

    @Autowired
    private MockMvc mockMvc;
    private String validCsv;

    @BeforeEach
    void setUp() throws Exception {
        validCsv = new String(
                getClass().getClassLoader().getResourceAsStream("test-data/large.csv").readAllBytes()
        );
    }

    @Test
    void ingestAndAnalyseCsv_shouldReturnResponse_whenValidCsvProvided() throws Exception {
        mockMvc.perform(post("/analytics-engine/ingestCsv")
                        .contentType("text/plain").content(validCsv))
                .andDo(print())

                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numberOfRows").value(10))
                .andExpect(jsonPath("$.numberOfColumns").value(6))
                .andExpect(jsonPath("$.totalCharacters").value(validCsv.length()))

                .andExpect(jsonPath("$.columnStatistics[0].columnName").value("driver"))
                .andExpect(jsonPath("$.columnStatistics[0].nullCount").value(0))
                .andExpect(jsonPath("$.columnStatistics[0].uniqueCount").value(10))
                .andExpect(jsonPath("$.columnStatistics[0].isNumeric").value(false))
                .andExpect(jsonPath("$.columnStatistics[0].min").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].max").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].mean").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].median").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].standardDeviation").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].percentiles").doesNotExist())

                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.alreadyExists").value(false));
    }


    @Test
    void getAnalysisById_shouldReturnResponse_whenIdExists() throws Exception {
        MvcResult result = mockMvc.perform(post("/analytics-engine/ingestCsv")
                        .contentType("text/plain").content(validCsv))
                .andExpect(status().isOk())
                .andReturn();
        Number idNum = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        long id = idNum.longValue();

        mockMvc.perform(get("/analytics-engine/{id}", id))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.numberOfRows").value(10))
                .andExpect(jsonPath("$.numberOfColumns").value(6))
                .andExpect(jsonPath("$.totalCharacters").value(validCsv.length()))

                .andExpect(jsonPath("$.columnStatistics[0].columnName").value("driver"))
                .andExpect(jsonPath("$.columnStatistics[0].nullCount").value(0))
                .andExpect(jsonPath("$.columnStatistics[0].uniqueCount").value(10))
                .andExpect(jsonPath("$.columnStatistics[0].isNumeric").value(false))
                .andExpect(jsonPath("$.columnStatistics[0].min").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].max").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].mean").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].median").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].standardDeviation").doesNotExist())
                .andExpect(jsonPath("$.columnStatistics[0].percentiles").doesNotExist())

                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.alreadyExists").value(true));
    }
}
