1. Extract ContentHasher ✓
2. Extract AnalysisMapper ✓
3. Define ParsedDataset record ✓
4. Extract CsvParser → returns ParsedDataset x
5. Extract JsonParser → returns ParsedDataset (handles JSON + NDJSON) ✓
6. Extract ParquetParser → returns ParsedDataset (owns DuckDB + temp file) x
7. Extract NumericStatsCalculator — pure stats, no I/O x
8. Slim DataAnalysisService to thin orchestrator (~100 lines)
9. Run tests, fix any regressions

   Controller refactor: auto-detection of file (12–17):
12. Create FormatDetector (Content-Type → magic bytes → sniff first char → CSV fallback)
13. Unit-test it (edge cases: empty, whitespace, conflicting hints, NDJSON vs JSON-array)
14. Add /ingest endpoint + single service entry point
15. Integration tests for /ingest
16. Deprecate the 4 old endpoints (keep working for one release)
17. Update README + Features docs

# Analysis Improvement New Feature
1. Correlation matrix for numeric columns — quantify how strong and what kind of relationship, so we can act on it.
## Three concrete uses:
1. Spot redundancy — r=1.0 means one column is derivable from the other. Drop it, save storage/compute.
2. Spot data quality bugs — columns that should correlate but don't (e.g. price vs price_after_tax at r=0.4) reveal broken ETL.
3. Inform downstream work — ML feature selection, dashboard design, join key candidates.

2. Outlier count via IQR rule per numeric column.
3. Top-K values + frequencies for non-numeric columns (fixes the all-null categorical block).
4. Completeness score per column and dataset-level (1 - nullCount/rows).
5. Cardinality ratio + auto-flag: primaryKey (1.0), constant (≈0), categorical, highCardinality.
6. Inferred semantic type per column (id, measure, category, flag, timestamp, email, etc.).
7. Histogram bins (10 buckets, counts) — lets downstream charts render without rescanning.
8. Skewness + kurtosis — distribution shape, signals transform needs (log, etc.).
9. Duplicate row count at dataset level.
10. Warnings array — human-readable callouts ("flag is constant", "score↔rank r=0.99", "id looks like a primary key").