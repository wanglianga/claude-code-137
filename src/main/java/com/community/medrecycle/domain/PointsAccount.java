package com.community.medrecycle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 居民积分账户 */
@Getter
@Setter
@Entity
@Table(name = "points_accounts")
public class PointsAccount {

    @Id
    private Long userId;

    @Column(nullable = false)
    private Integer balance = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
