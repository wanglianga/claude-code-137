package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 交接单-封袋关联 */
@Getter
@Setter
@Entity
@Table(name = "handover_bags")
public class HandoverBag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long handoverId;

    @Column(nullable = false)
    private Long bagId;
}
