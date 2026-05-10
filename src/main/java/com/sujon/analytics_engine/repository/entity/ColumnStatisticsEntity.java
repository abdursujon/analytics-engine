package  com.sujon.analytics_engine.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * JPA entity representing statistics for a single column in a data analysis.
 * <p>
 * This entity has a many-to-one relationship with {@link DataAnalysisEntity},
 * allowing each analysis to have multiple column statistics records.
 * <p>
 * Includes statistical profiling fields for numeric columns.
 */
@Entity
@Table(name = "column_statistics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnStatisticsEntity {

    @Id // Primary key for the entity
    @GeneratedValue(strategy = IDENTITY) // Auto-increment ID generation
    private Long id;

    @Column(name = "column_name", nullable = false) // CSV column header name
    private String columnName;

    @Column(name = "null_count") // Count of null/empty values
    private int nullCount;

    @Column(name = "unique_count") // Count of distinct non-null values
    private int uniqueCount;

    @Column(name = "is_numeric") // Flag indicating if column is numeric
    private boolean isNumeric;

    @Column(name = "min_value") // Minimum value for numeric columns
    private Double minValue;

    @Column(name = "max_value") // Maximum value for numeric columns
    private Double maxValue;

    @Column(name = "mean_value") // Arithmetic mean for numeric columns
    private Double meanValue;

    @Column(name = "median_value") // Median (50th percentile) for numeric columns
    private Double medianValue;

    @Column(name = "standard_deviation") // Standard deviation for numeric columns
    private Double standardDeviation;

    @Column(name = "percentile_25") // 25th percentile (Q1) for numeric columns
    private Double percentile25;

    @Column(name = "percentile_50") // 50th percentile (Q2/median) for numeric columns
    private Double percentile50;

    @Column(name = "percentile_75") // 75th percentile (Q3) for numeric columns
    private Double percentile75;

    @Column(name = "percentile_90") // 90th percentile for numeric columns
    private Double percentile90;

    @Column(name = "percentile_95") // 95th percentile for numeric columns
    private Double percentile95;

    @Column(name = "percentile_99") // 99th percentile for numeric columns
    private Double percentile99;

    @ManyToOne(fetch = LAZY) // Lazy fetch to avoid loading parent unnecessarily
    @JoinColumn(name = "data_analysis_id") // Foreign key to parent analysis
    private DataAnalysisEntity dataAnalysis;
}
