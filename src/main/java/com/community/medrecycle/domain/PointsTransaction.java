package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 积分流水 */
@Getter
@Setter
@Entity
@Table(name = "points_transactions")
public class PointsTransaction {

    /** EARN_WEIGH 称重即得 / EARN_REVIEW 复核通过发放 / REDEEM 兑换扣减 / ADJUST 争议调整 */
    public enum Type {EARN_WEIGH, EARN_REVIEW, REDEEM, ADJUST}

    public enum Status {SUCCESS, FAILED, PENDING}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type refType;

    private Long refId;

    /** 正为入账，负为扣减 */
    @Column(nullable = false)
    private Integer points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 255)
    private String remark;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
