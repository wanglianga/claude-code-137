package com.community.medrecycle.repo;

import com.community.medrecycle.domain.ExchangeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrder, Long> {
    List<ExchangeOrder> findByUserIdOrderByIdDesc(Long userId);
}
