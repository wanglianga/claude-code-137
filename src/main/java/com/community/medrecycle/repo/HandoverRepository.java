package com.community.medrecycle.repo;

import com.community.medrecycle.domain.Handover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HandoverRepository extends JpaRepository<Handover, Long> {
    Optional<Handover> findByHandoverNo(String handoverNo);

    List<Handover> findByCommunityPointIdOrderByIdDesc(Long communityPointId);
}
