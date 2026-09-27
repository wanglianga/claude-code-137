package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 暂存柜 */
@Getter
@Setter
@Entity
@Table(name = "storage_cabinets")
public class StorageCabinet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long communityPointId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** 容量（袋数） */
    @Column(nullable = false)
    private Integer capacityBags;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
