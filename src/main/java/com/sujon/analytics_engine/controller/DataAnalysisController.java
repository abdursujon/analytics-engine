package com.sujon.analytics_engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.exception.BadRequestException;
import com.sujon.analytics_engine.exception.NotFoundException;
import com.sujon.analytics_engine.service.DataAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController   
@RequestMapping("/analytics-engine")
@CrossOrigin(
    origins = "*",
    allowedHeaders = "*",
    methods = {RequestMethod.POST, RequestMethod.GET, RequestMethod.DELETE, RequestMethod.OPTIONS}
) 
@RequiredArgsConstructor
public class DataAnalysisController {

    private final DataAnalysisService dataAnalysisService;

    @PostMapping(
            value = "/ingestParquet",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/json"
    )
    public DataAnalysisResponseDto ingestAndAnalyzeParquet(@RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Parquet file is required");
        }

        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".parquet")) {
            throw new BadRequestException("File must have .parquet extension");
        }

        try {
            return dataAnalysisService.analyseParquetData(file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Failed to read uploaded file");
        }
    }


    @PostMapping(
            value = "/ingestJson",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "application/json"
    )
    public DataAnalysisResponseDto ingestAndAnalyzeJson(@RequestBody String body) {
        return dataAnalysisService.analyseJsonData(body, false);
    }


    @PostMapping(
            value = "/ingestNdjson",
            consumes = "application/x-ndjson",
            produces = "application/json"
    )
    public DataAnalysisResponseDto ingestAndAnalyzeNdjson(@RequestBody String body) {
        return dataAnalysisService.analyseJsonData(body, true);
    }


    @PostMapping(
            value = "/ingestCsv",
            consumes = {"text/plain", "text/csv"},
            produces = "application/json"
    )
    public DataAnalysisResponseDto ingestAndAnalyzeCsv(@RequestBody String data) {
        return dataAnalysisService.analyseCsvData(data);
    }

    @GetMapping("/{id}")
    public DataAnalysisResponseDto getAnalysisById(@PathVariable Long id) {
        return dataAnalysisService.getAnalysisById(id);
    }


    // Allow download of json response
    @GetMapping("/{id}/download.json")
    public ResponseEntity<byte[]> downloadJson(@PathVariable Long id) {
        try {
            DataAnalysisResponseDto response = dataAnalysisService.getAnalysisById(id);
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            byte[] prettyJsonBytes = mapper.writeValueAsBytes(response);

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"analysis.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(prettyJsonBytes);

        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to generate JSON");
        }
    }


    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteAnalysisById(@PathVariable Long id) {
        dataAnalysisService.deleteAnalysisById(id);
    }
}
