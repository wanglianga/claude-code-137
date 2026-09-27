package com.community.medrecycle.repo;

import com.community.medrecycle.domain.HandoverBag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoverBagRepository extends JpaRepository<HandoverBag, Long> {
    List<HandoverBag> findByHandoverId(Long handoverId);
}
