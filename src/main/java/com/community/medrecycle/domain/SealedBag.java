package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 封袋：每袋药品必须有来源（预约）与分类 */
@Getter
@Setter
@Entity
@Table(name = "sealed_bags")
public class SealedBag {

    public enum Status {SEALED, REVIEW_HOLD, STORED, HANDED_OVER, REJECTED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sealBagNo;

    @Column(nullable = false)
    private Long batchId;

    /** 来源预约 */
    @Column(nullable = false)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DrugCategory category;

    @Column(nullable = false)
    private Integer weightGrams;

    @Column(length = 2000)
    private String photoUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SEALED;

    private Long cabinetId;

    private LocalDateTime storedAt;

    private Long handoverId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
