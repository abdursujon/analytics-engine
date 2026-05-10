package com.sujon.analytics_engine; // Package declaration for test class

import com.fasterxml.jackson.databind.ObjectMapper; // JSON parsing for response
import com.sujon.analytics_engine.dto.DataAnalysisResponseDto;
import com.sujon.analytics_engine.model.ColumnStatistics; // Column statistics model
import com.sujon.analytics_engine.repository.DataAnalysisRepository; // Repository for cleanup
import org.junit.jupiter.api.BeforeEach; // Setup annotation
import org.junit.jupiter.api.Test; // Test annotation
import org.springframework.beans.factory.annotation.Autowired; // Dependency injection
import org.springframework.beans.factory.annotation.Value; // Resource injection
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; // MockMvc config
import org.springframework.boot.test.context.SpringBootTest; // Spring Boot test config
import org.springframework.core.io.Resource; // Resource loading
import org.springframework.test.context.ActiveProfiles; // Test profile activation
import org.springframework.test.web.servlet.MockMvc; // MockMvc for HTTP testing
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder; // Request builder

import java.util.List; // List type for percentiles

import static java.nio.charset.StandardCharsets.UTF_8; // UTF-8 charset
import static org.assertj.core.api.Assertions.assertThat; // AssertJ assertions
import static org.assertj.core.api.Assertions.within; // AssertJ tolerance
import static org.springframework.http.MediaType.TEXT_PLAIN; // Content type
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; // GET request builder
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; // POST request builder
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print; // Print result handler

/**
 * Integration tests for statistical profiling feature.
 * Tests min, max, mean, median, standard deviation, and percentiles calculation
 * for numeric columns in CSV data.
 */
@SpringBootTest // Load full Spring context for integration testing
@AutoConfigureMockMvc // Configure MockMvc automatically
@ActiveProfiles("test") // Use test profile for test database
class StatisticalProfilingTest {

    @Autowired // Inject MockMvc for HTTP testing
    private MockMvc mockMvc;

    @Autowired // Inject repository for database cleanup
    private DataAnalysisRepository dataAnalysisRepository;

    @Autowired // Inject ObjectMapper for JSON parsing
    private ObjectMapper objectMapper;

    /**
     * Helper method to perform HTTP request and log response.
     *
     * @param requestBuilder the request to perform
     * @return the response body as string
     */
    private String performAndLog(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        var result = mockMvc.perform(requestBuilder) // Execute the request
                .andDo(print()) // Print request/response details
                .andReturn(); // Return the result

        System.out.println("Response body: " + result.getResponse().getContentAsString()); // Log response
        System.out.println("Status: " + result.getResponse().getStatus()); // Log status code
        return result.getResponse().getContentAsString(); // Return response body
    }

    @BeforeEach // Run before each test
    void setUp() {
        dataAnalysisRepository.deleteAll(); // Clear database before each test
    }

