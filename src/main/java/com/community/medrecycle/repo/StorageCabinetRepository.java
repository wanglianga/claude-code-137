package com.community.medrecycle.repo;

import com.community.medrecycle.domain.StorageCabinet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorageCabinetRepository extends JpaRepository<StorageCabinet, Long> {
    List<StorageCabinet> findByCommunityPointId(Long communityPointId);

    Optional<StorageCabinet> findByCode(String code);
}
