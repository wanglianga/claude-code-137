package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "community_points")
public class CommunityPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String address;

    /** 环保公司上门量阈值（克）：暂存总重达到该值即可预约上门 */
    @Column(nullable = false)
    private Integer pickupThresholdGrams = 50000;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
