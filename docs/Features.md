# Implemented features:
1. Null and empty value detection via blank checks and nullCounts
2. Row shape validation by enforcing equal column count per row
3. Header presence validation by rejecting blank first line
4. Basic CSV structure validation by rejecting malformed rows
5. Per-column uniqueness tracking using Set
6. Numeric type detection using parse attempt per value
7. Min value calculation for numeric columns
8. Max value calculation for numeric columns
9. Mean calculation for numeric columns
10. Median calculation for numeric columns
11. Standard deviation calculation for numeric columns
12. Percentile calculation for numeric columns
13. Row counting with blank-line skipping
14. Total character count calculation
15. Content hashing for duplicate dataset detection
16. Cached analysis reuse via hash lookup
17. Persistent storage of analysis metadata
18. Persistent storage of per-column statistics
19. Max file limit 5mb/1,000,000 imposed
---

# Some important ideas
- Export functionality (JSON, Excel)
- Data filtering or transformation capabilities
- Batch processing of multiple CSVs
- Historical tracking and comparison
- Data visualization endpoints
- Column correlation analysis
- Missing data handling strategies

## Feature Scope
- Data ingestion and parsing
- Data validation and quality checks
- Schema inference and column type detection
- Schema stability and fingerprinting
- Column-level and dataset-level quality metrics
- Null, empty, and completeness analysis
- Uniqueness and cardinality analysis
- Statistical profiling (min, max, mean, median, std, percentiles)
- Categorical value profiling (top-K, dominance, long tail)
- Text column profiling (length, tokens, character patterns)
- Missing data pattern detection
- Missing data handling and imputation strategies
- Column correlation and dependency analysis
- Redundant and low-information column detection ****
- Anomaly and outlier detection
- Row-level and column-level validity labeling
- Dataset quality scoring and risk classification
- Rule-based issue detection and tagging
- Feature extraction and vectorization for ML
- Dataset fingerprinting and similarity detection
- Historical tracking and dataset versioning
- Dataset comparison and change analysis
- Data drift detection and monitoring
- Batch processing of multiple CSVs
- Consistent feature alignment across batches
- Model-ready data export (JSON, Parquet, etc.)
- Audit trail and lineage tracking
- Remediation recommendation generation
- Confidence scoring for predictions


# Analytics Engine

A Spring Boot REST API that profiles CSV data. Upload a CSV and the service infers column types, counts nulls and unique values, computes summary statistics (min, max, mean, median, standard deviation, and the
25/50/75/90/95/99 percentiles), and persists the results so they can be retrieved or downloaded later. Repeated uploads of the same content are detected via SHA-256 hashing and short-circuit to the cached analysis.

The project is intentionally scoped as a data-profiling component — the kind of building block that sits inside a larger data pipeline, rather than a pipeline itself. Focus is on clean Spring layering, a clear REST contract,
and a tested codebase.

> **Status:** complete and deployed. Scope is fixed — see [Limitations](#limitations) for what this service does and does not do.

  ---

## Try it

- **Live API:** https://spring-data-analysis-506639246506.europe-west2.run.app/
- **Web UI:** https://abdursujon.github.io/transform-my-raw-data/
- **Swagger UI (local):** http://localhost:8080/swagger-ui/index.html

### Quick start (curl)

**Linux / macOS**
  ```bash
  curl -X POST \
    -H "Content-Type: text/csv" \
    --data-binary @your-file.csv \
    https://spring-data-analysis-506639246506.europe-west2.run.app/analytics-engine/ingestCsv | jq
  ```

**Windows (PowerShell)**
  ```powershell
  curl -X POST -H "Content-Type: text/csv" --data-binary "@your-file.csv" `
    https://spring-data-analysis-506639246506.europe-west2.run.app/analytics-engine/ingestCsv | ConvertFrom-Json
  ```

The response includes the assigned `id`. You can fetch or download the analysis later:

  ```bash
  # Retrieve as JSON
  curl https://spring-data-analysis-506639246506.europe-west2.run.app/analytics-engine/{id} | jq

  # Download as a JSON file
  curl -o analysis.json \
    https://spring-data-analysis-506639246506.europe-west2.run.app/analytics-engine/{id}/download.json
  ```

  ---

## API

| Method | Path                                      | Description                              |
  |--------|-------------------------------------------|------------------------------------------|
| POST   | `/analytics-engine/ingestCsv`             | Upload CSV; returns the analysis         |
| GET    | `/analytics-engine/{id}`                  | Retrieve a previously stored analysis    |
| GET    | `/analytics-engine/{id}/download.json`    | Download the analysis as a JSON file     |
| DELETE | `/analytics-engine/{id}`                  | Delete an analysis                       |

### Example response

  ```json
  {
    "id": 1,
    "numberOfRows": 1000,
    "numberOfColumns": 3,
    "totalCharacters": 27543,
    "columnStatistics": [
      {
        "columnName": "age",
        "nullCount": 12,
        "uniqueCount": 67,
        "isNumeric": true,
        "min": 18.0,
        "max": 91.0,
        "mean": 42.3,
        "median": 41.0,
        "standardDeviation": 14.2,
        "percentiles": [29.0, 41.0, 55.0, 67.0, 73.0, 85.0]
      }
    ],
    "createdAt": "2026-01-15T10:30:00Z",
    "alreadyExists": false
  }
  ```

- `percentiles` is ordered as `[p25, p50, p75, p90, p95, p99]`, and is `null` for non-numeric columns.
- `alreadyExists` is `true` when the upload was deduplicated by content hash.

  ---

## Tech stack

- **Java 17**, **Spring Boot 3** (Web, JPA, Actuator)
- **Gradle** for builds
- **H2** as an in-memory database
- **Lombok** to cut boilerplate
- **JUnit 5** for unit and integration tests
- **Google Cloud Run** for deployment, via GitHub Actions

  ---

## Run locally

  ```bash
  git clone https://github.com/abdursujon/analytics-engine.git
  cd analytics-engine
  ./gradlew bootRun
  ```

Service starts on `http://localhost:8080`. Swagger UI is at `/swagger-ui/index.html`.

### Run tests

  ```bash
  ./gradlew test
  ```

An HTML report is generated at `build/reports/tests/test/index.html`.

### Build a runnable jar

  ```bash
  ./gradlew clean bootJar
  java -jar build/libs/analytics-engine.jar
  ```

### Free port 8080 if `bootRun` fails

  ```bash
  # Linux / macOS
  lsof -ti:8080 | xargs kill -9
  ```
  ```powershell
  # Windows (PowerShell)
  Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | ForEach-Object { Stop-Process -Id $_ -Force }
  ```

  ---

## Limitations

This service is intentionally a small, focused component, not a production data platform.
- **In-memory storage.** H2 is configured in-memory, so analyses are lost on restart.
- **5 MB upload cap** and a 1,000,000-cell limit per file, enforced at the service layer.
- **CSV only.** JSON and Excel are not supported.
- **No authentication.** CORS is open (`*`) — appropriate for a public demo, not for production.
- **Single-instance, synchronous.** Everything runs in the request thread; large files block the response.

  ---