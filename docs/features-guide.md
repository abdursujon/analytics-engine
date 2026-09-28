# Features

## ✅ Implemented

**Ingestion & validation**
- 📂 CSV, Parquet, JSON and NDJSON ingestion
- 🧾 Header presence validation (rejects a blank first line)
- 📐 Row shape validation (equal column count per row)
- 🚫 Malformed row rejection
- 📏 Upload limits: 5 MB per file and 1,000,000 cells

**Profiling**
- 🔍 Numeric type detection per column
- 🧮 Null and empty value counts
- 🔢 Unique value counts per column
- 📊 Min, max, mean, median and standard deviation
- 📈 Percentiles: p25, p50, p75, p90, p95, p99
- 🔗 Numeric correlation matrix
- 📋 Row count (blank lines skipped) and total character count

**Storage & retrieval**
- ♻️ SHA-256 content hashing with cached analysis reuse
- 💾 Persistent analysis metadata and per-column statistics
- 📥 Retrieve, download as JSON, or delete by id

## 🧭 Features That You Could Implement

**Data quality**
- Completeness and missing data pattern detection
- Missing data handling and imputation strategies
- Redundant and low-information column detection
- Anomaly and outlier detection
- Row-level and column-level validity labeling
- Rule-based issue detection and tagging
- Dataset quality scoring and risk classification
- Remediation recommendations

**Profiling**
- Outlier count per numeric column (IQR rule)
- Categorical profiling (top-K, dominance, long tail)
- Completeness score per column and dataset (1 - nullCount / rows)
- Cardinality ratio with flags: primary key, constant, categorical, high cardinality
- Semantic type inference (id, measure, category, flag, timestamp, email)
- Histogram bins (10 buckets) for charting without rescanning
- Skewness and kurtosis
- Duplicate row count
- Warnings array with readable callouts (constant column, strong correlation, likely primary key)
- Text profiling (length, tokens, character patterns)
- Column dependency analysis

**Datasets over time**
- Schema fingerprinting and stability checks
- Dataset similarity detection
- Historical tracking and versioning
- Dataset comparison and change analysis
- Data drift detection and monitoring
- Audit trail and lineage tracking

**Processing & export**
- Batch processing of multiple files
- Consistent feature alignment across batches
- Data filtering and transformation
- Feature extraction and vectorization for ML
- Export to Excel and Parquet
- Data visualization endpoints