    @Test // Test method annotation
    void shouldIdentifyNumericColumns(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // Verify name column is not numeric (contains strings)
        ColumnStatistics nameStats = response.columnStatistics().stream() // Find name column stats
                .filter(s -> s.columnName().equals("name")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw
        assertThat(nameStats.isNumeric()).isFalse(); // Assert not numeric
        assertThat(nameStats.min()).isNull(); // Assert no min for non-numeric
        assertThat(nameStats.max()).isNull(); // Assert no max for non-numeric

        // Verify score column is numeric
        ColumnStatistics scoreStats = response.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw
        assertThat(scoreStats.isNumeric()).isTrue(); // Assert is numeric
        assertThat(scoreStats.min()).isNotNull(); // Assert has min value
        assertThat(scoreStats.max()).isNotNull(); // Assert has max value
    }

    @Test // Test method annotation
    void shouldCalculateMinAndMaxCorrectly(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // Test score column: values are 85,90,75,95,80,70,88,72,92,78
        ColumnStatistics scoreStats = response.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(scoreStats.min()).isEqualTo(70.0); // Min score is 70
        assertThat(scoreStats.max()).isEqualTo(95.0); // Max score is 95

        // Test age column: values are 25,30,22,28,35,27,32,24,29,26
        ColumnStatistics ageStats = response.columnStatistics().stream() // Find age column stats
                .filter(s -> s.columnName().equals("age")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(ageStats.min()).isEqualTo(22.0); // Min age is 22
        assertThat(ageStats.max()).isEqualTo(35.0); // Max age is 35
    }

    @Test // Test method annotation
    void shouldCalculateMeanCorrectly(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // Test score column: values are 85,90,75,95,80,70,88,72,92,78 = 825/10 = 82.5
        ColumnStatistics scoreStats = response.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(scoreStats.mean()).isCloseTo(82.5, within(0.01)); // Mean score is 82.5

        // Test salary column: values sum to 559000/10 = 55900
        ColumnStatistics salaryStats = response.columnStatistics().stream() // Find salary column stats
                .filter(s -> s.columnName().equals("salary")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(salaryStats.mean()).isCloseTo(55900.0, within(0.01)); // Mean salary is 55900
    }

    @Test // Test method annotation
    void shouldCalculateMedianCorrectly(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // Test score column: sorted values 70,72,75,78,80,85,88,90,92,95
        // Median of 10 values = average of 5th and 6th = (80+85)/2 = 82.5
        ColumnStatistics scoreStats = response.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(scoreStats.median()).isCloseTo(82.5, within(0.01)); // Median score is 82.5

        // Test age column: sorted values 22,24,25,26,27,28,29,30,32,35
        // Median of 10 values = average of 5th and 6th = (27+28)/2 = 27.5
        ColumnStatistics ageStats = response.columnStatistics().stream() // Find age column stats
                .filter(s -> s.columnName().equals("age")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(ageStats.median()).isCloseTo(27.5, within(0.01)); // Median age is 27.5
    }

    @Test // Test method annotation
    void shouldCalculateStandardDeviationCorrectly(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // Test score column standard deviation
        ColumnStatistics scoreStats = response.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        // Population std dev for scores: sqrt(sum((x-mean)^2)/n)
        // Values: 85,90,75,95,80,70,88,72,92,78, mean=82.5
        // Variance = ((2.5)^2 + (7.5)^2 + (-7.5)^2 + (12.5)^2 + (-2.5)^2 + (-12.5)^2 + (5.5)^2 + (-10.5)^2 + (9.5)^2 + (-4.5)^2)/10
        // = (6.25 + 56.25 + 56.25 + 156.25 + 6.25 + 156.25 + 30.25 + 110.25 + 90.25 + 20.25)/10 = 688.5/10 = 68.85
        // Std dev = sqrt(68.85) ≈ 8.297
        assertThat(scoreStats.standardDeviation()).isCloseTo(8.297, within(0.01)); // Std dev is ~8.297
    }

    @Test // Test method annotation
    void shouldCalculatePercentilesCorrectly(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        ColumnStatistics scoreStats = response.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        List<Double> percentiles = scoreStats.percentiles(); // Get percentiles list
        assertThat(percentiles).hasSize(6); // Should have 6 percentiles (25,50,75,90,95,99)

        // Sorted scores: 70,72,75,78,80,85,88,90,92,95
        // 25th percentile (index 2.25) = interpolate between 75 and 78 = 75.75
        assertThat(percentiles.get(0)).isCloseTo(75.75, within(0.1)); // 25th percentile

        // 50th percentile = median = 82.5
        assertThat(percentiles.get(1)).isCloseTo(82.5, within(0.1)); // 50th percentile

        // 75th percentile (index 6.75) = interpolate between 88 and 90 = 89.5
        assertThat(percentiles.get(2)).isCloseTo(89.5, within(0.1)); // 75th percentile
    }

    @Test // Test method annotation
    void shouldReturnNullStatsForNonNumericColumns(
            @Value("classpath:test-data/simple.csv") Resource simpleCsv // Load test CSV with non-numeric data
    ) throws Exception {
        String csvData = simpleCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // driver column should not be numeric
        ColumnStatistics driverStats = response.columnStatistics().stream() // Find driver column stats
                .filter(s -> s.columnName().equals("driver")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(driverStats.isNumeric()).isFalse(); // Should not be numeric
        assertThat(driverStats.min()).isNull(); // No min for non-numeric
        assertThat(driverStats.max()).isNull(); // No max for non-numeric
        assertThat(driverStats.mean()).isNull(); // No mean for non-numeric
        assertThat(driverStats.median()).isNull(); // No median for non-numeric
        assertThat(driverStats.standardDeviation()).isNull(); // No std dev for non-numeric
        assertThat(driverStats.percentiles()).isNull(); // No percentiles for non-numeric
    }

    @Test // Test method annotation
    void shouldPersistStatisticalDataToDatabase(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        var entities = dataAnalysisRepository.findAll(); // Get all analysis entities
        assertThat(entities).hasSize(1); // Should have one entity

        var entity = entities.get(0); // Get the entity
        assertThat(entity.getColumnStatistics()).hasSize(5); // Should have 5 columns

        // Verify score column stats are persisted
        assertThat(entity.getColumnStatistics()) // Check column statistics
                .anyMatch(stat -> stat.getColumnName().equals("score") // Find score column
                        && stat.isNumeric() // Should be numeric
                        && stat.getMinValue() == 70.0 // Min should be 70
                        && stat.getMaxValue() == 95.0); // Max should be 95
    }

    @Test // Test method annotation
    void shouldReturnStatisticsOnGetById(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        String ingestResponse = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto ingestResult = objectMapper.readValue(ingestResponse, DataAnalysisResponseDto.class); // Parse response
        Long id = ingestResult.id(); // Get analysis ID

        String getResponse = performAndLog(get("/api/analysis/{id}", id)); // GET by ID
        DataAnalysisResponseDto getResult = objectMapper.readValue(getResponse, DataAnalysisResponseDto.class); // Parse response

        // Verify statistics are returned on GET request
        ColumnStatistics scoreStats = getResult.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(scoreStats.isNumeric()).isTrue(); // Should be numeric
        assertThat(scoreStats.min()).isEqualTo(70.0); // Min should be 70
        assertThat(scoreStats.max()).isEqualTo(95.0); // Max should be 95
        assertThat(scoreStats.mean()).isCloseTo(82.5, within(0.01)); // Mean should be 82.5
    }

    @Test // Test method annotation
    void shouldHandleHeaderOnlyCsvWithNoStats(
            @Value("classpath:test-data/header-only.csv") Resource headerOnlyCsv // Load header-only CSV
    ) throws Exception {
        String csvData = headerOnlyCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // All columns should not be numeric (no data to analyze)
        for (ColumnStatistics stats : response.columnStatistics()) { // Iterate all columns
            assertThat(stats.isNumeric()).isFalse(); // Should not be numeric
            assertThat(stats.min()).isNull(); // No min
            assertThat(stats.max()).isNull(); // No max
            assertThat(stats.mean()).isNull(); // No mean
        }
    }

    @Test // Test method annotation
    void shouldReturnCachedStatisticsForDuplicateContent(
            @Value("classpath:test-data/numeric-stats.csv") Resource numericCsv // Load test CSV
    ) throws Exception {
        String csvData = numericCsv.getContentAsString(UTF_8); // Read CSV content

        // First request - creates new analysis
        String firstResponse = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto firstResult = objectMapper.readValue(firstResponse, DataAnalysisResponseDto.class); // Parse response
        assertThat(firstResult.alreadyExists()).isFalse(); // Should be new

        // Second request with same content - should return cached
        String secondResponse = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto secondResult = objectMapper.readValue(secondResponse, DataAnalysisResponseDto.class); // Parse response
        assertThat(secondResult.alreadyExists()).isTrue(); // Should be cached

        // Verify statistics are the same
        ColumnStatistics firstScoreStats = firstResult.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        ColumnStatistics secondScoreStats = secondResult.columnStatistics().stream() // Find score column stats
                .filter(s -> s.columnName().equals("score")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw

        assertThat(secondScoreStats.min()).isEqualTo(firstScoreStats.min()); // Min should match
        assertThat(secondScoreStats.max()).isEqualTo(firstScoreStats.max()); // Max should match
        assertThat(secondScoreStats.mean()).isEqualTo(firstScoreStats.mean()); // Mean should match
    }

    @Test // Test method annotation
    void shouldHandleMixedNumericAndNonNumericColumns(
            @Value("classpath:test-data/large.csv") Resource largeCsv // Load large CSV with mixed columns
    ) throws Exception {
        String csvData = largeCsv.getContentAsString(UTF_8); // Read CSV content

        String responseBody = performAndLog(post("/api/analysis/ingestCsv") // POST to ingest endpoint
                .contentType(TEXT_PLAIN) // Set content type
                .content(csvData)); // Set request body

        DataAnalysisResponseDto response = objectMapper.readValue(responseBody, DataAnalysisResponseDto.class); // Parse response

        // driver column should not be numeric (contains names)
        ColumnStatistics driverStats = response.columnStatistics().stream() // Find driver column stats
                .filter(s -> s.columnName().equals("driver")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw
        assertThat(driverStats.isNumeric()).isFalse(); // Should not be numeric

        // number column should be numeric
        ColumnStatistics numberStats = response.columnStatistics().stream() // Find number column stats
                .filter(s -> s.columnName().equals("number")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw
        assertThat(numberStats.isNumeric()).isTrue(); // Should be numeric
        assertThat(numberStats.min()).isNotNull(); // Should have min
        assertThat(numberStats.max()).isNotNull(); // Should have max

        // podiums column should be numeric
        ColumnStatistics podiumsStats = response.columnStatistics().stream() // Find podiums column stats
                .filter(s -> s.columnName().equals("podiums")) // Filter by name
                .findFirst().orElseThrow(); // Get or throw
        assertThat(podiumsStats.isNumeric()).isTrue(); // Should be numeric
    }
}
