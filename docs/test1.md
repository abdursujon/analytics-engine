## Part 1 – CSV Analysis Tests

This test suite verifies the core behaviour of the CSV ingestion and analysis API.  
All tests are written using **Spring Boot Test**, **MockMvc**, **JUnit 5**, and **AssertJ**.

### Scope
The tests validate CSV parsing, data profiling, persistence, and error handling for the
`POST /analytics-engine/ingestCsv` endpoint.

---
## Getting Started

Run the Part 1 tests:
```bash
./gradlew test --tests DataAnalysisApiTest
```
---

### Application Context

- **contextLoads**
    - Confirms the Spring application context starts successfully.
    - Ensures all beans are wired correctly.

---

### CSV Parsing & Analysis

- **shouldAnalyzeSimpleCsv**
    - Parses a simple 3×3 CSV.
    - Verifies row count, column count, character count.
    - Confirms zero null values per column.

- **shouldHandleSingleRowCsv**
    - Handles a CSV with only one data row.
    - Ensures minimal datasets are processed correctly.

- **shouldHandleLargeCsv**
    - Parses a larger dataset (10 rows, 6 columns).
    - Verifies scalability beyond trivial inputs.

- **shouldHandleEmptyCsvWithHeaderOnly**
    - Accepts a CSV with only a header row.
    - Ensures zero data rows are valid.
    - Still generates column statistics.

---

### Null Value Handling

- **shouldCountNullValuesCorrectly**
    - Detects and counts null/empty values per column.
    - Treats empty fields (`""`) as nulls.

- **shouldHandleMixedNullValues**
    - Validates null detection across a realistic dataset.
    - Ensures nulls in different positions are counted correctly.

---

### Persistence Verification

- **shouldPersistDataToDatabase**
    - Confirms that a successful analysis creates a database record.

- **shouldPersistCorrectAnalysisData**
    - Verifies persisted entity fields:
        - numberOfRows
        - numberOfColumns
        - totalCharacters
        - originalData
        - createdAt timestamp

- **shouldPersistColumnStatisticsEntities**
    - Ensures one `ColumnStatisticsEntity` is created per column.
    - Verifies correct null counts.
    - Confirms bidirectional parent–child relationship.

- **shouldHandleMultipleIngestRequests**
    - Confirms multiple CSV uploads are processed independently.
    - Verifies multiple analyses can coexist in the database.

---

### Error Handling & Validation

- **shouldReturnBadRequestForInvalidCsv**
    - Rejects malformed CSVs with inconsistent column counts.
    - Returns HTTP 400 without persisting data.

- **shouldReturnBadRequestForEmptyInput**
    - Rejects completely empty input.
    - Differentiates between empty input and header-only CSV.

- **shouldRejectCsvContainingSonnyHayes**
    - Enforces business-rule validation.
    - Rejects CSVs containing `"Sonny Hayes"`.
    - Confirms no persistence occurs on validation failure.

---

### What This Test Suite Ensures

- Correct CSV structure parsing
- Accurate row, column, character, and null counting
- Robust handling of edge cases
- Proper database persistence and relationships
- Safe handling of invalid input
- Clean HTTP error responses (400 Bad Request)
- Idempotent, production-style API behaviour

This suite provides strong confidence that the CSV ingestion layer behaves correctly
under real-world data engineering scenarios.

## Test locally 
curl -X POST http://localhost:8080/analytics-engine/ingestCsv \
-H "Content-Type: text/csv" \
--data-binary @large.csv
