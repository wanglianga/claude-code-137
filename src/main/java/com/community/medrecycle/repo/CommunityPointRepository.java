package com.community.medrecycle.repo;

import com.community.medrecycle.domain.CommunityPoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPointRepository extends JpaRepository<CommunityPoint, Long> {
}
