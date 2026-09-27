package com.community.medrecycle.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 居民回收预约 */
@Getter
@Setter
@Entity
@Table(name = "appointments")
public class Appointment {

    public enum Status {PENDING, CHECKED_IN, COMPLETED, CANCELLED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String appointmentNo;

    @Column(nullable = false)
    private Long residentId;

    @Column(nullable = false)
    private Long communityPointId;

    private Long activityDayId;

    /** 药品类型（如：感冒药、降压药……） */
    @Column(nullable = false, length = 100)
    private String drugType;

    /** 数量（盒/瓶/支） */
    @Column(nullable = false)
    private Integer quantity;

    /** 是否含处方药 */
    @Column(nullable = false)
    private Boolean hasPrescription = false;

    /** 是否液体 */
    @Column(nullable = false)
    private Boolean isLiquid = false;

    /** 是否针剂 */
    @Column(nullable = false)
    private Boolean isInjection = false;

    /** 是否有个人信息包装 */
    @Column(nullable = false)
    private Boolean hasPersonalInfo = false;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime checkedInAt;
}
