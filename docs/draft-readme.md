# Analytics Engine

A Spring Boot REST API that profiles tabular data (CSV and Parquet). Upload a CSV and the service infers column types, counts nulls and unique values, computes summary statistics (min, max, mean, median, standard deviation, and the
25/50/75/90/95/99 percentiles), and persists the results so they can be retrieved or downloaded later. Repeated uploads of the same content are detected via SHA-256 hashing and short-circuit to the cached analysis.

The project is intentionally scoped as a data-profiling component — the kind of building block that sits inside a larger data pipeline, rather than a pipeline itself. Focus is on clean Spring layering, a clear REST contract,
and a tested codebase.

  ---

## Try it

- **Live API:** https://spring-data-analysis-506639246506.europe-west2.run.app/
- **Web UI:** https://abdursujon.github.io/transform-my-raw-data/

### How to use the service from terminal
## Parquet File Upload
**Linux / macOS**
```bash
curl -X POST \
-F "file=@your-file.parquet" \
https://spring-data-analysis-506639246506.europe-west2.run.app/analytics-engine/ingestParquet | jq
 ```
## CSV File Upload
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

## API Endpoints

| Method | Path                                      | Description                              |
  |--------|-------------------------------------------|------------------------------------------|
| POST   | `/analytics-engine/ingestParquet`         | Upload Parquet (multipart); returns the analysis |
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
  "alreadyExists": false,
  "format": "CSV"
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
  ./gradlew bootRun or ./gradlew clean bootRun
 
  ```

- Service starts on `http://localhost:8080/analytics-engine`
- Swagger UI is at `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON (raw spec) `http://localhost:8080/v3/api-docs`
- H2 database web console `http://localhost:8080/h2-console`
  (JDBC URL: jdbc:h2:mem:analysis-db, Username: sa, Password: (blank — leave the field empty)
- Spring Actuator health check `http://localhost:8080/actuator/health`

### Run tests

  ```bash
  ./gradlew test
  ```

### Upload File and Test Locally
**Linux / macOS**
```bash
curl -X POST \
  -H "Content-Type: text/csv" \
  --data-binary @your-file.csv \
  http://localhost:8080/analytics-engine/ingestCsv | jq
```

**Windows (PowerShell)**
```powershell
curl -X POST -H "Content-Type: text/csv" --data-binary "@your-file.csv" `
      http://localhost:8080/analytics-engine/ingestCsv | ConvertFrom-Json
An HTML report is generated at `build/reports/tests/test/index.html`.
```
The response includes the assigned `id`. You can fetch or download the analysis later:
```bash
    # Retrieve as JSON
    curl http://localhost:8080/analytics-engine/{id} | jq

    # Download as a JSON file   
    curl -o analysis.json \
      http://localhost:8080/analytics-engine/{id}/download.json

    # Delete an analysis 
    curl -X DELETE http://localhost:8080/analytics-engine/{id}
```
### Build a runnable jar

```bash
  ./gradlew clean bootJar
  java -jar build/libs/analytics-engine.jar
```
### Bootrun issues and how to fix
### Invoke the wrapper jar directly to regenerate the wrapper scripts:
```bash
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain wrapper
chmod +x gradlew
./gradlew clean bootRun
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