package com.community.medrecycle.repo;

import com.community.medrecycle.domain.ExceptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExceptionRecordRepository extends JpaRepository<ExceptionRecord, Long> {
    List<ExceptionRecord> findByBatchId(Long batchId);

    List<ExceptionRecord> findByStatusOrderByIdDesc(ExceptionRecord.Status status);

    long countByBatchIdAndStatus(Long batchId, ExceptionRecord.Status status);

    long countByStatus(ExceptionRecord.Status status);
}
