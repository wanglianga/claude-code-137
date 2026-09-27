package com.community.medrecycle.repo;

import com.community.medrecycle.domain.DrugCategory;
import com.community.medrecycle.domain.PointsRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointsRuleRepository extends JpaRepository<PointsRule, Long> {
    Optional<PointsRule> findByCategory(DrugCategory category);
}
