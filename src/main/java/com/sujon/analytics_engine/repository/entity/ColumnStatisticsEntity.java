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

    // Primary key for the entity
    @Id
    // Auto-increment ID generation
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "null_count")
    private int nullCount;

    @Column(name = "unique_count")
    private int uniqueCount;

    @Column(name = "is_numeric")
    private boolean isNumeric;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "mean_value")
    private Double meanValue;

    @Column(name = "median_value")
    private Double medianValue;

    @Column(name = "standard_deviation")
    private Double standardDeviation;

    @Column(name = "percentile_25")
    private Double percentile25;

    @Column(name = "percentile_50")
    private Double percentile50;

    @Column(name = "percentile_75")
    private Double percentile75;

    @Column(name = "percentile_90")
    private Double percentile90;

    @Column(name = "percentile_95")
    private Double percentile95;

    @Column(name = "percentile_99")
    private Double percentile99;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "data_analysis_id")
    private DataAnalysisEntity dataAnalysis;
}
