# Controller refactor: auto-detect file format
1. Create FormatDetector (Content-Type → magic bytes → sniff first char → CSV fallback)
2. Unit-test it (edge cases: empty, whitespace, conflicting hints, NDJSON vs JSON-array)
3. Add /ingest endpoint + single service entry point
4. Integration tests for /ingest
5. Deprecate the 4 old endpoints (keep working for one release)
6. Update README + features guide
