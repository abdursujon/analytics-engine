# Analytics Engine

A Spring Boot REST API that profiles tabular data (CSV and Parquet). Upload a CSV and the service infers column types, counts nulls and unique values, computes summary statistics (min, max, mean, median, standard deviation, and the
25/50/75/90/95/99 percentiles), and persists the results so they can be retrieved or downloaded later. Repeated uploads of the same content are detected via SHA-256 hashing and short-circuit to the cached analysis.



