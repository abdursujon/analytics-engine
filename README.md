# Analytics Engine

A Spring Boot REST API that profiles tabular data and returns instant data insights.

**Web UI:** https://abdursujon.github.io/analytics-engine-ui/

## Features

Upload a file, get a full statistical profile back in one request.

- 📂 **Multi-format ingestion:** CSV, Parquet, JSON and NDJSON.
- 🔍 **Type inference:** Detects numeric and text columns automatically.
- 📊 **Summary statistics:** Min, max, mean, median, standard deviation and p25/p50/p75/p90/p95/p99 percentiles.
- 🔗 **Correlation matrix:** Pairwise correlation across numeric columns.
- 🧮 **Data quality:** Null and unique value counts per column.
- ♻️ **Deduplication:** SHA-256 content hashing returns the cached analysis for repeated uploads.
- 💾 **Persistence:** Retrieve, download or delete any stored analysis by id.

Built with:

- ☕ **Java 17** and **Spring Boot 3** (Web, JPA, Actuator)
- 🗄️ **H2** in-memory database
- 🐘 **Gradle** builds
- ✅ **JUnit 5** unit and integration tests
- 📘 **Swagger / OpenAPI** documentation
- ☁️ **AWS EC2** deployment

## API Endpoints

| Method | Path                                   | Description                    |
|--------|----------------------------------------|--------------------------------|
| POST   | `/analytics-engine/ingestCsv`          | Upload CSV                     |
| POST   | `/analytics-engine/ingestParquet`      | Upload Parquet (multipart)     |
| POST   | `/analytics-engine/ingestJson`         | Upload JSON                    |
| POST   | `/analytics-engine/ingestNdjson`       | Upload NDJSON                  |
| GET    | `/analytics-engine/{id}`               | Retrieve a stored analysis     |
| GET    | `/analytics-engine/{id}/download.json` | Download the analysis as JSON  |
| DELETE | `/analytics-engine/{id}`               | Delete an analysis             |

## Getting Started 🚀

### 1. Clone & run

```bash
git clone https://github.com/abdursujon/analytics-engine.git
cd analytics-engine
./gradlew bootRun
```

Visit http://localhost:8080/swagger-ui/index.html to explore the API.

### 2. Upload a file

```bash
# CSV
curl -X POST -H "Content-Type: text/csv" --data-binary @your-file.csv \
  http://localhost:8080/analytics-engine/ingestCsv | jq

# Parquet
curl -X POST -F "file=@your-file.parquet" \
  http://localhost:8080/analytics-engine/ingestParquet | jq

# JSON
curl -X POST -H "Content-Type: application/json" --data-binary @your-file.json \
  http://localhost:8080/analytics-engine/ingestJson | jq

# NDJSON
curl -X POST -H "Content-Type: application/x-ndjson" --data-binary @your-file.ndjson \
  http://localhost:8080/analytics-engine/ingestNdjson | jq
```

### 3. Fetch, download or delete

```bash
curl http://localhost:8080/analytics-engine/{id} | jq
curl -o analysis.json http://localhost:8080/analytics-engine/{id}/download.json
curl -X DELETE http://localhost:8080/analytics-engine/{id}
```

### 4. Test & build

```bash
./gradlew test                  # report: build/reports/tests/test/index.html
./gradlew clean bootJar
java -jar build/libs/analytics-engine.jar
```

### Useful URLs

- OpenAPI spec: http://localhost:8080/v3/api-docs
- H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:analysis-db`, user `sa`, blank password)
- Health check: http://localhost:8080/actuator/health

## Troubleshooting

```bash
# Regenerate the Gradle wrapper
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain wrapper
chmod +x gradlew

# Free port 8080
lsof -ti:8080 | xargs kill -9
```

## Limitations

- 💾 **In-memory storage:** Analyses are lost on restart.
- 📏 **Upload limits:** 5 MB per file and 1,000,000 cells.
- 🔓 **No authentication:** CORS is open, suited to a public demo.
- 🧵 **Synchronous:** Large files are processed in the request thread.
