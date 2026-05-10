package  com.sujon.analytics_engine.repository.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.FetchType.EAGER;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * JPA entity representing a data analysis record in the {@code data_analysis} table.
 */
@Entity
@Table(name = "data_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataAnalysisEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Lob
    @Column(name = "original_data", nullable = false, columnDefinition = "TEXT")
    private String originalData;
    
    // content_hash for checking duplicate call
    @Column(name = "content_hash", nullable = false, unique = true, length = 64)
    private String contentHash;

    @Column(name = "number_of_rows")
    private int numberOfRows;

    @Column(name = "number_of_columns")
    private int numberOfColumns;

    @Column(name = "total_characters")
    private long totalCharacters;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "dataAnalysis", cascade = ALL, orphanRemoval = true, fetch = EAGER)
    @Builder.Default
    private List<ColumnStatisticsEntity> columnStatistics = new ArrayList<>();
}
