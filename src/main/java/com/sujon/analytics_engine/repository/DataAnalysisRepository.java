package  com.sujon.analytics_engine.repository;

import com.sujon.analytics_engine.repository.entity.DataAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository interface for database operations on {@link DataAnalysisEntity}.
 */
@Repository
public interface DataAnalysisRepository extends JpaRepository<DataAnalysisEntity, Long> {
    // Added find by content hash for duplicate api call check for same file
    Optional<DataAnalysisEntity> findByContentHash(String contentHash);
}



