package com.community.medrecycle.repo;

import com.community.medrecycle.domain.PointsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointsAccountRepository extends JpaRepository<PointsAccount, Long> {

    /** 原子加积分（兑换峰值下保证不超扣、不丢更新） */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PointsAccount a SET a.balance = a.balance + :points, a.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE a.userId = :userId")
    int addBalance(@Param("userId") Long userId, @Param("points") int points);

    /** 原子扣积分：余额不足时影响行数为 0 */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PointsAccount a SET a.balance = a.balance - :points, a.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE a.userId = :userId AND a.balance >= :points")
    int deductBalance(@Param("userId") Long userId, @Param("points") int points);
}
