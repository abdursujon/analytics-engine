# Implementation steps (Parquet support)

1. Add DuckDB JDBC dependency to build.gradle. x
2. Configure multipart upload limits in application.yml (5MB cap). x
3. Update DataAnalysisEntity: make originalData nullable, add format column (enum: CSV / PARQUET). x
4. Update DataAnalysisResponseDto: add format field. x
5. Refactor DataAnalysisService: extract shared stats-computation core method so both CSV and Parquet paths reuse it. X
6. Update CSV path to use the new shared core (verify no behavior change).X
7. Add Parquet ingestion method in DataAnalysisService: hash bytes, dedup, write temp file, read schema + rows via DuckDB, call shared core, clean up temp file. X
8. Add /ingestParquet endpoint in DataAnalysisController (multipart/form-data).X
9. Add validation: file extension check, empty-file check, cell-count cap. x
10. Create test fixture: small sample.parquet in src/test/resources/parquet/ (with a reproducible PyArrow generation script). X
11. Add unit tests for the new service methods (mocked repositories). 
12. Add integration test for the new controller endpoint (MockMvc multipart upload).
13. Verify existing CSV tests still pass (regression check after refactor). X


## Convert CSV to Parquet (Ubuntu/Debian)

Prerequisites:
sudo apt install python3-venv -y

Steps:

1. Create a virtual environment (one-time, in your project root):
   python3 -m venv .venv
2. Activate it (every new terminal session):
   source .venv/bin/activate
2. Prompt will show (.venv).
3. Install dependencies (one-time per venv):
   pip install pandas pyarrow
4. Convert — cd to the folder with your CSV, then:
   python3 -c "import pandas as pd; pd.read_csv('titanic-dataset.csv').to_parquet('titanic-dataset.parquet', index=False)"
5. Verify:
   duckdb -c "SELECT * FROM 'titanic-dataset.parquet' LIMIT 5"
6. Exit venv when done:
   deactivate

## Duck db setup 
## DuckDB setup for the Spring Boot project
1. Add dependency in build.gradle under dependencies:
   implementation 'org.duckdb:duckdb_jdbc:1.1.3'
2. Refresh Gradle:
   ./gradlew build --refresh-dependencies
3. Verify the jar resolves:
   ./gradlew dependencies --configuration runtimeClasspath | grep duckdb
3. Should print org.duckdb:duckdb_jdbc:1.1.3.

## DuckDB CLI for local testing (not required by the app):
snap install duckdb
duckdb -c "SELECT * FROM 'titanic-dataset.parquet' LIMIT 5"
duckdb -c "SELECT * FROM 'empty.parquet' LIMIT 5"


Implementation steps (JSON / NDJSON support)

1. Add JSON value to DataFormat enum. X
2. No new dependency needed — Jackson is already pulled in by spring-boot-starter-web. X
3. Allow both JSON and NDJSON (Newline Delimited JSON) x
   - JSON array of flat objects: [{"a":1,"b":"x"},{"a":2,"b":"y"}]
   - NDJSON (one object per line): {"a":1,"b":"x"}\n{"a":2,"b":"y"}
4. Add analyseJsonData(String body, boolean isNdjson) in DataAnalysisService: x
   - Hash the raw text (same sha256 + normalizeForHash as CSV, or hash raw for NDJSON)
   - Dedup via findByContentHash
   - Parse with Jackson → list of Map<String,Object> records
   - Union all keys across records → ordered headers[]
   - Per-column: infer numeric from JSON value type (Number → numeric; null → null; everything else → non-numeric)
   - Build the same 4 intermediate arrays the CSV/Parquet paths build
   - Call shared computeAndPersistStats(..., DataFormat.JSON, ...)
5. Reject deeply nested values (objects/arrays inside a field) with a clear error — out of scope for a tabular profiler. x
6. Add 2 endpoints in DataAnalysisController: x
   - POST /analytics-engine/ingestJson — body application/json
   - POST /analytics-engine/ingestNdjson — body application/x-ndjson
7. Validation: empty body, file-size cap (5MB), cell-count cap (1M), malformed JSON. x
8. Create test fixtures in src/test/resources/json/: x
   - sample.json (array form)
   - sample.ndjson (line-delimited)
   - empty.json ([])
   - mixed-null.json
   - nested.json (should fail validation)
   - invalid.json (broken syntax)
9. Add unit tests for the new service methods (mocked repos).
10. Add integration tests for both endpoints (MockMvc).
11. Verify existing CSV + Parquet tests still pass. x
12. Frontend: send Content-Type: application/json (string body) or application/x-ndjson. No multipart needed since JSON is text. x

  ---
JSON test fixtures — generation commands

mkdir -p src/test/resources/json
cd src/test/resources/json

# 1. Array of flat objects — happy path
cat > sample.json <<'EOF'
[
{"id": 1, "name": "alice", "score": 1.5},
{"id": 2, "name": "bob",   "score": 2.5},
{"id": 3, "name": null,    "score": 3.5},
{"id": 4, "name": "carol", "score": null}
]
EOF

# 2. NDJSON (one object per line)
cat > sample.ndjson <<'EOF'   
{"id": 1, "name": "alice", "score": 1.5}
{"id": 2, "name": "bob",   "score": 2.5}
{"id": 3, "name": null,    "score": 3.5}
{"id": 4, "name": "carol", "score": null}
EOF

# 3. Empty array
echo '[]' > empty.json

# 4. Single row, all values present
echo '[{"id": 42, "name": "alice", "score": 3.14}]' > single-row.json

# 5. Mixed nulls — every column has some
cat > mixed-null.json <<'EOF'
[
{"id": 1,    "name": "a",  "score": 1.5},
{"id": 2,    "name": null, "score": 2.5},
{"id": null, "name": "b",  "score": null},
{"id": null, "name": null, "score": null}
]
EOF

# 6. Nested — should be rejected
cat > nested.json <<'EOF'
[{"id": 1, "meta": {"tag": "x"}}]
EOF

# 7. Invalid syntax
echo '[{"id": 1, ' > invalid.json

  ---
Manual test (after implementation)

# Array
curl -X POST http://localhost:8080/analytics-engine/ingestJson \
-H "Content-Type: application/json" \
--data-binary @sample.json | jq

# NDJSON
curl -X POST http://localhost:8080/analytics-engine/ingestNdjson \
-H "Content-Type: application/x-ndjson" \
--data-binary @sample.ndjson | jq