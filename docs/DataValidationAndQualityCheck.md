Data validation and quality check requirements
## 1. Request-Level Validation and download 
- Content-Type must be `text/plain` or `text/csv` (done)
- Request body must not be empty (done)
- Maximum CSV size enforced
- UTF-8 encoding required
- User can only access the id that they acquired through analysis, any other id must be revoked to access use and download

## 2. CSV Structure Validation
- Single consistent delimiter
- Header row required
- No empty or duplicate header names
- Same column count for all rows

## 3. Schema Validation
- Expected column list predefined
- Column order enforced (or explicitly ignored)
- Data type enforced per column
- Required columns must be present
- Optional columns explicitly defined

## 4. Value Validation
- Leading/trailing whitespace trimmed
- Illegal characters rejected
- Max/min length per column enforced
- Numeric range validation
- Date format validation
- Enum / allowed values enforced

## 5. Null and Missing Data
- Distinguish empty string vs null
- Required fields cannot be null or empty
- Missing value threshold enforced per column

## 6. Duplicate Detection
- Duplicate rows detection
- Duplicate key detection (business key or composite key)

## 7. Statistical Sanity Checks
- Min/max sanity validation
- Outlier threshold detection
- Mean/variance computable for numeric columns

## 8. Cross-Field Rules
- Column dependency rules (e.g. startDate <= endDate)
- Conditional required fields

## 9. Error Handling
- Fail-fast or accumulate-all-errors strategy defined
- Row and column number included in errors
- Deterministic error codes
- Correct HTTP status mapping

## 10. Persistence Safety
- Validation must occur before database write
- Transaction rollback on validation failure

## 11. Observability
- Validation failures logged
- Rejected row count tracked
- Validation metrics exposed

## 12. Security Validation
- CSV formula injection protection (`=`, `+`, `-`, `@`)
- No script or expression execution
- Safe parsing only