package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 环保交接单 */
@Getter
@Setter
@Entity
@Table(name = "handovers")
public class Handover {

    public enum Status {DRAFT, COMPLETED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String handoverNo;

    @Column(nullable = false)
    private Long communityPointId;

    /** 去向：环保公司名称 */
    @Column(nullable = false, length = 100)
    private String envCompany;

    @Column(nullable = false)
    private Integer totalWeightGrams = 0;

    @Column(nullable = false)
    private Integer bagCount = 0;

    @Column(length = 2000)
    private String photoUrls;

    /** 签收人 */
    @Column(length = 64)
    private String receiverName;

    /** 双人交接第二签收人 */
    @Column(length = 64)
    private String secondReceiverName;

    /** 是否需要双人交接（含精神类/注射器袋时） */
    @Column(nullable = false)
    private Boolean dualRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private Long handedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;
}
