package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 回收批次：把预约、称重、暂存、复核、积分、交接、异常串在同一批次 */
@Getter
@Setter
@Entity
@Table(name = "recycle_batches")
public class RecycleBatch {

    public enum Status {OPEN, IN_STORAGE, HANDED_OVER}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String batchNo;

    @Column(nullable = false)
    private Long communityPointId;

    @Column(nullable = false)
    private Long collectorId;

    private Long activityDayId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime closedAt;
}
