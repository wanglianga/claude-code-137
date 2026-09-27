package com.community.medrecycle.repo;

import com.community.medrecycle.domain.SealedBag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SealedBagRepository extends JpaRepository<SealedBag, Long> {

    Optional<SealedBag> findBySealBagNo(String sealBagNo);

    boolean existsBySealBagNo(String sealBagNo);

    List<SealedBag> findByBatchId(Long batchId);

    List<SealedBag> findByCabinetIdAndStatus(Long cabinetId, SealedBag.Status status);

    List<SealedBag> findByStatus(SealedBag.Status status);

    /** 某社区点下处于指定状态的袋（经批次关联） */
    @Query("SELECT b FROM SealedBag b, RecycleBatch rb " +
            "WHERE b.batchId = rb.id AND rb.communityPointId = :pointId AND b.status = :status")
    List<SealedBag> findByPointAndStatus(@Param("pointId") Long pointId,
                                         @Param("status") SealedBag.Status status);

    /** 某社区点指定状态袋的总重量 */
    @Query("SELECT COALESCE(SUM(b.weightGrams), 0) FROM SealedBag b, RecycleBatch rb " +
            "WHERE b.batchId = rb.id AND rb.communityPointId = :pointId AND b.status = :status")
    long sumWeightByPointAndStatus(@Param("pointId") Long pointId,
                                   @Param("status") SealedBag.Status status);

    List<SealedBag> findByIdIn(Collection<Long> ids);

    List<SealedBag> findByHandoverId(Long handoverId);
}
