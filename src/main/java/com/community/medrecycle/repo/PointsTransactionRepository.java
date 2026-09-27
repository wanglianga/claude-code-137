package com.community.medrecycle.repo;

import com.community.medrecycle.domain.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {
    List<PointsTransaction> findByUserIdOrderByIdDesc(Long userId);

    List<PointsTransaction> findByBatchId(Long batchId);

    List<PointsTransaction> findByRefTypeAndRefId(PointsTransaction.Type refType, Long refId);
}
