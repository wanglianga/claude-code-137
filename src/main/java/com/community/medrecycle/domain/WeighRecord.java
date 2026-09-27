package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 现场称重记录 */
@Getter
@Setter
@Entity
@Table(name = "weigh_records")
public class WeighRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrugCategory category;

    @Column(nullable = false)
    private Integer weightGrams;

    /** 包装照片，逗号分隔 */
    @Column(length = 2000)
    private String photoUrls;

    /** 封袋编号 */
    @Column(nullable = false, length = 64)
    private String sealBagNo;

    @Column(nullable = false)
    private Long weighedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
