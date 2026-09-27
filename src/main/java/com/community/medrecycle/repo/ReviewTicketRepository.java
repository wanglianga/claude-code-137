package com.community.medrecycle.repo;

import com.community.medrecycle.domain.ReviewTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewTicketRepository extends JpaRepository<ReviewTicket, Long> {
    List<ReviewTicket> findByStatusOrderByIdAsc(ReviewTicket.Status status);

    List<ReviewTicket> findByBagId(Long bagId);

    long countByStatus(ReviewTicket.Status status);
}
