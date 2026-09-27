package com.community.medrecycle.repo;

import com.community.medrecycle.domain.RecycleBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecycleBatchRepository extends JpaRepository<RecycleBatch, Long> {
    Optional<RecycleBatch> findByBatchNo(String batchNo);

    List<RecycleBatch> findByCommunityPointIdOrderByIdDesc(Long communityPointId);
}
