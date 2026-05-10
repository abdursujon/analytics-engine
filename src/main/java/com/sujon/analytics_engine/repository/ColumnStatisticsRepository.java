package  com.sujon.analytics_engine.repository;

import  com.sujon.analytics_engine.repository.entity.ColumnStatisticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for database operations on {@link ColumnStatisticsEntity}.
 */
@Repository
public interface ColumnStatisticsRepository extends JpaRepository<ColumnStatisticsEntity, Long> {
}
